package com.example.ikhaya_accommodation_seeking_app;


import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddListingActivity extends AppCompatActivity {

    private AutoCompleteTextView dropdownPropertyType, dropdownWaterSource, dropdownElectricity, dropdownSanitation;
    private ChipGroup cgSelectedAmenities;
    private EditText edtPropertyName, edtLocation, edtPrice, edtRooms, edtDescription;
    private TextView tvLocationStatus;
    private Button btnVerifyLocation, btnSelectExtraAmenities, btnUploadImage, btnSubmitListing;
    private ImageView imgProperty;
    private Uri selectedImageUri;

    private double verifiedLatitude = 0.0;
    private double verifiedLongitude = 0.0;
    private boolean isLocationVerified = false;

    private final String[] extraAmenitiesList = {
            "Secure Yard Parking", "Fenced & Gated Yard", "Free Wi-Fi Included",
            "Pet Friendly", "Deposit Required", "Available Immediately",
            "Near Taxi Rank / Bus Stop", "DSTV / OpenView Port", "Burglar Bars / Security Gate"
    };
    private final boolean[] selectedAmenitiesState = new boolean[extraAmenitiesList.length];
    private final List<String> activeAmenities = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_listing);

        // 1. Bind All Views First
        edtPropertyName = findViewById(R.id.edtPropertyName);
        edtLocation = findViewById(R.id.edtLocation);
        edtPrice = findViewById(R.id.edtPrice);
        edtRooms = findViewById(R.id.edtRooms);
        edtDescription = findViewById(R.id.edtDescription);
        imgProperty = findViewById(R.id.imgProperty);

        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        btnVerifyLocation = findViewById(R.id.btnVerifyLocation);

        dropdownPropertyType = findViewById(R.id.dropdownPropertyType);
        dropdownWaterSource = findViewById(R.id.dropdownWaterSource);
        dropdownElectricity = findViewById(R.id.dropdownElectricity);
        dropdownSanitation = findViewById(R.id.dropdownSanitation);
        cgSelectedAmenities = findViewById(R.id.cgSelectedAmenities);

        btnSelectExtraAmenities = findViewById(R.id.btnSelectExtraAmenities);
        btnUploadImage = findViewById(R.id.btnUploadImage);
        btnSubmitListing = findViewById(R.id.btnSubmitListing);

        // 2. Setup Dropdown Adapters
        setupDropdown(dropdownPropertyType, R.array.sa_property_types);
        setupDropdown(dropdownWaterSource, R.array.sa_water_sources);
        setupDropdown(dropdownElectricity, R.array.sa_electricity_types);
        setupDropdown(dropdownSanitation, R.array.sa_sanitation_types);

        // 3. Location Verification Action
        btnVerifyLocation.setOnClickListener(v -> {
            String locationInput = edtLocation.getText().toString().trim();
            if (locationInput.isEmpty()) {
                edtLocation.setError("Enter an address or area first");
                return;
            }
            verifyAddress(locationInput);
        });

        // 4. Amenities Multi-Select Action
        btnSelectExtraAmenities.setOnClickListener(v -> showAmenitiesPickerDialog());

        // 5. Image Picker Action
        ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgProperty.setImageURI(uri);
                    }
                }
        );
        btnUploadImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // 6. Submit Action
        btnSubmitListing.setOnClickListener(v -> {
            String propertyName = edtPropertyName.getText().toString().trim();
            String location = edtLocation.getText().toString().trim();
            String price = edtPrice.getText().toString().trim();
            String rooms = edtRooms.getText().toString().trim();
            String description = edtDescription.getText().toString().trim();

            String propType = dropdownPropertyType.getText().toString();
            String water = dropdownWaterSource.getText().toString();
            String power = dropdownElectricity.getText().toString();

            if (propertyName.isEmpty() || location.isEmpty() || price.isEmpty() || rooms.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Please fill in all text fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (propType.isEmpty() || water.isEmpty() || power.isEmpty()) {
                Toast.makeText(this, "Please select property, water, and power specifications", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isLocationVerified) {
                Toast.makeText(this, "Please verify your property location first so tenants can find it on a map", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedImageUri == null) {
                Toast.makeText(this, "Please select a property image", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Listing created with " + activeAmenities.size() + " custom amenities!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupDropdown(AutoCompleteTextView dropdown, int arrayResId) {
        String[] items = getResources().getStringArray(arrayResId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, items);
        dropdown.setAdapter(adapter);
    }

    private void showAmenitiesPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Features & Rules");
        builder.setMultiChoiceItems(extraAmenitiesList, selectedAmenitiesState, (dialog, which, isChecked) -> {
            selectedAmenitiesState[which] = isChecked;
        });

        builder.setPositiveButton("Done", (dialog, which) -> {
            cgSelectedAmenities.removeAllViews();
            activeAmenities.clear();

            for (int i = 0; i < extraAmenitiesList.length; i++) {
                if (selectedAmenitiesState[i]) {
                    String amenity = extraAmenitiesList[i];
                    activeAmenities.add(amenity);

                    Chip chip = new Chip(this);
                    chip.setText(amenity);
                    chip.setCloseIconVisible(true);
                    final int index = i;
                    chip.setOnCloseIconClickListener(v -> {
                        selectedAmenitiesState[index] = false;
                        activeAmenities.remove(amenity);
                        cgSelectedAmenities.removeView(chip);
                    });
                    cgSelectedAmenities.addView(chip);
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void verifyAddress(String addressQuery) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        tvLocationStatus.setText("Verifying address with map registry...");
        tvLocationStatus.setTextColor(android.graphics.Color.parseColor("#FFA500"));

        new Thread(() -> {
            try {
                String query = addressQuery.toLowerCase().contains("south africa")
                        ? addressQuery
                        : addressQuery + ", South Africa";

                List<Address> addresses = geocoder.getFromLocationName(query, 5);

                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address matched = addresses.get(0);
                        verifiedLatitude = matched.getLatitude();
                        verifiedLongitude = matched.getLongitude();
                        isLocationVerified = true;

                        String fullMatchedAddress = matched.getAddressLine(0);
                        tvLocationStatus.setText("Verified: " + fullMatchedAddress);
                        tvLocationStatus.setTextColor(android.graphics.Color.parseColor("#28A745"));
                        Toast.makeText(this, "Location verified successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        isLocationVerified = false;
                        tvLocationStatus.setText("Could not find address. Try adding the town or municipality.");
                        tvLocationStatus.setTextColor(android.graphics.Color.parseColor("#DC3545"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvLocationStatus.setText("Verification error: " + e.getMessage());
                    tvLocationStatus.setTextColor(android.graphics.Color.parseColor("#DC3545"));
                });
            }
        }).start();
    }
}