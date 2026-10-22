
package com.example.ikhaya_accommodation_seeking_app;

import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean isLocationVerified = false;
    private double verifiedLatitude;
    private double verifiedLongitude;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetMultipleContents(),
                    uris -> {
                        if (uris == null || uris.isEmpty()) return;
                        if (imagePreview == null || imageCountText == null
                                || uploadImageButton == null) return;

                        selectedImageUris.clear();
                        selectedImageUris.addAll(uris);

                        imagePreview.setImageURI(selectedImageUris.get(0));
                        imageCountText.setText(
                                selectedImageUris.size()
                                        + (selectedImageUris.size() == 1
                                        ? " photo selected" : " photos selected")
                        );
                        uploadImageButton.setText("Change Selected Photos");

                        Toast.makeText(
                                this,
                                selectedImageUris.size() + " photo(s) selected",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_listing);

        propertyNameInput = findViewById(R.id.edtPropertyName);
        locationInput = findViewById(R.id.edtLocation);
        priceInput = findViewById(R.id.edtPrice);
        roomsInput = findViewById(R.id.edtRooms);
        descriptionInput = findViewById(R.id.edtDescription);

        propertyTypeInput = findViewById(R.id.dropdownPropertyType);
        waterSourceInput = findViewById(R.id.dropdownWaterSource);
        electricityInput = findViewById(R.id.dropdownElectricity);
        sanitationInput = findViewById(R.id.dropdownSanitation);

        imagePreview = findViewById(R.id.imgProperty);
        locationStatus = findViewById(R.id.tvLocationStatus);
        imageCountText = findViewById(R.id.tvSelectedImageCount);
        verifyLocationButton = findViewById(R.id.btnVerifyLocation);
        uploadImageButton = findViewById(R.id.btnUploadImage);
        submitButton = findViewById(R.id.btnSubmitListing);
        amenitiesGroup = findViewById(R.id.cgSelectedAmenities);

        android.view.View backButton = findViewById(R.id.btnBackAddListing);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (propertyNameInput == null || locationInput == null
                || priceInput == null || roomsInput == null
                || descriptionInput == null || propertyTypeInput == null
                || waterSourceInput == null || electricityInput == null
                || sanitationInput == null || imagePreview == null
                || locationStatus == null || imageCountText == null
                || verifyLocationButton == null || uploadImageButton == null
                || submitButton == null || amenitiesGroup == null) {
            Log.e(TAG, "Required Add Listing layout view is missing");
            Toast.makeText(
                    this,
                    "Could not open Add Listing. Check the XML layout.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        setupDropdown(propertyTypeInput, R.array.sa_property_types);
        setupDropdown(waterSourceInput, R.array.sa_water_sources);
        setupDropdown(electricityInput, R.array.sa_electricity_types);
        setupDropdown(sanitationInput, R.array.sa_sanitation_types);

        uploadImageButton.setOnClickListener(
                v -> imagePicker.launch("image/*")
        );

        verifyLocationButton.setOnClickListener(v -> verifyAddress());

        locationInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                isLocationVerified = false;
                locationStatus.setText(
                        "Location has not been verified yet."
                );
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        View amenitiesButton =
                findViewById(R.id.btnSelectExtraAmenities);

        if (amenitiesButton != null) {
            amenitiesButton.setOnClickListener(
                    v -> showAmenitiesPickerDialog()
            );
        }

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
        dropdown.setOnFocusChangeListener((v, focused) -> {
            if (focused) dropdown.showDropDown();
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
                            String amenity = options[which];
                            if (isChecked) {
                                if (!selectedAmenities.contains(amenity)) {
                                    selectedAmenities.add(amenity);
                                }
                            } else {
                                selectedAmenities.remove(amenity);
                            }
                        }
                )
                .setPositiveButton("Done", (dialog, which) -> {
                    amenitiesGroup.removeAllViews();

                    for (String amenity :
                            new ArrayList<>(selectedAmenities)) {

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
                .toString().trim();

        if (addressText.isEmpty()) {
            locationInput.setError(
                    "Enter the property location first."
            );
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
                        if (isFinishing() || isDestroyed()) return;

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
                        if (isFinishing() || isDestroyed()) return;

                        isLocationVerified = false;
                        locationStatus.setText(
                                "Location not found. Check the address and try again."
                        );
                        verifyLocationButton.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Location verification failed", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    isLocationVerified = false;
                    locationStatus.setText(
                            "Could not verify location. Check your connection and try again."
                    );
                    verifyLocationButton.setEnabled(true);
                });
            }
        });
    }

    private void addListing() {
        String propertyName =
                propertyNameInput.getText().toString().trim();
        String location =
                locationInput.getText().toString().trim();
        String priceText =
                priceInput.getText().toString().trim();
        String roomsText =
                roomsInput.getText().toString().trim();
        String description =
                descriptionInput.getText().toString().trim();

        String propertyType =
                propertyTypeInput.getText().toString().trim();
        String waterSource =
                waterSourceInput.getText().toString().trim();
        String electricity =
                electricityInput.getText().toString().trim();
        String sanitation =
                sanitationInput.getText().toString().trim();

        if (propertyName.isEmpty() || location.isEmpty()
                || priceText.isEmpty() || roomsText.isEmpty()
                || description.isEmpty() || propertyType.isEmpty()
                || waterSource.isEmpty() || electricity.isEmpty()
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

        if (price <= 0 || rooms <= 0) {
            Toast.makeText(
                    this,
                    "Price and rooms must be greater than zero.",
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
            HttpURLConnection connection = null;

            try {
                connection = SupabaseHelper.createConnection(
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
                listing.put("property_name", propertyName);
                listing.put("location", location);
                listing.put("price", price);
                listing.put("rooms", rooms);
                listing.put("property_type", propertyType);
                listing.put("description", description);
                listing.put("availability", "Available");

                SupabaseHelper.writeJson(connection, listing);

                int responseCode = connection.getResponseCode();
                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    Log.e(TAG, "Create listing: " + response);
                    showError(
                            "Could not create listing. Check Supabase permissions."
                    );
                    return;
                }

                JSONArray returned = new JSONArray(response);

                if (returned.length() == 0) {
                    showError(
                            "The listing was not returned by Supabase."
                    );
                    return;
                }

                String listingId =
                        returned.getJSONObject(0).getString("id");

                ArrayList<String> uploadedUrls = new ArrayList<>();

                for (int i = 0; i < selectedImageUris.size(); i++) {
                    String imagePath = userId
                            + "/" + listingId
                            + "/property_"
                            + System.currentTimeMillis()
                            + "_" + i + ".jpg";

                    boolean uploaded = uploadImage(
                            accessToken,
                            selectedImageUris.get(i),
                            imagePath
                    );

                    if (!uploaded) {
                        Log.e(
                                TAG,
                                "Failed to upload image " + i
                        );
                        continue;
                    }

                    String publicUrl =
                            SupabaseHelper.getPublicStorageUrl(
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
                            "Listing created, but no photos uploaded successfully."
                    );
                    return;
                }

                updateListingImagePath(
                        accessToken,
                        listingId,
                        uploadedUrls.get(0)
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            this,
                            "Listing published with "
                                    + uploadedUrls.size()
                                    + " photo(s).",
                            Toast.LENGTH_LONG
                    ).show();

                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Add listing failed", e);
                showError(
                        "Error adding listing. Please try again."
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
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
            connection =
                    SupabaseHelper.createStorageConnection(
                            "property-images",
                            imagePath,
                            "POST",
                            accessToken
                    );

            String mimeType =
                    getContentResolver().getType(imageUri);

            if (mimeType == null) {
                mimeType = "image/jpeg";
            }

            connection.setRequestProperty(
                    "Content-Type",
                    mimeType
            );
            connection.setRequestProperty("x-upsert", "true");
            connection.setDoOutput(true);

            try (InputStream input =
                         getContentResolver().openInputStream(imageUri);
                 OutputStream output =
                         connection.getOutputStream()) {

                if (input == null) return false;

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }

                output.flush();
            }

            int code = connection.getResponseCode();
            String response =
                    SupabaseHelper.readResponse(connection);

            Log.d(
                    TAG,
                    "Image upload: " + code + " " + response
            );

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
            String response =
                    SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e(
                        TAG,
                        "Could not save image record: " + response
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not save image record", e);

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
            String response =
                    SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e(
                        TAG,
                        "Image URL update failed: " + response
                );
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

            Toast.makeText(
                    this,
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
