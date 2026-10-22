package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.app.Dialog;
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
import android.preference.PreferenceManager;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
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

    private static final String TAG = "iKhayaListing";

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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
                                        ? (" " + getString(R.string.photo_selected_singular))
                                        : (" " + getString(R.string.photo_selected_plural)))
                        );

                        uploadImageButton.setText(R.string.btn_change_selected_photos);

                        Toast.makeText(
                                this,
                                selectedImageUris.size() + " " + getString(R.string.photos_selected_toast),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    captureDeviceGps();
                } else {
                    Toast.makeText(this, R.string.permission_location_denied_map_fallback, Toast.LENGTH_LONG).show();
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
            submitButton.setText(R.string.btn_submit_for_review);
        }

        View backButton = findViewById(R.id.btnBackAddListing);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        setupDropdown(propertyTypeInput, R.array.sa_property_types);
        setupDropdown(waterSourceInput, R.array.sa_water_sources);
        setupDropdown(electricityInput, R.array.sa_electricity_types);
        setupDropdown(sanitationInput, R.array.sa_sanitation_types);

        TextView tvPriceLabel = findViewById(R.id.tvPriceLabel);
        TextView tvRoomsLabel = findViewById(R.id.tvRoomsLabel);
        ChipGroup cgOfferType = findViewById(R.id.cgOfferType);

        if (cgOfferType != null) {
            cgOfferType.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipSell)) {
                    if (tvPriceLabel != null) tvPriceLabel.setText(R.string.total_selling_price_r);
                } else {
                    if (tvPriceLabel != null) tvPriceLabel.setText(R.string.monthly_rental_price_r);
                }
            });
        }

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

        uploadImageButton.setOnClickListener(v -> imagePicker.launch("image/*"));

        verifyLocationButton.setOnClickListener(v -> showPinAdjustmentDialog(verifiedLatitude, verifiedLongitude));

        if (locationInput instanceof AutoCompleteTextView) {
            AutoCompleteTextView autoCompleteLocation = (AutoCompleteTextView) locationInput;

            autoCompleteLocation.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
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

        View btnExtraAmenities = findViewById(R.id.btnSelectExtraAmenities);
        if (btnExtraAmenities != null) {
            btnExtraAmenities.setOnClickListener(v -> showAmenitiesPickerDialog());
        }

        submitButton.setOnClickListener(v -> addListing());
    }

    private void setupDropdown(AutoCompleteTextView dropdown, int arrayResource) {
        if (dropdown == null) return;
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
            if (hasFocus) dropdown.showDropDown();
        });
    }

    private void showAmenitiesPickerDialog() {
        String[] options = {
                getString(R.string.amenity_wifi),
                getString(R.string.amenity_parking),
                getString(R.string.amenity_security),
                getString(R.string.amenity_furnished),
                getString(R.string.amenity_water_included),
                getString(R.string.amenity_electricity_included),
                getString(R.string.amenity_deposit_required),
                getString(R.string.amenity_no_smoking),
                getString(R.string.amenity_shared_kitchen),
                getString(R.string.amenity_laundry_facilities)
        };

        boolean[] checked = new boolean[options.length];

        for (int i = 0; i < options.length; i++) {
            checked[i] = selectedAmenities.contains(options[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_select_features_and_rules)
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
                .setPositiveButton(R.string.btn_done, (dialog, which) -> {
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
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void captureDeviceGps() {
        locationStatus.setText(R.string.acquiring_satellite_lock);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (location != null) {
                    verifiedLatitude = location.getLatitude();
                    verifiedLongitude = location.getLongitude();
                    isLocationVerified = true;

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        locationStatus.setText(String.format(Locale.getDefault(),
                                getString(R.string.format_gps_captured), verifiedLatitude, verifiedLongitude));
                        locationStatus.setTextColor(Color.parseColor("#2DCE89"));
                    });
                } else {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        locationStatus.setText(R.string.gps_signal_weak);
                    });
                }
            } catch (SecurityException ignored) {}
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
                                    Toast.makeText(this, R.string.searching_gps_signal, Toast.LENGTH_SHORT).show();
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
                                getString(R.string.format_pinned_coords),
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

            Toast.makeText(this, R.string.error_complete_required_fields, Toast.LENGTH_LONG).show();
            return;
        }

        if (!isLocationVerified) {
            Toast.makeText(this, R.string.error_verify_location_first, Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, R.string.error_select_photo_first, Toast.LENGTH_LONG).show();
            return;
        }

        double price;
        int rooms;

        try {
            price = Double.parseDouble(priceText);
            rooms = Integer.parseInt(roomsText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_price_or_rooms, Toast.LENGTH_LONG).show();
            return;
        }

        boolean isLand = propertyType.toLowerCase().contains("land") || propertyType.toLowerCase().contains("plot");
        if (price <= 0 || (rooms <= 0 && !isLand)) {
            Toast.makeText(this, isLand ? getString(R.string.error_price_greater_zero) : getString(R.string.error_price_rooms_greater_zero), Toast.LENGTH_LONG).show();
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, R.string.session_expired_login_again, Toast.LENGTH_LONG).show();
            return;
        }

        submitButton.setEnabled(false);
        uploadImageButton.setEnabled(false);
        verifyLocationButton.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/listings",
                        "POST",
                        accessToken
                );

                connection.setRequestProperty("Prefer", "return=representation");

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

                listing.put("status", "PENDING");
                listing.put("availability", "Pending");

                SupabaseHelper.writeJson(connection, listing);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    Log.e(TAG, "Create listing response: " + response);
                    showError(getString(R.string.error_check_supabase_permissions));
                    return;
                }

                JSONArray returned = new JSONArray(response);

                if (returned.length() == 0) {
                    showError(getString(R.string.error_listing_not_returned));
                    return;
                }

                String listingId = returned.getJSONObject(0).getString("id");

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

                    boolean uploaded = uploadImage(accessToken, selectedImageUris.get(i), imagePath);

                    if (!uploaded) {
                        Log.e(TAG, "Failed to upload image " + i);
                        continue;
                    }

                    String publicUrl = SupabaseHelper.getPublicStorageUrl("property-images", imagePath);
                    uploadedUrls.add(publicUrl);
                    saveImageRecord(accessToken, listingId, publicUrl);
                }

                if (uploadedUrls.isEmpty()) {
                    showError(getString(R.string.error_no_photos_uploaded));
                    return;
                }

                updateListingImagePath(accessToken, listingId, uploadedUrls.get(0));

                SupabaseHelper.createNotification(
                        userId,
                        "Listing Under Review",
                        "Your listing '" + propertyName + "' has been submitted and is pending admin approval.",
                        accessToken
                );

                SupabaseHelper.notifyAdmins(
                        "New Listing Submitted",
                        "A landlord has submitted '" + propertyName + "'. It requires review.",
                        accessToken
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(
                            AddListingActivity.this,
                            getString(R.string.listing_submitted_success_prefix) + " " + uploadedUrls.size() + " " + getString(R.string.listing_submitted_success_suffix),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Add listing failed", e);
                showError(getString(R.string.error_adding_listing_general));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private boolean uploadImage(String accessToken, Uri imageUri, String imagePath) {
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

            try (InputStream inputStream = getContentResolver().openInputStream(imageUri);
                 OutputStream outputStream = connection.getOutputStream()) {

                if (inputStream == null) return false;

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            Log.d(TAG, "Upload response: " + code + " " + response);
            return code >= 200 && code < 300;

        } catch (Exception e) {
            Log.e(TAG, "Image upload failed", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void saveImageRecord(String accessToken, String listingId, String imageUrl) {
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
                Log.e(TAG, "Could not save image record: " + response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not save image record", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void updateListingImagePath(String accessToken, String listingId, String imageUrl) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    "/rest/v1/listings?id=eq." + SupabaseHelper.encode(listingId),
                    "PATCH",
                    accessToken
            );

            JSONObject body = new JSONObject();
            body.put("image_url", imageUrl);

            SupabaseHelper.writeJson(connection, body);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e(TAG, "Image URL update failed: " + response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not update image URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            submitButton.setEnabled(true);
            uploadImageButton.setEnabled(true);
            verifyLocationButton.setEnabled(true);

            Toast.makeText(AddListingActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}