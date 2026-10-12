
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PropertyDetailsActivity extends AppCompatActivity {

    private ImageView propertyImage;
    private TextView propertyName;
    private TextView propertyPrice;
    private TextView propertyLocation;
    private TextView propertyDescription;

    private ChipGroup chipGroupAmenities;
    private ChipGroup chipGroupRules;

    private MapView mapView;
    private ProgressBar progressTranslate;
    private TextView btnTranslate;

    private String listingId;
    private String landlordUid;
    private String locationText = "";

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Configuration.getInstance().load(
                this,
                getSharedPreferences("osmdroid", MODE_PRIVATE)
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property_details);

        listingId = getIntent().getStringExtra("LISTING_ID");

        if (listingId == null || listingId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    getString(R.string.something_went_wrong),
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }

        initializeViews();
        initializeMap();
        setupButtons();
        loadListing();
    }

    private void initializeViews() {
        propertyImage = findViewById(R.id.propertyImage);
        propertyName = findViewById(R.id.propertyName);
        propertyPrice = findViewById(R.id.propertyPrice);
        propertyLocation = findViewById(R.id.propertyLocation);
        propertyDescription = findViewById(R.id.propertyDescription);

        chipGroupAmenities = findViewById(R.id.chipGroupAmenities);
        chipGroupRules = findViewById(R.id.chipGroupRules);

        progressTranslate = findViewById(R.id.progressTranslate);
        btnTranslate = findViewById(R.id.btnTranslate);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void initializeMap() {
        mapView = findViewById(R.id.mapView);

        if (mapView != null) {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.getController().setZoom(14.0);
        }
    }

    private void setupButtons() {
        Button btnOpenMap = findViewById(R.id.btnOpenMap);
        Button btnGetDirections = findViewById(R.id.btnGetDirections);
        Button btnOpenFullMap = findViewById(R.id.btnOpenFullMap);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnContact = findViewById(R.id.btnContact);

        if (btnOpenMap != null) {
            btnOpenMap.setOnClickListener(v -> openLocationInMaps(false));
        }

        if (btnGetDirections != null) {
            btnGetDirections.setOnClickListener(v -> openLocationInMaps(true));
        }

        if (btnOpenFullMap != null) {
            btnOpenFullMap.setOnClickListener(v -> openLocationInMaps(false));
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveListing());
        }

        if (btnContact != null) {
            btnContact.setOnClickListener(v -> contactLandlord());
        }

        if (btnTranslate != null) {
            btnTranslate.setOnClickListener(v ->
                    Toast.makeText(
                            this,
                            "Translation is not available yet.",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    }

    private void loadListing() {
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/listings"
                        + "?select=*"
                        + "&id=eq."
                        + URLEncoder.encode(listingId, "UTF-8")
                        + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    Log.e("PropertyDetails", "Load listing failed: " + response);
                    showMessage("Could not load this accommodation.");
                    return;
                }

                JSONArray listings = new JSONArray(response);

                if (listings.length() == 0) {
                    showMessage("This listing could not be found.");
                    return;
                }

                JSONObject listing = listings.getJSONObject(0);

                String imageUrl = listing.optString("image_url", "");
                String name = listing.optString("property_name", "Accommodation");
                String price = listing.optString("price", "Price unavailable");
                String location = listing.optString("location", "Location unavailable");
                String description = listing.optString(
                        "description",
                        "No description provided."
                );
                String rooms = listing.optString("rooms", "");
                String propertyType = listing.optString("property_type", "");
                String availability = listing.optString("availability", "");
                String landlord = listing.optString("landlord_uid", "");

                landlordUid = landlord;
                locationText = location;

                runOnUiThread(() -> {
                    propertyName.setText(name);
                    propertyPrice.setText("R" + price + " per month");
                    propertyLocation.setText(location);

                    StringBuilder fullDescription = new StringBuilder(description);

                    if (!propertyType.isEmpty()) {
                        fullDescription.append("\n\nAccommodation type: ")
                                .append(propertyType);
                    }

                    if (!rooms.isEmpty()) {
                        fullDescription.append("\nRooms: ").append(rooms);
                    }

                    if (!availability.isEmpty()) {
                        fullDescription.append("\nAvailability: ")
                                .append(availability);
                    }

                    propertyDescription.setText(fullDescription.toString());
                });

                if (!imageUrl.trim().isEmpty()
                        && !imageUrl.equalsIgnoreCase("null")) {
                    loadPropertyImage(imageUrl);
                } else {
                    loadFirstImageFromImageTable(accessToken);
                }

                updateMap(location);

            } catch (Exception e) {
                Log.e("PropertyDetails", "Error loading listing", e);
                showMessage("Error loading accommodation details.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void loadFirstImageFromImageTable(String accessToken) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/listing_images"
                        + "?select=image_url"
                        + "&listing_id=eq."
                        + URLEncoder.encode(listingId, "UTF-8")
                        + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    Log.e("PropertyDetails", "Could not load image records: " + response);
                    return;
                }

                JSONArray images = new JSONArray(response);

                if (images.length() > 0) {
                    String imageUrl = images.getJSONObject(0)
                            .optString("image_url", "");

                    if (!imageUrl.trim().isEmpty()
                            && !imageUrl.equalsIgnoreCase("null")) {
                        loadPropertyImage(imageUrl);
                    }
                }
            } catch (Exception e) {
                Log.e("PropertyDetails", "Could not load listing image records", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void loadPropertyImage(String imageUrl) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(imageUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");

                int code = connection.getResponseCode();

                if (code < 200 || code >= 300) {
                    Log.e("PropertyDetails", "Image request returned " + code);
                    return;
                }

                Bitmap bitmap = BitmapFactory.decodeStream(
                        connection.getInputStream()
                );

                if (bitmap != null && !isFinishing()) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && propertyImage != null) {
                            propertyImage.setImageBitmap(bitmap);
                            propertyImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("PropertyDetails", "Could not display property image", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void updateMap(String location) {
        if (mapView == null || location == null || location.trim().isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(
                        PropertyDetailsActivity.this,
                        Locale.getDefault()
                );

                List<Address> addresses = geocoder.getFromLocationName(
                        location,
                        1
                );

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    GeoPoint point = new GeoPoint(
                            address.getLatitude(),
                            address.getLongitude()
                    );

                    runOnUiThread(() -> {
                        if (mapView != null) {
                            mapView.getController().setCenter(point);
                            mapView.invalidate();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("PropertyDetails", "Could not locate address on map", e);
            }
        });
    }

    private void openLocationInMaps(boolean directions) {
        if (locationText == null || locationText.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Location is not available.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String encodedLocation = Uri.encode(locationText);
        Uri uri;

        if (directions) {
            uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                    + encodedLocation);
        } else {
            uri = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                    + encodedLocation);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Could not open Maps.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void saveListing() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/saved_listings",
                        "POST",
                        accessToken
                );

                JSONObject body = new JSONObject();
                body.put("tenant_uid", userId);
                body.put("listing_id", listingId);

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    showMessage("Accommodation saved.");
                } else if (code == 409) {
                    showMessage("This accommodation is already saved.");
                } else {
                    Log.e("PropertyDetails", "Save failed: " + response);
                    showMessage("Could not save this accommodation.");
                }
            } catch (Exception e) {
                Log.e("PropertyDetails", "Save listing failed", e);
                showMessage("Error saving accommodation.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void contactLandlord() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {
            showMessage("Please log in again.");
            return;
        }

        if (landlordUid == null || landlordUid.isEmpty()) {
            showMessage("Landlord details are not available.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/enquiries",
                        "POST",
                        accessToken
                );

                JSONObject body = new JSONObject();
                body.put("tenant_uid", userId);
                body.put("landlord_uid", landlordUid);
                body.put("listing_id", listingId);
                body.put("message", "I am interested in this accommodation.");

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    showMessage("Your enquiry has been sent.");
                } else {
                    Log.e("PropertyDetails", "Enquiry failed: " + response);
                    showMessage("Could not send your enquiry.");
                }
            } catch (Exception e) {
                Log.e("PropertyDetails", "Could not send enquiry", e);
                showMessage("Error sending your enquiry.");
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
                        PropertyDetailsActivity.this,
                        message,
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) {
            mapView.onDetach();
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}