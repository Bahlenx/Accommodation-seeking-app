package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class SavedListingsActivity extends AppCompatActivity {

    private LinearLayout savedListingsContainer;
    private TextView txtNoSavedListings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_saved_listings
        );

        savedListingsContainer =
                findViewById(
                        R.id.savedListingsContainer
                );

        txtNoSavedListings =
                findViewById(
                        R.id.txtNoSavedListings
                );

        findViewById(
                R.id.btnBack
        ).setOnClickListener(
                v -> finish()
        );

        loadSavedListings();
    }

    private void loadSavedListings() {

        String userId =
                SupabaseHelper.getUserId(this);

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (userId == null
                || userId.isEmpty()
                || accessToken == null
                || accessToken.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        new Thread(() -> {

            try {

                String encodedUserId =
                        URLEncoder.encode(
                                userId,
                                "UTF-8"
                        );

                String endpoint =
                        "/rest/v1/saved_listings"
                                + "?select=*"
                                + "&tenant_uid=eq."
                                + encodedUserId
                                + "&order=created_at.desc";

                HttpURLConnection connection =
                        SupabaseHelper.createConnection(
                                endpoint,
                                "GET",
                                accessToken
                        );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(
                                connection
                        );

                connection.disconnect();

                if (responseCode < 200
                        || responseCode >= 300) {

                    showMessage(
                            "Could not load saved listings."
                    );

                    return;
                }

                JSONArray savedListings =
                        new JSONArray(response);

                runOnUiThread(() -> {

                    savedListingsContainer
                            .removeAllViews();

                    if (savedListings.length() == 0) {

                        txtNoSavedListings
                                .setVisibility(
                                        View.VISIBLE
                                );

                    } else {

                        txtNoSavedListings
                                .setVisibility(
                                        View.GONE
                                );

                        for (int i = 0;
                             i < savedListings.length();
                             i++) {

                            try {

                                JSONObject saved =
                                        savedListings
                                                .getJSONObject(i);

                                String listingId =
                                        saved.getString(
                                                "listing_id"
                                        );

                                loadListingDetails(
                                        listingId,
                                        accessToken
                                );

                            } catch (Exception e) {

                                e.printStackTrace();
                            }
                        }
                    }
                });

            } catch (Exception e) {

                e.printStackTrace();

                showMessage(
                        "Error loading saved listings."
                );
            }
        }).start();
    }

    private void loadListingDetails(
            String listingId,
            String accessToken
    ) {

        new Thread(() -> {

            try {

                String encodedListingId =
                        URLEncoder.encode(
                                listingId,
                                "UTF-8"
                        );

                String endpoint =
                        "/rest/v1/listings"
                                + "?select=*"
                                + "&id=eq."
                                + encodedListingId
                                + "&limit=1";

                HttpURLConnection connection =
                        SupabaseHelper.createConnection(
                                endpoint,
                                "GET",
                                accessToken
                        );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(
                                connection
                        );

                connection.disconnect();

                if (responseCode < 200
                        || responseCode >= 300) {

                    return;
                }

                JSONArray listings =
                        new JSONArray(response);

                if (listings.length() == 0) {
                    return;
                }

                JSONObject listing =
                        listings.getJSONObject(0);

                runOnUiThread(() ->
                        addListingCard(listing)
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }).start();
    }

    private void addListingCard(
            JSONObject listing
    ) {

        try {

            String listingId =
                    listing.getString("id");

            String propertyName =
                    listing.optString(
                            "property_name",
                            "Accommodation"
                    );

            String location =
                    listing.optString(
                            "location",
                            "Location not available"
                    );

            String price =
                    listing.optString(
                            "price",
                            "Price not available"
                    );

            String rooms =
                    listing.optString(
                            "rooms",
                            ""
                    );

            LinearLayout card =
                    new LinearLayout(this);

            card.setOrientation(
                    LinearLayout.VERTICAL
            );

            card.setPadding(
                    24,
                    24,
                    24,
                    24
            );

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            cardParams.setMargins(
                    0,
                    0,
                    0,
                    20
            );

            card.setLayoutParams(
                    cardParams
            );

            TextView name =
                    new TextView(this);

            name.setText(
                    propertyName
            );

            name.setTextSize(20);

            TextView locationText =
                    new TextView(this);

            locationText.setText(
                    "Location: " + location
            );

            locationText.setTextSize(15);

            TextView priceText =
                    new TextView(this);

            priceText.setText(
                    "Price: R" + price
            );

            priceText.setTextSize(16);

            card.addView(name);
            card.addView(locationText);
            card.addView(priceText);

            if (!rooms.isEmpty()) {

                TextView roomsText =
                        new TextView(this);

                roomsText.setText(
                        "Rooms: " + rooms
                );

                roomsText.setTextSize(15);

                card.addView(
                        roomsText
                );
            }

            card.setOnClickListener(v -> {

                Intent intent =
                        new Intent(
                                SavedListingsActivity.this,
                                PropertyDetailsActivity.class
                        );

                intent.putExtra(
                        "LISTING_ID",
                        listingId
                );

                startActivity(intent);
            });

            savedListingsContainer
                    .addView(card);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void showMessage(
            String message
    ) {

        runOnUiThread(() ->
                Toast.makeText(
                        SavedListingsActivity.this,
                        message,
                        Toast.LENGTH_SHORT
                ).show()
        );
    }
}