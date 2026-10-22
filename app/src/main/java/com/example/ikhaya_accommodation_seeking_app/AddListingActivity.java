package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.preference.PreferenceManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddListingActivity extends AppCompatActivity {

    private EditText propertyNameInput;
    private EditText locationInput;
    private EditText priceInput;
    private EditText roomsInput;
    private EditText descriptionInput;

    private AutoCompleteTextView propertyTypeInput;
    private AutoCompleteTextView waterSourceInput;
    private AutoCompleteTextView electricityInput;
    private AutoCompleteTextView sanitationInput;

    private ImageView imagePreview;
    private TextView locationStatus;
    private TextView imageCountText;
    private Button verifyLocationButton;
    private Button uploadImageButton;
    private Button submitButton;
    private ChipGroup amenitiesGroup;

    private final ArrayList<Uri> selectedImageUris = new ArrayList<>();
    private final ArrayList<String> selectedAmenities = new ArrayList<>();

    private boolean isLocationVerified = false;
    private double verifiedLatitude;
    private double verifiedLongitude;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetMultipleContents(),
                    uris -> {
                        if (uris == null || uris.isEmpty()) {
                            return;
                        }

                        selectedImageUris.clear();
                        selectedImageUris.addAll(uris);

                        imagePreview.setImageURI(selectedImageUris.get(0));

                        imageCountText.setText(
                                selectedImageUris.size()
                                        + (selectedImageUris.size() == 1
                                        ? " photo selected"
                                        : " photos selected")
                        );

                        uploadImageButton.setText("Change Selected Photos");

                        Toast.makeText(
                                this,
                                selectedImageUris.size() + " photo(s) selected",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    captureDeviceGps();
                } else {
                    Toast.makeText(this, "Permission denied. You can still drop a pin on the map.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_add_listing);

        propertyNameInput = findViewById(R.id.edtPropertyName);
        locationInput = findViewById(R.id.autoCompleteLocation);

        priceInput = findViewById(R.id.edtPrice);
        roomsInput = findViewById(R.id.edtRooms);
        descriptionInput = findViewById(R.id.edtDescription);

        propertyTypeInput = findViewById(R.id.dropdownPropertyType);
        waterSourceInput = findViewById(R.id.dropdownWaterSource);
        electricityInput = findViewById(R.id.dropdownElectricity);
        sanitationInput = findViewById(R.id.dropdownSanitation);

        imagePreview = findViewById(R.id.imgProperty);
        locationStatus = findViewById(R.id.tvLocationCoords);

        imageCountText = findViewById(R.id.tvSelectedImageCount);
        verifyLocationButton = findViewById(R.id.btnAdjustPinOnMap);

        uploadImageButton = findViewById(R.id.btnUploadImage);
        submitButton = findViewById(R.id.btnSubmitListing);
        amenitiesGroup = findViewById(R.id.cgSelectedAmenities);

        Button btnUseCurrentGps = findViewById(R.id.btnUseCurrentGps);
        if (btnUseCurrentGps != null) {
            btnUseCurrentGps.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    captureDeviceGps();
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            });
        }

        if (submitButton != null) {
            submitButton.setText("Submit for Review");
        }

        View backButton = findViewById(R.id.btnBackAddListing);
        backButton.setOnClickListener(v -> finish());

        setupDropdown(
                propertyTypeInput,
                R.array.sa_property_types
        );

        setupDropdown(
                waterSourceInput,
                R.array.sa_water_sources
        );

        setupDropdown(
                electricityInput,
                R.array.sa_electricity_types
        );

        setupDropdown(
                sanitationInput,
                R.array.sa_sanitation_types
        );

        TextView tvPriceLabel = findViewById(R.id.tvPriceLabel);
        TextView tvRoomsLabel = findViewById(R.id.tvRoomsLabel);
        ChipGroup cgOfferType = findViewById(R.id.cgOfferType);

        // 1. Change Price Title based on Rent vs Sell
        if (cgOfferType != null) {
            cgOfferType.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipSell)) {
                    if (tvPriceLabel != null) tvPriceLabel.setText(R.string.total_selling_price_r);
                } else {
                    if (tvPriceLabel != null) tvPriceLabel.setText(R.string.monthly_rental_price_r);
                }
            });
        }

        // 2. Hide "Rooms" if they select Land
        if (propertyTypeInput != null) {
            propertyTypeInput.setOnItemClickListener((parent, view, position, id) -> {
                String selectedType = parent.getItemAtPosition(position).toString().toLowerCase();

                if (selectedType.contains("land") || selectedType.contains("plot")) {
                    if (tvRoomsLabel != null) tvRoomsLabel.setVisibility(View.GONE);
                    if (roomsInput != null) {
                        roomsInput.setVisibility(View.GONE);
                        roomsInput.setText("0");
                    }
                } else {
                    if (tvRoomsLabel != null) tvRoomsLabel.setVisibility(View.VISIBLE);
                    if (roomsInput != null) {
                        roomsInput.setVisibility(View.VISIBLE);
                        if (roomsInput.getText().toString().equals("0")) {
                            roomsInput.setText("");
                        }
                    }
                }
            });
        }

        uploadImageButton.setOnClickListener(
                v -> imagePicker.launch("image/*")
        );

        verifyLocationButton.setOnClickListener(
                v -> showPinAdjustmentDialog(verifiedLatitude, verifiedLongitude)
        );

        if (locationInput instanceof AutoCompleteTextView) {
            AutoCompleteTextView autoCompleteLocation = (AutoCompleteTextView) locationInput;

            autoCompleteLocation.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence s, int start, int count, int after
                ) {}

                @Override
                public void onTextChanged(
                        CharSequence s, int start, int before, int count
                ) {
                    isLocationVerified = false;
                    if (locationStatus != null) {
                        locationStatus.setText(R.string.no_pin_dropped);
                        locationStatus.setTextColor(Color.parseColor("#8C7A70"));
                    }
                    searchHandler.removeCallbacks(searchRunnable);
                    if (s.length() >= 3) {
                        searchRunnable = () -> fetchAddressSuggestions(s.toString().trim(), autoCompleteLocation);
                        searchHandler.postDelayed(searchRunnable, 600);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        findViewById(R.id.btnSelectExtraAmenities)
                .setOnClickListener(v -> showAmenitiesPickerDialog());

        submitButton.setOnClickListener(v -> addListing());
    }

    private void setupDropdown(
            AutoCompleteTextView dropdown,
            int arrayResource
    ) {
        String[] items = getResources().getStringArray(arrayResource);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                items
        );

        dropdown.setAdapter(adapter);
        dropdown.setKeyListener(null);

        dropdown.setOnClickListener(v -> dropdown.showDropDown());

        dropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                dropdown.showDropDown();
            }
        });
    }

    private void showAmenitiesPickerDialog() {
        String[] options = {
                "Wi-Fi",
                "Parking",
                "Security",
                "Furnished",
                "Water included",
                "Electricity included",
                "Deposit required",
                "No smoking",
                "Shared kitchen",
                "Laundry facilities"
        };

        boolean[] checked = new boolean[options.length];

        for (int i = 0; i < options.length; i++) {
            checked[i] = selectedAmenities.contains(options[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Features and Rules")
                .setMultiChoiceItems(
                        options,
                        checked,
                        (dialog, which, isChecked) -> {
                            if (isChecked) {
                                if (!selectedAmenities.contains(options[which])) {
                                    selectedAmenities.add(options[which]);
                                }
                            } else {
                                selectedAmenities.remove(options[which]);
                            }
                        }
                )
                .setPositiveButton("Done", (dialog, which) -> {
                    amenitiesGroup.removeAllViews();

                    for (String amenity : selectedAmenities) {
                        Chip chip = new Chip(this);
                        chip.setText(amenity);
                        chip.setCloseIconVisible(true);
                        chip.setOnCloseIconClickListener(v -> {
                            selectedAmenities.remove(amenity);
                            amenitiesGroup.removeView(chip);
                        });
                        amenitiesGroup.addView(chip);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void verifyAddress() {
        String addressText = locationInput.getText()
                .toString()
                .trim();

        if (addressText.isEmpty()) {
            locationInput.setError("Enter the property location first.");
            return;
        }

        verifyLocationButton.setEnabled(false);
        locationStatus.setText("Checking location...");

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(
                        AddListingActivity.this,
                        Locale.getDefault()
                );

                List<Address> addresses =
                        geocoder.getFromLocationName(addressText, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    double latitude = address.getLatitude();
                    double longitude = address.getLongitude();

                    runOnUiThread(() -> {
                        verifiedLatitude = latitude;
                        verifiedLongitude = longitude;
                        isLocationVerified = true;

                        locationStatus.setText(
                                "Location verified successfully."
                        );

                        verifyLocationButton.setEnabled(true);
                    });
                } else {
                    runOnUiThread(() -> {
                        isLocationVerified = false;
                        locationStatus.setText(
                                "Location not found. Check the address and try again."
                        );
                        verifyLocationButton.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    isLocationVerified = false;
                    locationStatus.setText(
                            "Could not verify location. Check your connection and try again."
                    );
                    verifyLocationButton.setEnabled(true);
                });
            }
        });
    }

    private void captureDeviceGps() {
        locationStatus.setText("Acquiring satellite lock...");

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                // Try GPS first, fallback to cell-tower network location
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (location != null) {
                    verifiedLatitude = location.getLatitude();
                    verifiedLongitude = location.getLongitude();
                    isLocationVerified = true;

                    runOnUiThread(() -> {
                        locationStatus.setText(String.format(Locale.getDefault(),
                                "GPS Captured: %.5f, %.5f (Verified)", verifiedLatitude, verifiedLongitude));
                        locationStatus.setTextColor(Color.parseColor("#2DCE89")); // Green
                    });
                } else {
                    runOnUiThread(() -> {
                        locationStatus.setText("GPS signal weak. Try stepping outside or drop a pin manually.");
                    });
                }
            } catch (SecurityException e) {
                // Handled by the permission launcher
            }
        }
    }

    private void fetchAddressSuggestions(String query, AutoCompleteTextView autoCompleteLocation) {
        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(AddListingActivity.this, Locale.getDefault());
                List<Address> matches = geocoder.getFromLocationName(query + ", South Africa", 5);

                if (matches != null && !matches.isEmpty()) {
                    List<String> displayStrings = new ArrayList<>();
                    for (Address addr : matches) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(addr.getAddressLine(i));
                        }
                        displayStrings.add(sb.toString());
                    }

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                    AddListingActivity.this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    displayStrings
                            );
                            autoCompleteLocation.setAdapter(adapter);
                            autoCompleteLocation.showDropDown();
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void showPinAdjustmentDialog(double initialLat, double initialLon) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_pin_picker);

        MapView pickerMap = dialog.findViewById(R.id.pickerMapView);
        Button btnConfirmPin = dialog.findViewById(R.id.btnConfirmPin);
        View btnLocateMe = dialog.findViewById(R.id.btnDialogLocateMe);

        if (pickerMap != null) {
            XYTileSource tileSource = new XYTileSource(
                    "OpenStreetMapDE",
                    0,
                    19,
                    256,
                    ".png",
                    new String[]{ "https://tile.openstreetmap.de/" },
                    "© OpenStreetMap contributors"
            );
            pickerMap.setTileSource(tileSource);
            pickerMap.setMultiTouchControls(true);

            GeoPoint startPoint = (!Double.isNaN(initialLat) && initialLat != 0.0)
                    ? new GeoPoint(initialLat, initialLon)
                    : new GeoPoint(-26.2041, 28.0473);

            pickerMap.getController().setZoom(16.0);
            pickerMap.getController().setCenter(startPoint);

            pickerMap.onResume();

            dialog.setOnDismissListener(d -> {
                pickerMap.onPause();
                pickerMap.onDetach();
            });

            if (btnLocateMe != null) {
                btnLocateMe.setOnClickListener(v -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        if (locationManager != null) {
                            try {
                                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                if (loc == null) loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                                if (loc != null) {
                                    GeoPoint myPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                                    pickerMap.getController().animateTo(myPoint);
                                    pickerMap.getController().setZoom(18.0);
                                } else {
                                    Toast.makeText(this, "Searching for GPS signal...", Toast.LENGTH_SHORT).show();
                                }
                            } catch (SecurityException ignored) {}
                        }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                    }
                });
            }

            if (btnConfirmPin != null) {
                btnConfirmPin.setOnClickListener(v -> {
                    GeoPoint finalCenter = (GeoPoint) pickerMap.getMapCenter();
                    verifiedLatitude = finalCenter.getLatitude();
                    verifiedLongitude = finalCenter.getLongitude();
                    isLocationVerified = true;

                    if (locationStatus != null) {
                        locationStatus.setText(String.format(
                                Locale.getDefault(),
                                "Pinned: %.5f, %.5f (Verified)",
                                verifiedLatitude,
                                verifiedLongitude
                        ));
                        locationStatus.setTextColor(Color.parseColor("#2DCE89"));
                    }

                    dialog.dismiss();
                });
            }
        }

        dialog.show();
    }

    private void addListing() {
        String propertyName = propertyNameInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();
        String roomsText = roomsInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        String propertyType = propertyTypeInput.getText().toString().trim();
        String waterSource = waterSourceInput.getText().toString().trim();
        String electricity = electricityInput.getText().toString().trim();
        String sanitation = sanitationInput.getText().toString().trim();

        if (propertyName.isEmpty()
                || location.isEmpty()
                || priceText.isEmpty()
                || roomsText.isEmpty()
                || description.isEmpty()
                || propertyType.isEmpty()
                || waterSource.isEmpty()
                || electricity.isEmpty()
                || sanitation.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please complete all required fields.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (!isLocationVerified) {
            Toast.makeText(
                    this,
                    "Verify the property location before publishing.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please select at least one property photo.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        double price;
        int rooms;

        try {
            price = Double.parseDouble(priceText);
            rooms = Integer.parseInt(roomsText);
        } catch (NumberFormatException e) {
            Toast.makeText(
                    this,
                    "Enter a valid price and number of rooms.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        boolean isLand = propertyType.toLowerCase().contains("land") || propertyType.toLowerCase().contains("plot");
        if (price <= 0 || (rooms <= 0 && !isLand)) {
            Toast.makeText(
                    this,
                    isLand ? "Price must be greater than zero." : "Price and rooms must be greater than zero.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(
                    this,
                    "Your session has expired. Please log in again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        submitButton.setEnabled(false);
        uploadImageButton.setEnabled(false);
        verifyLocationButton.setEnabled(false);

        executor.execute(() -> {
            try {
                HttpURLConnection connection =
                        SupabaseHelper.createConnection(
                                "/rest/v1/listings",
                                "POST",
                                accessToken
                        );

                connection.setRequestProperty(
                        "Prefer",
                        "return=representation"
                );

                JSONObject listing = new JSONObject();
                listing.put("landlord_uid", userId);

                ChipGroup cgOfferType = findViewById(R.id.cgOfferType);
                String offerType = (cgOfferType != null && cgOfferType.getCheckedChipId() == R.id.chipSell) ? "sale" : "rent";
                listing.put("offer_type", offerType);

                listing.put("property_name", propertyName);
                listing.put("location", location);
                listing.put("price", price);
                listing.put("rooms", rooms);
                listing.put("property_type", propertyType);
                listing.put("description", description);

                listing.put("water_source", waterSource);
                listing.put("electricity", electricity);
                listing.put("sanitation", sanitation);

                if (isLocationVerified) {
                    listing.put("latitude", verifiedLatitude);
                    listing.put("longitude", verifiedLongitude);
                }

                // Force into pending state until an admin approves it
                listing.put("status", "PENDING");
                listing.put("availability", "Pending");

                SupabaseHelper.writeJson(connection, listing);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    Log.e("iKhayaListing", response);
                    showError("Could not create listing. Check your Supabase table permissions.");
                    return;
                }

                JSONArray returned = new JSONArray(response);

                if (returned.length() == 0) {
                    showError("The listing was not returned by Supabase.");
                    return;
                }

                String listingId =
                        returned.getJSONObject(0).getString("id");

                ArrayList<String> uploadedUrls = new ArrayList<>();

                for (int i = 0; i < selectedImageUris.size(); i++) {
                    String imagePath = userId
                            + "/"
                            + listingId
                            + "/property_"
                            + System.currentTimeMillis()
                            + "_"
                            + i
                            + ".jpg";

                    boolean uploaded = uploadImage(
                            accessToken,
                            selectedImageUris.get(i),
                            imagePath
                    );

                    if (!uploaded) {
                        Log.e(
                                "iKhayaImage",
                                "Failed to upload image " + i
                        );
                        continue;
                    }

                    String publicUrl = SupabaseHelper.getPublicStorageUrl(
                            "property-images",
                            imagePath
                    );

                    uploadedUrls.add(publicUrl);

                    saveImageRecord(
                            accessToken,
                            listingId,
                            publicUrl
                    );
                }

                if (uploadedUrls.isEmpty()) {
                    showError(
                            "The listing was created, but no photos uploaded successfully."
                    );
                    return;
                }

                updateListingImagePath(
                        accessToken,
                        listingId,
                        uploadedUrls.get(0)
                );

                runOnUiThread(() -> {
                    Toast.makeText(
                            AddListingActivity.this,
                            "Listing submitted for review with " + uploadedUrls.size() + " photo(s).",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e("iKhayaListing", "Add listing failed", e);
                showError("Error adding listing. Please try again.");
            }
        });
    }

    private boolean uploadImage(
            String accessToken,
            Uri imageUri,
            String imagePath
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createStorageConnection(
                    "property-images",
                    imagePath,
                    "POST",
                    accessToken
            );

            String mimeType = getContentResolver().getType(imageUri);
            if (mimeType == null) {
                mimeType = "image/jpeg";
            }

            connection.setRequestProperty("Content-Type", mimeType);
            connection.setRequestProperty("x-upsert", "true");
            connection.setDoOutput(true);

            try (InputStream inputStream =
                         getContentResolver().openInputStream(imageUri);
                 OutputStream outputStream = connection.getOutputStream()) {

                if (inputStream == null) {
                    return false;
                }

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            Log.d(
                    "iKhayaImage",
                    "Upload response: " + code + " " + response
            );

            return code >= 200 && code < 300;

        } catch (Exception e) {
            Log.e("iKhayaImage", "Image upload failed", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void saveImageRecord(
            String accessToken,
            String listingId,
            String imageUrl
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    "/rest/v1/listing_images",
                    "POST",
                    accessToken
            );

            JSONObject record = new JSONObject();
            record.put("listing_id", listingId);
            record.put("image_url", imageUrl);

            SupabaseHelper.writeJson(connection, record);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e("iKhayaImage", "Could not save image record: " + response);
            }

        } catch (Exception e) {
            Log.e("iKhayaImage", "Could not save image record", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void updateListingImagePath(
            String accessToken,
            String listingId,
            String imageUrl
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    "/rest/v1/listings?id=eq."
                            + SupabaseHelper.encode(listingId),
                    "PATCH",
                    accessToken
            );

            JSONObject body = new JSONObject();
            body.put("image_url", imageUrl);

            SupabaseHelper.writeJson(connection, body);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e("iKhayaListing", "Image URL update failed: " + response);
            }

        } catch (Exception e) {
            Log.e("iKhayaListing", "Could not update image URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            submitButton.setEnabled(true);
            uploadImageButton.setEnabled(true);
            verifyLocationButton.setEnabled(true);

            Toast.makeText(
                    AddListingActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}