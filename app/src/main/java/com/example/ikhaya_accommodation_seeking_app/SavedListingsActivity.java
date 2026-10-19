package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class SavedListingsActivity extends AppCompatActivity {

    private LinearLayout savedListingsContainer;
    private View txtNoSavedListings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_listings);

        savedListingsContainer = findViewById(R.id.savedListingsContainer);
        txtNoSavedListings = findViewById(R.id.txtNoSavedListings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View btnEmptyExplore = findViewById(R.id.btnEmptyExplore);
        if (btnEmptyExplore != null) {
            btnEmptyExplore.setOnClickListener(v -> finish());
        }

        setupBottomNavigation();
        loadSavedListings();
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
                        int baseMarginPx = (int) (16 * getResources().getDisplayMetrics().density);
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
                navMessages.setOnClickListener(v -> {
                    startActivity(new Intent(SavedListingsActivity.this, UserSelectionActivity.class));
                });
            }
        }
    }

    private void loadSavedListings() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String encodedUserId = URLEncoder.encode(userId, "UTF-8");
                String endpoint = "/rest/v1/saved_listings?select=*&tenant_uid=eq."
                        + encodedUserId
                        + "&order=created_at.desc";

                HttpURLConnection connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    showMessage("Could not load saved listings.");
                    return;
                }

                JSONArray savedListings = new JSONArray(response);

                runOnUiThread(() -> {
                    savedListingsContainer.removeAllViews();

                    if (savedListings.length() == 0) {
                        txtNoSavedListings.setVisibility(View.VISIBLE);
                    } else {
                        txtNoSavedListings.setVisibility(View.GONE);

                        for (int i = 0; i < savedListings.length(); i++) {
                            try {
                                JSONObject saved = savedListings.getJSONObject(i);
                                String listingId = saved.getString("listing_id");
                                loadListingDetails(listingId, accessToken);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                showMessage("Error loading saved listings.");
            }
        }).start();
    }

    private void loadListingDetails(String listingId, String accessToken) {
        new Thread(() -> {
            try {
                String encodedListingId = URLEncoder.encode(listingId, "UTF-8");
                String endpoint = "/rest/v1/listings?select=*&id=eq." + encodedListingId + "&limit=1";

                HttpURLConnection connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    return;
                }

                JSONArray listings = new JSONArray(response);
                if (listings.length() == 0) {
                    return;
                }

                JSONObject listing = listings.getJSONObject(0);
                runOnUiThread(() -> addListingCard(listing));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void addListingCard(JSONObject listing) {
        try {
            String listingId = listing.getString("id");
            String propertyName = listing.optString("property_name", "Accommodation");
            String location = listing.optString("location", "Location not available");
            String price = listing.optString("price", "Price not available");
            String rooms = listing.optString("rooms", "");

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
            int pad = dpToPx(16);
            content.setPadding(pad, pad, pad, pad);

            TextView tvName = new TextView(this);
            tvName.setText(propertyName);
            tvName.setTextSize(17);
            tvName.setTextColor(Color.parseColor("#1F1614"));
            tvName.setTypeface(null, Typeface.BOLD);
            content.addView(tvName);

            LinearLayout locRow = new LinearLayout(this);
            locRow.setOrientation(LinearLayout.HORIZONTAL);
            locRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams locParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            locParams.setMargins(0, dpToPx(6), 0, dpToPx(10));
            locRow.setLayoutParams(locParams);

            ImageView pinIcon = new ImageView(this);
            pinIcon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)));
            pinIcon.setImageResource(R.drawable.map_pin);
            pinIcon.setColorFilter(Color.parseColor("#8C7A70"));

            TextView tvLocation = new TextView(this);
            tvLocation.setText(" " + location);
            tvLocation.setTextSize(13);
            tvLocation.setTextColor(Color.parseColor("#8C7A70"));

            locRow.addView(pinIcon);
            locRow.addView(tvLocation);
            content.addView(locRow);

            LinearLayout bottomRow = new LinearLayout(this);
            bottomRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomRow.setGravity(Gravity.CENTER_VERTICAL);
            bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            TextView tvPrice = new TextView(this);
            tvPrice.setText("R " + price + " / month");
            tvPrice.setTextSize(15);
            tvPrice.setTextColor(Color.parseColor("#BA5536"));
            tvPrice.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
            );
            tvPrice.setLayoutParams(priceParams);
            bottomRow.addView(tvPrice);

            if (!rooms.isEmpty()) {
                TextView tvRooms = new TextView(this);
                tvRooms.setText(rooms + " rooms");
                tvRooms.setTextSize(12);
                tvRooms.setTextColor(Color.parseColor("#1F1614"));
                tvRooms.setBackgroundColor(Color.parseColor("#F5EFEB"));
                tvRooms.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
                bottomRow.addView(tvRooms);
            }

            content.addView(bottomRow);
            card.addView(content);

            card.setOnClickListener(v -> {
                Intent intent = new Intent(SavedListingsActivity.this, PropertyDetailsActivity.class);
                intent.putExtra("LISTING_ID", listingId);
                startActivity(intent);
            });

            savedListingsContainer.addView(card);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(SavedListingsActivity.this, message, Toast.LENGTH_SHORT).show()
        );
    }
}