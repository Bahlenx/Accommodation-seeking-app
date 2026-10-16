package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditListingActivity extends AppCompatActivity {

    private EditText nameInput;
    private EditText locationInput;
    private EditText priceInput;
    private EditText roomsInput;
    private EditText typeInput;
    private EditText descriptionInput;
    private Button saveButton;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String listingId = "";
    private String userId = "";
    private String accessToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_listing);

        nameInput = findViewById(R.id.edtEditPropertyName);
        locationInput = findViewById(R.id.edtEditLocation);
        priceInput = findViewById(R.id.edtEditPrice);
        roomsInput = findViewById(R.id.edtEditRooms);
        typeInput = findViewById(R.id.edtEditPropertyType);
        descriptionInput = findViewById(R.id.edtEditDescription);
        saveButton = findViewById(R.id.btnSaveListingChanges);

        Button backButton = findViewById(R.id.btnBackEditListing);
        backButton.setOnClickListener(v -> finish());

        listingId = getIntent().getStringExtra("LISTING_ID");

        if (listingId == null || listingId.trim().isEmpty()) {
            listingId = getIntent().getStringExtra("listingId");
        }

        userId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        if (listingId == null || listingId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Listing ID is missing.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        if (userId == null || userId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Please log in again to edit a listing.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        saveButton.setOnClickListener(v -> saveChanges());
        loadListing();
    }

    private void loadListing() {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listings?select=*"
                                + "&id=eq." + encode(listingId)
                                + "&landlord_uid=eq." + encode(userId)
                                + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint, "GET", accessToken
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage("Could not load listing. " + response);
                    return;
                }

                JSONArray data = new JSONArray(response);

                if (data.length() == 0) {
                    showMessage(
                            "Listing not found or you do not have permission to edit it."
                    );
                    return;
                }

                JSONObject listing = data.getJSONObject(0);

                runOnUiThread(() -> {
                    nameInput.setText(
                            listing.optString("property_name", "")
                    );
                    locationInput.setText(
                            listing.optString("location", "")
                    );
                    priceInput.setText(
                            listing.optString("price", "")
                    );
                    roomsInput.setText(
                            listing.optString("rooms", "")
                    );
                    typeInput.setText(
                            listing.optString("property_type", "")
                    );
                    descriptionInput.setText(
                            listing.optString("description", "")
                    );
                });

            } catch (Exception e) {
                showMessage("Could not load listing. Please try again.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void saveChanges() {
        String name = nameInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String price = priceInput.getText().toString().trim();
        String rooms = roomsInput.getText().toString().trim();
        String type = typeInput.getText().toString().trim();
        String description =
                descriptionInput.getText().toString().trim();

        if (name.isEmpty() || location.isEmpty() || price.isEmpty()
                || rooms.isEmpty() || type.isEmpty()
                || description.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please complete all fields.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        double parsedPrice;
        int parsedRooms;

        try {
            parsedPrice = Double.parseDouble(price);
            parsedRooms = Integer.parseInt(rooms);

            if (parsedPrice < 0 || parsedRooms < 0) {
                Toast.makeText(
                        this,
                        "Price and rooms cannot be negative.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(
                    this,
                    "Enter a valid price and number of rooms.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        saveButton.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listings?id=eq."
                                + encode(listingId)
                                + "&landlord_uid=eq."
                                + encode(userId);

                connection = SupabaseHelper.createConnection(
                        endpoint, "PATCH", accessToken
                );

                connection.setRequestProperty(
                        "Prefer", "return=representation"
                );

                JSONObject body = new JSONObject();
                body.put("property_name", name);
                body.put("location", location);
                body.put("price", parsedPrice);
                body.put("rooms", parsedRooms);
                body.put("property_type", type);
                body.put("description", description);

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                runOnUiThread(() -> {
                    saveButton.setEnabled(true);

                    if (code >= 200 && code < 300) {
                        Toast.makeText(
                                EditListingActivity.this,
                                "Listing updated successfully.",
                                Toast.LENGTH_SHORT
                        ).show();

                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(
                                EditListingActivity.this,
                                "Could not update listing: " + response,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    Toast.makeText(
                            EditListingActivity.this,
                            "Could not save changes. Check your connection.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20");
    }

    private void showMessage(String message) {
        runOnUiThread(() -> Toast.makeText(
                EditListingActivity.this,
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