package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditListingActivity extends AppCompatActivity {

    private static final String TAG = "EditListing";

    private EditText nameInput;
    private EditText locationInput;
    private EditText priceInput;
    private EditText roomsInput;
    private EditText descriptionInput;

    private AutoCompleteTextView typeInput;
    private AutoCompleteTextView waterInput;
    private AutoCompleteTextView electricityInput;
    private AutoCompleteTextView sanitationInput;

    private AutoCompleteTextView authorityDocTypeInput;
    private TextView authorityDocNameText;
    private Button uploadAuthorityDocButton;
    private Uri selectedDocUriEdit = null;

    private ImageView imagePreview;
    private Button uploadImageButton;
    private Button saveButton;

    private final ArrayList<String> selectedAmenities = new ArrayList<>();
    private ChipGroup cgEditAmenities;
    private Button btnRemovePhoto;

    private ChipGroup cgOfferTypeEdit;
    private Chip chipRentEdit, chipSellEdit;
    private TextView tvEditPriceLabel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String listingId = "";
    private String userId = "";
    private String accessToken = "";
    private Uri selectedImageUri = null;
    private String currentImageUrl = "";

    private String originalAddress = "";
    private String originalDocType = "";
    private String originalDocUrl = "";
    private JSONObject originalListingSnapshot = null;

    private double propertyLatitude = 0.0;
    private double propertyLongitude = 0.0;

    private final ActivityResultLauncher<String> docPickerEdit =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedDocUriEdit = uri;
                    if (authorityDocNameText != null) {
                        authorityDocNameText.setText("Document attached: " + uri.getLastPathSegment());
                        authorityDocNameText.setTextColor(Color.parseColor("#2DCE89"));
                    }
                    if (uploadAuthorityDocButton != null) {
                        uploadAuthorityDocButton.setText("Change Authority Document");
                    }
                }
            });

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    if (imagePreview != null) {
                        imagePreview.setImageURI(uri);
                    }
                    if (uploadImageButton != null) {
                        uploadImageButton.setText(R.string.btn_change_selected_photos);
                    }
                    if (btnRemovePhoto != null) {
                        btnRemovePhoto.setVisibility(View.VISIBLE);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_edit_listing);

        nameInput = findViewById(R.id.edtEditPropertyName);
        locationInput = findViewById(R.id.autoCompleteLocationEdit);

        cgOfferTypeEdit = findViewById(R.id.cgOfferTypeEdit);
        chipRentEdit = findViewById(R.id.chipRentEdit);
        chipSellEdit = findViewById(R.id.chipSellEdit);
        tvEditPriceLabel = findViewById(R.id.tvEditPriceLabel);

        if (cgOfferTypeEdit != null) {
            cgOfferTypeEdit.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.chipSellEdit) {
                    if (tvEditPriceLabel != null) tvEditPriceLabel.setText(R.string.total_purchase_price_r);
                } else {
                    if (tvEditPriceLabel != null) tvEditPriceLabel.setText(R.string.monthly_rental_price_r);
                }
            });
        }

        View btnAdjustPin = findViewById(R.id.btnAdjustPinOnMapEdit);
        if (btnAdjustPin != null) {
            btnAdjustPin.setOnClickListener(v -> {
                AddListingActivity.launchUberStylePinPicker(
                        this,
                        propertyLatitude,
                        propertyLongitude,
                        (lat, lon, resolvedAddress) -> {
                            propertyLatitude = lat;
                            propertyLongitude = lon;
                            locationInput.setText(resolvedAddress);
                        }
                );
            });
        }

        View btnUseGps = findViewById(R.id.btnUseCurrentGpsEdit);
        if (btnUseGps != null) {
            btnUseGps.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    if (lm != null) {
                        try {
                            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (loc != null) {
                                propertyLatitude = loc.getLatitude();
                                propertyLongitude = loc.getLongitude();
                                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                                List<Address> addrs = geocoder.getFromLocation(propertyLatitude, propertyLongitude, 1);
                                if (addrs != null && !addrs.isEmpty()) {
                                    locationInput.setText(addrs.get(0).getAddressLine(0));
                                }
                                Toast.makeText(this, R.string.msg_gps_captured, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, R.string.msg_searching_gps, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {}
                    }
                } else {
                    Toast.makeText(this, R.string.msg_location_permission_required, Toast.LENGTH_SHORT).show();
                }
            });
        }

        priceInput = findViewById(R.id.edtEditPrice);
        roomsInput = findViewById(R.id.edtEditRooms);
        descriptionInput = findViewById(R.id.edtEditDescription);
        imagePreview = findViewById(R.id.imgEditProperty);
        uploadImageButton = findViewById(R.id.btnUploadImageEdit);
        saveButton = findViewById(R.id.btnSaveListingChanges);

        typeInput = findViewById(R.id.edtEditPropertyType);
        waterInput = findViewById(R.id.dropdownEditWaterSource);
        electricityInput = findViewById(R.id.dropdownEditElectricity);
        sanitationInput = findViewById(R.id.dropdownEditSanitation);

        authorityDocTypeInput = findViewById(R.id.dropdownEditAuthorityDocType);
        authorityDocNameText = findViewById(R.id.tvEditSelectedDocName);
        uploadAuthorityDocButton = findViewById(R.id.btnUploadAuthorityDocEdit);

        cgEditAmenities = findViewById(R.id.cgSelectedAmenitiesEdit);
        View btnExtraAmenities = findViewById(R.id.btnSelectExtraAmenitiesEdit);
        btnRemovePhoto = findViewById(R.id.btnRemovePhotoEdit);

        if (btnExtraAmenities != null) {
            btnExtraAmenities.setOnClickListener(v -> showAmenitiesPickerDialog());
        }

        if (btnRemovePhoto != null) {
            btnRemovePhoto.setOnClickListener(v -> {
                selectedImageUri = null;
                currentImageUrl = "";
                if (imagePreview != null) {
                    imagePreview.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                btnRemovePhoto.setVisibility(View.GONE);
                if (uploadImageButton != null) {
                    uploadImageButton.setText(R.string.choose_property_photos);
                }
                Toast.makeText(this, "Photo removed. You can choose a new one or keep it blank.", Toast.LENGTH_SHORT).show();
            });
        }

        setupDropdown(typeInput, R.array.sa_property_types);
        setupDropdown(waterInput, R.array.sa_water_sources);
        setupDropdown(electricityInput, R.array.sa_electricity_types);
        setupDropdown(sanitationInput, R.array.sa_sanitation_types);
        setupDropdown(authorityDocTypeInput, R.array.sa_authority_doc_types);

        if (uploadAuthorityDocButton != null) {
            uploadAuthorityDocButton.setOnClickListener(v -> docPickerEdit.launch("*/*"));
        }

        View backButton = findViewById(R.id.btnBackEditListing);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        listingId = getIntent().getStringExtra("LISTING_ID");
        if (listingId == null || listingId.trim().isEmpty()) {
            listingId = getIntent().getStringExtra("listingId");
        }

        userId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        if (listingId == null || listingId.trim().isEmpty() || userId == null || accessToken == null) {
            Toast.makeText(this, R.string.error_missing_credentials_or_listing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (uploadImageButton != null) {
            uploadImageButton.setOnClickListener(v -> imagePicker.launch("image/*"));
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveChanges());
        }

        loadListing();
    }

    private void setupDropdown(AutoCompleteTextView dropdown, int arrayResource) {
        if (dropdown == null) return;
        String[] items = getResources().getStringArray(arrayResource);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, items);
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
                .setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        if (!selectedAmenities.contains(options[which])) selectedAmenities.add(options[which]);
                    } else {
                        selectedAmenities.remove(options[which]);
                    }
                })
                .setPositiveButton(R.string.btn_done, (dialog, which) -> renderAmenityChips())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void renderAmenityChips() {
        if (cgEditAmenities == null) return;
        cgEditAmenities.removeAllViews();

        for (String amenity : selectedAmenities) {
            Chip chip = new Chip(this);
            chip.setText(amenity);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                selectedAmenities.remove(amenity);
                cgEditAmenities.removeView(chip);
            });
            cgEditAmenities.addView(chip);
        }
    }

    private void loadListing() {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings?select=*&id=eq." + encode(listingId) + "&landlord_uid=eq." + encode(userId) + "&limit=1";
                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    JSONArray data = new JSONArray(response);
                    if (data.length() > 0) {
                        JSONObject listing = data.getJSONObject(0);

                        JSONObject existingOriginal = listing.optJSONObject("original_payload");
                        if (existingOriginal != null && existingOriginal.length() > 0) {
                            originalListingSnapshot = existingOriginal;
                        } else {
                            originalListingSnapshot = new JSONObject(listing.toString());
                        }

                        currentImageUrl = listing.optString("image_url", "");

                        JSONArray existingAmenities = listing.optJSONArray("amenities");
                        if (existingAmenities != null) {
                            selectedAmenities.clear();
                            for (int i = 0; i < existingAmenities.length(); i++) {
                                selectedAmenities.add(existingAmenities.optString(i));
                            }
                        }

                        propertyLatitude = listing.optDouble("latitude", 0.0);
                        propertyLongitude = listing.optDouble("longitude", 0.0);

                        originalAddress = listing.optString("location", "");
                        originalDocType = listing.optString("authority_doc_type", "MUNICIPAL_RATES");
                        originalDocUrl = listing.optString("authority_doc_url", "");

                        String offerType = listing.optString("offer_type", "rent");
                        boolean isSale = "sale".equalsIgnoreCase(offerType);

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            if (chipSellEdit != null && chipRentEdit != null) {
                                if (isSale) {
                                    chipSellEdit.setChecked(true);
                                    if (tvEditPriceLabel != null) tvEditPriceLabel.setText(R.string.total_purchase_price_r);
                                } else {
                                    chipRentEdit.setChecked(true);
                                    if (tvEditPriceLabel != null) tvEditPriceLabel.setText(R.string.monthly_rental_price_r);
                                }
                            }

                            if (nameInput != null) nameInput.setText(listing.optString("property_name", ""));
                            if (locationInput != null) locationInput.setText(listing.optString("location", ""));
                            if (priceInput != null) priceInput.setText(listing.optString("price", ""));
                            if (roomsInput != null) roomsInput.setText(listing.optString("rooms", ""));
                            if (typeInput != null) typeInput.setText(listing.optString("property_type", ""), false);
                            if (waterInput != null) waterInput.setText(listing.optString("water_source", ""), false);
                            if (electricityInput != null) electricityInput.setText(listing.optString("electricity", ""), false);
                            if (sanitationInput != null) sanitationInput.setText(listing.optString("sanitation", ""), false);
                            if (descriptionInput != null) descriptionInput.setText(listing.optString("description", ""));

                            if (authorityDocTypeInput != null) {
                                String label = AddListingActivity.getDocTypeLabel(originalDocType);
                                authorityDocTypeInput.setText(label, false);
                            }
                            if (authorityDocNameText != null) {
                                if (!originalDocUrl.isEmpty()) {
                                    authorityDocNameText.setText("Current document attached");
                                    authorityDocNameText.setTextColor(Color.parseColor("#2DCE89"));
                                } else {
                                    authorityDocNameText.setText("No authority document attached");
                                    authorityDocNameText.setTextColor(Color.parseColor("#8C7A70"));
                                }
                            }

                            renderAmenityChips();
                            if (btnRemovePhoto != null) {
                                btnRemovePhoto.setVisibility(!currentImageUrl.isEmpty() ? View.VISIBLE : View.GONE);
                            }
                        });

                        if (!currentImageUrl.isEmpty()) {
                            loadExistingImage(currentImageUrl);
                        }
                    } else {
                        showMessage(getString(R.string.error_listing_not_found_permission));
                    }
                } else {
                    showMessage(getString(R.string.error_could_not_load_listing) + " " + response);
                }
            } catch (Exception e) {
                showMessage(getString(R.string.error_could_not_load_listing_general));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void loadExistingImage(String urlString) {
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.connect();
                InputStream in = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (imagePreview != null) {
                            imagePreview.setImageBitmap(bitmap);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load existing image", e);
            }
        });
    }

    public interface UploadCallback {
        void onSuccess(String uploadedPublicUrl);
        void onError(String error);
    }

    private void saveChanges() {
        String name = nameInput != null ? nameInput.getText().toString().trim() : "";
        String location = locationInput != null ? locationInput.getText().toString().trim() : "";
        String price = priceInput != null ? priceInput.getText().toString().trim() : "";
        String rooms = roomsInput != null ? roomsInput.getText().toString().trim() : "";
        String type = typeInput != null ? typeInput.getText().toString().trim() : "";
        String description = descriptionInput != null ? descriptionInput.getText().toString().trim() : "";

        if (name.isEmpty() || location.isEmpty() || price.isEmpty() || rooms.isEmpty() || type.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, R.string.error_complete_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        double parsedPrice;
        int parsedRooms;

        try {
            parsedPrice = Double.parseDouble(price);
            parsedRooms = Integer.parseInt(rooms);
            if (parsedPrice < 0 || parsedRooms < 0) {
                Toast.makeText(this, R.string.error_price_rooms_negative, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_price_or_rooms, Toast.LENGTH_SHORT).show();
            return;
        }

        if (saveButton != null) {
            saveButton.setEnabled(false);
            saveButton.setText(R.string.btn_saving_changes_in_progress);
        }

        if (selectedDocUriEdit != null) {
            uploadAuthorityDocument(selectedDocUriEdit, new UploadCallback() {
                @Override
                public void onSuccess(String uploadedPublicUrl) {
                    sendListingUpdatePayload(uploadedPublicUrl);
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (saveButton != null) {
                            saveButton.setEnabled(true);
                            saveButton.setText(R.string.save_changes);
                        }
                        Toast.makeText(EditListingActivity.this, "Document upload failed: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            sendListingUpdatePayload(originalDocUrl);
        }
    }

    private void uploadAuthorityDocument(Uri docUri, UploadCallback callback) {
        executor.execute(() -> {
            try {
                String docPath = userId + "/" + listingId + "/authority_doc_edit_" + System.currentTimeMillis() + ".jpg";
                boolean uploadedDoc = uploadImageToStorage(docUri, docPath);
                if (uploadedDoc) {
                    String docPublicUrl = SupabaseHelper.getPublicStorageUrl("property-images", docPath);
                    callback.onSuccess(docPublicUrl);
                } else {
                    callback.onError("Storage upload returned failure status.");
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    private void sendListingUpdatePayload(String finalDocUrl) {
        executor.execute(() -> {
            String name = nameInput != null ? nameInput.getText().toString().trim() : "";
            String location = locationInput != null ? locationInput.getText().toString().trim() : "";
            String price = priceInput != null ? priceInput.getText().toString().trim() : "";
            String rooms = roomsInput != null ? roomsInput.getText().toString().trim() : "";
            String type = typeInput != null ? typeInput.getText().toString().trim() : "";
            String description = descriptionInput != null ? descriptionInput.getText().toString().trim() : "";

            double parsedPrice = Double.parseDouble(price);
            int parsedRooms = Integer.parseInt(rooms);

            String finalImageUrl = currentImageUrl;
            if (selectedImageUri != null) {
                String imagePath = userId + "/" + listingId + "/property_edit_" + System.currentTimeMillis() + ".jpg";
                boolean uploaded = uploadImageToStorage(selectedImageUri, imagePath);
                if (uploaded) {
                    finalImageUrl = SupabaseHelper.getPublicStorageUrl("property-images", imagePath);
                }
            }

            String selectedDocLabel = authorityDocTypeInput != null ? authorityDocTypeInput.getText().toString().trim() : "";
            String newDocType = AddListingActivity.getDocTypeKey(selectedDocLabel);

            boolean isAddressChanged = !originalAddress.trim().equalsIgnoreCase(location.trim());
            boolean isDocTypeChanged = !originalDocType.trim().equalsIgnoreCase(newDocType.trim());
            boolean isDocUrlChanged = (finalDocUrl != null && !finalDocUrl.isEmpty() && !finalDocUrl.equals(originalDocUrl));

            boolean isHighRiskEdit = isAddressChanged || isDocTypeChanged || isDocUrlChanged;
            String targetStatus = isHighRiskEdit ? "SUSPENDED_FOR_REVERIFICATION" : "CHANGES_PENDING_REVIEW";

            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings?id=eq." + encode(listingId) + "&landlord_uid=eq." + encode(userId);
                connection = SupabaseHelper.createConnection(endpoint, "PATCH", accessToken);
                connection.setRequestProperty("Prefer", "return=representation");

                JSONObject body = new JSONObject();
                body.put("property_name", name);
                body.put("location", location);
                boolean isForSale = chipSellEdit != null && chipSellEdit.isChecked();
                body.put("offer_type", isForSale ? "sale" : "rent");
                body.put("price", parsedPrice);
                body.put("rooms", parsedRooms);
                body.put("property_type", type);
                body.put("description", description);
                body.put("authority_doc_type", newDocType);

                if (originalListingSnapshot != null) {
                    body.put("original_payload", originalListingSnapshot);
                }

                if (finalDocUrl != null && !finalDocUrl.isEmpty()) {
                    body.put("authority_doc_url", finalDocUrl);
                }

                String water = waterInput != null ? waterInput.getText().toString().trim() : "";
                String electricity = electricityInput != null ? electricityInput.getText().toString().trim() : "";
                String sanitation = sanitationInput != null ? sanitationInput.getText().toString().trim() : "";

                if (!water.isEmpty()) body.put("water_source", water);
                if (!electricity.isEmpty()) body.put("electricity", electricity);
                if (!sanitation.isEmpty()) body.put("sanitation", sanitation);

                body.put("status", targetStatus);
                body.put("availability", "Pending");

                if (isHighRiskEdit) {
                    body.put("reverification_reason", "High-risk fields updated (address or authority proof).");
                }

                JSONArray amenitiesJson = new JSONArray(selectedAmenities);
                body.put("amenities", amenitiesJson);
                body.put("image_url", finalImageUrl);

                if (propertyLatitude != 0.0 && propertyLongitude != 0.0) {
                    body.put("latitude", propertyLatitude);
                    body.put("longitude", propertyLongitude);
                }

                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (saveButton != null) {
                        saveButton.setEnabled(true);
                        saveButton.setText(R.string.save_changes);
                    }

                    if (code >= 200 && code < 300) {
                        String toastMsg = isHighRiskEdit
                                ? "Listing suspended pending admin re-verification."
                                : "Listing changes submitted for review.";
                        Toast.makeText(EditListingActivity.this, toastMsg, Toast.LENGTH_LONG).show();

                        SupabaseHelper.createNotification(
                                userId,
                                isHighRiskEdit ? "Listing Suspended for Re-verification" : "Listing Changes Pending Review",
                                "Your listing '" + name + "' changes have been submitted.",
                                accessToken
                        );

                        SupabaseHelper.notifyAdmins(
                                isHighRiskEdit ? "High-Risk Listing Edit" : "Listing Edit Submitted",
                                "Landlord updated '" + name + "'. Status: " + targetStatus,
                                accessToken
                        );

                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(EditListingActivity.this, "Update failed (HTTP " + code + ")", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (saveButton != null) {
                        saveButton.setEnabled(true);
                        saveButton.setText(R.string.save_changes);
                    }
                    Toast.makeText(EditListingActivity.this, R.string.error_network_updating_listing, Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private boolean uploadImageToStorage(Uri imageUri, String imagePath) {
        HttpURLConnection connection = null;
        try {
            connection = SupabaseHelper.createStorageConnection("property-images", imagePath, "POST", accessToken);
            String mimeType = getContentResolver().getType(imageUri);
            connection.setRequestProperty("Content-Type", mimeType != null ? mimeType : "image/jpeg");
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
            return code >= 200 && code < 300;
        } catch (Exception e) {
            Log.e(TAG, "Image upload failed", e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(EditListingActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}