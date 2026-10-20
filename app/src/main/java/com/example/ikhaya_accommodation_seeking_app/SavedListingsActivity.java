
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    private TextView txtNoSavedListings;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_listings);

        savedListingsContainer =
                findViewById(R.id.savedListingsContainer);
        txtNoSavedListings =
                findViewById(R.id.txtNoSavedListings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadSavedListings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (savedListingsContainer != null) loadSavedListings();
    }

    private void loadSavedListings() {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || token == null || token.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        runOnUiThread(() -> {
            txtNoSavedListings.setText("Loading your favorites...");
            txtNoSavedListings.setVisibility(View.VISIBLE);
            savedListingsContainer.removeAllViews();
        });

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/saved_listings"
                        + "?select=*"
                        + "&tenant_uid=eq."
                        + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint, "GET", token);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage("Could not load favorites. Supabase error: " + code);
                    return;
                }

                JSONArray savedRows = new JSONArray(response);
                List<JSONObject> result = new ArrayList<>();

                for (int i = 0; i < savedRows.length(); i++) {
                    JSONObject saved = savedRows.optJSONObject(i);
                    if (saved == null) continue;

                    String listingId = firstValue(
                            saved, "listing_id", "property_id");

                    if (listingId.isEmpty()) continue;

                    JSONObject property = fetchListing(listingId, token);

                    if (property != null) {
                        property.put("_saved_listing_id",
                                saved.optString("id", ""));
                        property.put("_saved_listing_listing_id", listingId);
                        result.add(property);
                    }
                }

                runOnUiThread(() -> {
                    savedListingsContainer.removeAllViews();

                    if (result.isEmpty()) {
                        txtNoSavedListings.setText(
                                "You haven't saved any properties yet.");
                        txtNoSavedListings.setVisibility(View.VISIBLE);
                        return;
                    }

                    txtNoSavedListings.setVisibility(View.GONE);

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

            connection = SupabaseHelper.createConnection(
                    endpoint, "GET", token);

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
        String listingId = firstValue(
                property, "_saved_listing_listing_id", "id");
        String savedRowId = firstValue(property, "_saved_listing_id");

        String name = firstValue(
                property, "property_name", "title", "name");
        String location = firstValue(
                property, "location", "address", "area");
        String price = firstValue(
                property, "price", "rental_price", "monthly_price");
        String rooms = firstValue(
                property, "rooms", "bedrooms", "room_count");
        String imageUrl = firstValue(
                property, "image_url", "image", "image_path",
                "photo_url", "property_image", "imageUrl",
                "cover_image", "photo");

        if (name.isEmpty()) name = "Accommodation";
        if (location.isEmpty()) location = "Location not available";
        if (price.isEmpty()) price = "Price not available";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(14));
        card.setBackground(roundedBackground(Color.WHITE, "#E9E3DE", 20));
        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(18);
        card.setLayoutParams(cardParams);

        ImageView image = new ImageView(this);
        image.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(235, 229, 224));
        image.setContentDescription("Saved property photo");
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        card.addView(image);

        if (!imageUrl.isEmpty()) loadImage(imageUrl, image);

        TextView nameView = makeText(name, 19, true, "#241A18");
        nameView.setPadding(0, dp(12), 0, 0);

        TextView locationView = makeText(
                "📍 " + location, 14, false, "#81736D");
        locationView.setPadding(0, dp(5), 0, 0);

        TextView priceView = makeText(
                price.startsWith("R") ? price : "R" + price,
                17, true, "#B95737");
        priceView.setPadding(0, dp(8), 0, 0);

        card.addView(nameView);
        card.addView(locationView);
        card.addView(priceView);

        if (!rooms.isEmpty()) {
            TextView roomsView = makeText(
                    rooms + " rooms", 14, false, "#756A65");
            roomsView.setPadding(0, dp(6), 0, 0);
            card.addView(roomsView);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);

        Button viewButton = new Button(this);
        viewButton.setText("View property");
        viewButton.setAllCaps(false);
        viewButton.setTextColor(Color.WHITE);
        viewButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(185, 87, 55)));

        Button removeButton = new Button(this);
        removeButton.setText("Remove favorite");
        removeButton.setAllCaps(false);
        removeButton.setTextColor(Color.rgb(158, 56, 40));
        removeButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(240, 228, 223)));

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        actionParams.setMargins(dp(3), 0, dp(3), 0);

        actions.addView(viewButton, actionParams);
        actions.addView(removeButton, actionParams);
        card.addView(actions);

        String finalName = name;
        String finalLocation = location;
        String finalPrice = price;

        viewButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    SavedListingsActivity.this,
                    PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);
            intent.putExtra("propertyName", finalName);
            intent.putExtra("listingLocation", finalLocation);
            intent.putExtra("listingPrice", finalPrice);
            startActivity(intent);
        });

        removeButton.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Remove favorite?")
                        .setMessage("Remove \"" + finalName
                                + "\" from your saved listings?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove",
                                (dialog, which) ->
                                        removeFavorite(
                                                listingId, savedRowId, userId))
                        .show());

        savedListingsContainer.addView(card);
    }

    private void removeFavorite(
            String listingId, String savedRowId, String userId) {
        String token = SupabaseHelper.getAccessToken(this);

        if (token == null || token.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/saved_listings"
                        + "?tenant_uid=eq."
                        + SupabaseHelper.encode(userId);

                if (!savedRowId.isEmpty()) {
                    endpoint += "&id=eq."
                            + SupabaseHelper.encode(savedRowId);
                } else {
                    endpoint += "&listing_id=eq."
                            + SupabaseHelper.encode(listingId);
                }

                connection = SupabaseHelper.createConnection(
                        endpoint, "DELETE", token);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    showMessage("Favorite removed.");
                    loadSavedListings();
                } else {
                    showMessage("Could not remove favorite. Supabase error: "
                            + code);
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
                if (!imageUrl.startsWith("https://")
                        && !imageUrl.startsWith("http://")) return;

                connection = (HttpURLConnection)
                        new URL(imageUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.connect();

                if (connection.getResponseCode() < 200
                        || connection.getResponseCode() >= 300) return;

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
                // Keep the placeholder if the image is inaccessible.
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

    private TextView makeText(
            String value, int size, boolean bold, String color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor(color));
        if (bold) text.setTypeface(null, Typeface.BOLD);
        return text;
    }

    private GradientDrawable roundedBackground(
            int fill, String stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showMessage(String message) {
        runOnUiThread(() -> Toast.makeText(
                SavedListingsActivity.this, message,
                Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
