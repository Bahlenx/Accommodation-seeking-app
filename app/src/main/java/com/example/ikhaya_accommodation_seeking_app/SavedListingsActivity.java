package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavedListingsActivity extends AppCompatActivity {

    private LinearLayout savedListingsContainer;
    private View txtNoSavedListings;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_listings);

        savedListingsContainer = findViewById(R.id.savedListingsContainer);
        txtNoSavedListings = findViewById(R.id.txtNoSavedListings);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btnEmptyExplore = findViewById(R.id.btnEmptyExplore);
        if (btnEmptyExplore != null) {
            btnEmptyExplore.setOnClickListener(v -> finish());
        }

        setupBottomNavigation();
        loadSavedListings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (savedListingsContainer != null) {
            loadSavedListings();
        }
    }

    private void setupBottomNavigation() {
        View savedRoot = findViewById(R.id.savedRoot);
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);

            if (savedRoot != null) {
                ViewCompat.setOnApplyWindowInsetsListener(savedRoot, (v, insets) -> {
                    Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                    v.setPadding(0, statusBar.top, 0, 0);

                    if (floatingBottomNav.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                        int baseMarginPx = dpToPx(16);
                        lp.bottomMargin = navBar.bottom + baseMarginPx;
                        floatingBottomNav.setLayoutParams(lp);
                        floatingBottomNav.setPadding(0, 0, 0, 0);
                    }

                    return WindowInsetsCompat.CONSUMED;
                });
            }

            // Tab 1: Discover
            View navHome = findViewById(R.id.navHome);
            if (navHome != null) {
                navHome.setOnClickListener(v -> {
                    Intent intent = new Intent(SavedListingsActivity.this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }

            // Tab 3: Viewings
            View navViewings = findViewById(R.id.navViewings);
            if (navViewings != null) {
                navViewings.setOnClickListener(v -> {
                    startActivity(new Intent(SavedListingsActivity.this, ViewingsActivity.class));
                    finish();
                });
            }

            // Tab 4: Messages
            View navMessages = findViewById(R.id.navMessages);
            if (navMessages != null) {
                navMessages.setOnClickListener(v ->
                        startActivity(new Intent(SavedListingsActivity.this, UserSelectionActivity.class))
                );
            }
        }
    }

    private void loadSavedListings() {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/saved_listings"
                        + "?select=*"
                        + "&tenant_uid=eq."
                        + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(endpoint, "GET", token);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage("Could not load favorites.");
                    return;
                }

                JSONArray savedRows = new JSONArray(response);
                List<JSONObject> result = new ArrayList<>();

                for (int i = 0; i < savedRows.length(); i++) {
                    JSONObject saved = savedRows.optJSONObject(i);
                    if (saved == null) continue;

                    String listingId = firstValue(saved, "listing_id", "property_id");
                    if (listingId.isEmpty()) continue;

                    JSONObject property = fetchListing(listingId, token);
                    if (property != null) {
                        property.put("_saved_listing_id", saved.optString("id", ""));
                        property.put("_saved_listing_listing_id", listingId);
                        result.add(property);
                    }
                }

                runOnUiThread(() -> {
                    savedListingsContainer.removeAllViews();

                    if (result.isEmpty()) {
                        if (txtNoSavedListings != null) {
                            txtNoSavedListings.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    if (txtNoSavedListings != null) {
                        txtNoSavedListings.setVisibility(View.GONE);
                    }

                    for (JSONObject property : result) {
                        addListingCard(property, userId);
                    }
                });

            } catch (Exception e) {
                showMessage("Could not load favorites. Please try again.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private JSONObject fetchListing(String listingId, String token) {
        HttpURLConnection connection = null;

        try {
            String endpoint = "/rest/v1/listings"
                    + "?select=*"
                    + "&id=eq."
                    + SupabaseHelper.encode(listingId)
                    + "&limit=1";

            connection = SupabaseHelper.createConnection(endpoint, "GET", token);
            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) return null;

            JSONArray rows = new JSONArray(response);
            return rows.length() == 0 ? null : rows.optJSONObject(0);

        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void addListingCard(JSONObject property, String userId) {
        String listingId = firstValue(property, "_saved_listing_listing_id", "id");
        String savedRowId = firstValue(property, "_saved_listing_id");

        String name = firstValue(property, "property_name", "title", "name");
        String location = firstValue(property, "location", "address", "area");
        String price = firstValue(property, "price", "rental_price", "monthly_price");
        String rooms = firstValue(property, "rooms", "bedrooms", "room_count");
        String imageUrl = firstValue(
                property, "image_url", "image", "image_path",
                "photo_url", "property_image", "imageUrl",
                "cover_image", "photo"
        );

        if (name.isEmpty()) name = getString(R.string.accommodation);
        if (location.isEmpty()) location = getString(R.string.location_not_available);
        if (price.isEmpty()) price = getString(R.string.price_not_available);

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(16));
        card.setLayoutParams(cardParams);
        card.setCardElevation(dpToPx(2));
        card.setRadius(dpToPx(18));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(14);
        content.setPadding(pad, pad, pad, pad);

        // Thumbnail
        ImageView image = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(160)
        );
        image.setLayoutParams(imgParams);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.parseColor("#EFEBE4"));
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        content.addView(image);

        if (!imageUrl.isEmpty()) {
            loadImage(imageUrl, image);
        }

        // Title
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(17);
        tvName.setTextColor(Color.parseColor("#1F1614"));
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setPadding(0, dpToPx(10), 0, 0);
        content.addView(tvName);

        // Location
        LinearLayout locRow = new LinearLayout(this);
        locRow.setOrientation(LinearLayout.HORIZONTAL);
        locRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams locParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        locParams.setMargins(0, dpToPx(4), 0, dpToPx(8));
        locRow.setLayoutParams(locParams);

        TextView tvLocation = new TextView(this);
        tvLocation.setText(getString(R.string.listing_location_pin, location));
        tvLocation.setTextSize(13);
        tvLocation.setTextColor(Color.parseColor("#8C7A70"));
        locRow.addView(tvLocation);
        content.addView(locRow);

        // Price & Rooms Row
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        priceRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tvPrice = new TextView(this);
        tvPrice.setText(getString(R.string.listing_price_month, price));
        tvPrice.setTextSize(15);
        tvPrice.setTextColor(Color.parseColor("#BA5536"));
        tvPrice.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        tvPrice.setLayoutParams(priceParams);
        priceRow.addView(tvPrice);

        if (!rooms.isEmpty()) {
            TextView tvRooms = new TextView(this);
            tvRooms.setText(getString(R.string.listing_rooms_tag, rooms));
            tvRooms.setTextSize(12);
            tvRooms.setTextColor(Color.parseColor("#1F1614"));
            tvRooms.setBackgroundColor(Color.parseColor("#F5EFEB"));
            tvRooms.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
            priceRow.addView(tvRooms);
        }
        content.addView(priceRow);

        // Action Buttons
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dpToPx(12), 0, 0);

        Button viewButton = new Button(this);
        viewButton.setText(R.string.view_property);
        viewButton.setAllCaps(false);
        viewButton.setTextColor(Color.WHITE);
        viewButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BA5536")));

        Button removeButton = new Button(this);
        removeButton.setText(R.string.remove);
        removeButton.setAllCaps(false);
        removeButton.setTextColor(Color.parseColor("#BA5536"));
        removeButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F4ECE9")));

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        actionParams.setMargins(dpToPx(3), 0, dpToPx(3), 0);

        actions.addView(viewButton, actionParams);
        actions.addView(removeButton, actionParams);
        content.addView(actions);

        card.addView(content);

        String finalName = name;
        String finalLocation = location;
        String finalPrice = price;

        viewButton.setOnClickListener(v -> {
            Intent intent = new Intent(SavedListingsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("propertyName", finalName);
            intent.putExtra("listingLocation", finalLocation);
            intent.putExtra("listingPrice", finalPrice);
            startActivity(intent);
        });

        removeButton.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.remove_favorite_title)
                        .setMessage(getString(R.string.remove_favorite_message, finalName))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.remove, (dialog, which) ->
                                removeFavorite(listingId, savedRowId, userId)
                        )
                        .show()
        );

        savedListingsContainer.addView(card);
    }

    private void removeFavorite(String listingId, String savedRowId, String userId) {
        String token = SupabaseHelper.getAccessToken(this);

        if (token == null || token.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/saved_listings?tenant_uid=eq." + SupabaseHelper.encode(userId);

                if (!savedRowId.isEmpty()) {
                    endpoint += "&id=eq." + SupabaseHelper.encode(savedRowId);
                } else {
                    endpoint += "&listing_id=eq." + SupabaseHelper.encode(listingId);
                }

                connection = SupabaseHelper.createConnection(endpoint, "DELETE", token);
                int code = connection.getResponseCode();

                if (code >= 200 && code < 300) {
                    showMessage("Favorite removed.");
                    loadSavedListings();
                } else {
                    showMessage("Could not remove favorite. Supabase error: " + code);
                }

            } catch (Exception e) {
                showMessage("Could not remove favorite. Please try again.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void loadImage(String imageUrl, ImageView imageView) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("http://")) return;

                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.connect();

                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return;

                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String firstValue(JSONObject object, String... keys) {
        for (String key : keys) {
            Object raw = object.opt(key);
            if (raw == null || raw == JSONObject.NULL) continue;

            String value = String.valueOf(raw).trim();
            if (!value.isEmpty() && !value.equalsIgnoreCase("null")) {
                return value;
            }
        }
        return "";
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(SavedListingsActivity.this, message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}