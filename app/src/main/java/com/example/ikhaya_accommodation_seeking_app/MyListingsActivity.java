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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyListingsActivity extends AppCompatActivity {

    private LinearLayout listingsContainer;
    private TextView txtNoListings;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> editListingLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadMyListings()
            );

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

        if (userId == null || userId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        txtNoListings.setVisibility(View.GONE);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listings"
                                + "?select=*"
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(userId)
                                + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage("Could not load your listings.");
                    return;
                }

                JSONArray listings = new JSONArray(response);

                runOnUiThread(() -> {
                    listingsContainer.removeAllViews();

                    if (listings.length() == 0) {
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
                showMessage("Could not load your listings.");
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
        String propertyName = listing.optString("property_name", "Property");
        String location = listing.optString("location", "Location not provided");
        String price = listing.optString("price", "0");
        String rooms = listing.optString("rooms", "0");

        txtListingName.setText(propertyName);
        txtListingLocation.setText(location);
        txtListingPrice.setText("R" + price + " / month");
        txtListingRooms.setText(rooms + " rooms");

        imgListing.setImageResource(android.R.drawable.ic_menu_gallery);

        btnEditListing.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                Toast.makeText(
                        MyListingsActivity.this,
                        "Listing ID is missing.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Intent intent = new Intent(
                    MyListingsActivity.this,
                    EditListingActivity.class
            );

            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);
            intent.putExtra("propertyName", propertyName);

            editListingLauncher.launch(intent);
        });

        btnDeleteListing.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                Toast.makeText(
                        this,
                        "Listing ID is missing.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete listing?")
                    .setMessage(
                            "Are you sure you want to delete \""
                                    + propertyName
                                    + "\"?"
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) ->
                            deleteListing(listingId))
                    .show();
        });

        listingsContainer.addView(card);
    }

    private void deleteListing(String listingId) {
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (accessToken == null || accessToken.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listings?id=eq."
                                + SupabaseHelper.encode(listingId);

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "DELETE",
                        accessToken
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        Toast.makeText(
                                MyListingsActivity.this,
                                "Listing deleted.",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadMyListings();
                    } else {
                        Toast.makeText(
                                MyListingsActivity.this,
                                "Could not delete listing.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                showMessage("Could not delete listing.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showMessage(String message) {
        runOnUiThread(() -> Toast.makeText(
                MyListingsActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}