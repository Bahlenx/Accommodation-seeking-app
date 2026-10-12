package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;

public class MyListingsActivity extends AppCompatActivity {

    private LinearLayout listingsContainer;
    private TextView txtNoListings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_listings);

        Button btnBack = findViewById(R.id.btnBack);

        listingsContainer = findViewById(R.id.myListingsContainer);
        txtNoListings = findViewById(R.id.txtNoListings);

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
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() ||
                accessToken == null || accessToken.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/listings" +
                                "?select=*" +
                                "&landlord_uid=eq." +
                                SupabaseHelper.encode(userId) +
                                "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                String response =
                        SupabaseHelper.readResponse(connection);

                JSONArray listings = new JSONArray(response);

                runOnUiThread(() -> {

                    listingsContainer.removeAllViews();

                    if (listings.length() == 0) {

                        txtNoListings.setVisibility(View.VISIBLE);

                        return;
                    }

                    txtNoListings.setVisibility(View.GONE);

                    for (int i = 0; i < listings.length(); i++) {

                        JSONObject listing =
                                listings.optJSONObject(i);

                        if (listing != null) {
                            addListingCard(listing);
                        }
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MyListingsActivity.this,
                                "Could not load your listings.",
                                Toast.LENGTH_SHORT
                        ).show()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    private void addListingCard(JSONObject listing) {

        View card = LayoutInflater.from(this)
                .inflate(
                        R.layout.item_listing,
                        listingsContainer,
                        false
                );

        ImageView imgListing =
                card.findViewById(R.id.imgListing);

        TextView txtListingName =
                card.findViewById(R.id.txtListingName);

        TextView txtListingLocation =
                card.findViewById(R.id.txtListingLocation);

        TextView txtListingPrice =
                card.findViewById(R.id.txtListingPrice);

        TextView txtListingRooms =
                card.findViewById(R.id.txtListingRooms);

        Button btnEditListing =
                card.findViewById(R.id.btnEditListing);

        Button btnDeleteListing =
                card.findViewById(R.id.btnDeleteListing);

        String listingId =
                listing.optString("id", "");

        String propertyName =
                listing.optString("property_name", "Property");

        String location =
                listing.optString("location", "Location not provided");

        String price =
                listing.optString("price", "0");

        String rooms =
                listing.optString("rooms", "0");

        String imageUrl =
                listing.optString("image_url", "");

        txtListingName.setText(propertyName);
        txtListingLocation.setText(location);
        txtListingPrice.setText("R" + price + " / month");
        txtListingRooms.setText(rooms + " rooms");

        // The current database stores the image URL/path.
        // Image loading can be added once the storage URL handling is finalized.
        imgListing.setImageResource(
                android.R.drawable.ic_menu_gallery
        );

        btnEditListing.setOnClickListener(v -> {

            Toast.makeText(
                    MyListingsActivity.this,
                    "Edit listing will be added next.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        btnDeleteListing.setOnClickListener(v -> {

            if (!listingId.isEmpty()) {
                deleteListing(listingId);
            }
        });

        listingsContainer.addView(card);
    }

    private void deleteListing(String listingId) {

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (accessToken == null || accessToken.isEmpty()) {
            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/listings?id=eq." +
                                SupabaseHelper.encode(listingId);

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "DELETE",
                        accessToken
                );

                int responseCode =
                        connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {

                    runOnUiThread(() -> {

                        Toast.makeText(
                                MyListingsActivity.this,
                                "Listing deleted.",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadMyListings();
                    });

                } else {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MyListingsActivity.this,
                                    "Could not delete listing.",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MyListingsActivity.this,
                                "Could not delete listing.",
                                Toast.LENGTH_SHORT
                        ).show()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }
}