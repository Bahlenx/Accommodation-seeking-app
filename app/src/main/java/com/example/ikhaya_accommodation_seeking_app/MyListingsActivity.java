
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyListingsActivity extends AppCompatActivity {

    private LinearLayout listingsContainer;
    private TextView txtNoListings;
    private final ExecutorService executor =
            Executors.newFixedThreadPool(3);

    private final ActivityResultLauncher<Intent> editListingLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadMyListings()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_listings);

        listingsContainer = findViewById(R.id.myListingsContainer);
        txtNoListings = findViewById(R.id.txtNoListings);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        loadMyListings();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (listingsContainer != null) {
            loadMyListings();
        }
    }

    private void loadMyListings() {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || token == null || token.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        txtNoListings.setText("Loading your listings...");
        txtNoListings.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/listings"
                        + "?select=*"
                        + "&landlord_uid=eq."
                        + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint, "GET", token
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage("Could not load listings. Supabase error: " + code);
                    return;
                }

                JSONArray listings = new JSONArray(response);

                runOnUiThread(() -> {
                    listingsContainer.removeAllViews();

                    if (listings.length() == 0) {
                        txtNoListings.setText("You haven't added any listings yet.");
                        txtNoListings.setVisibility(View.VISIBLE);
                        return;
                    }

                    txtNoListings.setVisibility(View.GONE);

                    for (int i = 0; i < listings.length(); i++) {
                        JSONObject listing = listings.optJSONObject(i);
                        if (listing != null) {
                            addListingCard(listing);
                        }
                    }
                });

            } catch (Exception e) {
                showMessage("Could not load listings. Please try again.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void addListingCard(JSONObject listing) {
        View card = LayoutInflater.from(this).inflate(
                R.layout.item_listing,
                listingsContainer,
                false
        );

        ImageView imgListing = card.findViewById(R.id.imgListing);
        TextView txtListingName = card.findViewById(R.id.txtListingName);
        TextView txtListingLocation = card.findViewById(R.id.txtListingLocation);
        TextView txtListingPrice = card.findViewById(R.id.txtListingPrice);
        TextView txtListingRooms = card.findViewById(R.id.txtListingRooms);
        Button btnEditListing = card.findViewById(R.id.btnEditListing);
        Button btnDeleteListing = card.findViewById(R.id.btnDeleteListing);

        String listingId = listing.optString("id", "");
        String propertyName = firstValue(
                listing, "property_name", "title", "name"
        );
        String location = firstValue(
                listing, "location", "address", "area"
        );
        String price = firstValue(listing, "price", "monthly_price");
        String rooms = firstValue(
                listing, "rooms", "bedrooms", "room_count"
        );
        String imageUrl = firstValue(
                listing,
                "image_url",
                "image",
                "image_path",
                "photo_url",
                "property_image",
                "imageUrl",
                "cover_image",
                "photo"
        );

        if (propertyName.isEmpty()) propertyName = "Property";
        if (location.isEmpty()) location = "Location not provided";
        if (price.isEmpty()) price = "0";
        if (rooms.isEmpty()) rooms = "Not specified";

        txtListingName.setText(propertyName);
        txtListingLocation.setText(location);
        txtListingPrice.setText("R" + price + " / month");
        txtListingRooms.setText(rooms + " rooms");

        imgListing.setImageResource(android.R.drawable.ic_menu_gallery);
        imgListing.setScaleType(ImageView.ScaleType.CENTER_CROP);

        if (!imageUrl.isEmpty()) {
            loadListingImage(imageUrl, imgListing);
        }

        String finalPropertyName = propertyName;

        btnEditListing.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                showMessage("Listing ID is missing.");
                return;
            }

            Intent intent = new Intent(
                    MyListingsActivity.this,
                    EditListingActivity.class
            );

            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);
            intent.putExtra("propertyName", finalPropertyName);

            editListingLauncher.launch(intent);
        });

        btnDeleteListing.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                showMessage("Listing ID is missing.");
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete listing?")
                    .setMessage("Delete \"" + finalPropertyName + "\"?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) ->
                            deleteListing(listingId))
                    .show();
        });

        listingsContainer.addView(card);
    }

    private String firstValue(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);

            if (value != null && value != JSONObject.NULL) {
                String result = String.valueOf(value).trim();

                if (!result.isEmpty()
                        && !result.equalsIgnoreCase("null")) {
                    return result;
                }
            }
        }

        return "";
    }

    private void loadListingImage(String imageUrl, ImageView imageView) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                if (!imageUrl.startsWith("https://")
                        && !imageUrl.startsWith("http://")) {
                    return;
                }

                connection = (HttpURLConnection)
                        new URL(imageUrl).openConnection();

                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() < 200
                        || connection.getResponseCode() >= 300) {
                    return;
                }

                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);

                    if (bitmap != null && !isFinishing()) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

            } catch (Exception ignored) {
                // Keep the placeholder if the image URL cannot be loaded.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void deleteListing(String listingId) {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || token == null || token.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/listings"
                        + "?id=eq." + SupabaseHelper.encode(listingId)
                        + "&landlord_uid=eq." + SupabaseHelper.encode(userId);

                connection = SupabaseHelper.createConnection(
                        endpoint, "DELETE", token
                );

                int code = connection.getResponseCode();
                SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    showMessage("Listing deleted.");
                    loadMyListings();
                } else {
                    showMessage("Could not delete listing. Supabase error: " + code);
                }

            } catch (Exception e) {
                showMessage("Could not delete listing. Please try again.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(
                        MyListingsActivity.this,
                        message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
