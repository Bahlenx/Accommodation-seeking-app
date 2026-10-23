package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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

    private ImageView imagePreview;
    private Button uploadImageButton;
    private Button saveButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String listingId = "";
    private String userId = "";
    private String accessToken = "";
    private Uri selectedImageUri = null;
    private String currentImageUrl = "";

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

        setupDropdown(typeInput, R.array.sa_property_types);
        setupDropdown(waterInput, R.array.sa_water_sources);
        setupDropdown(electricityInput, R.array.sa_electricity_types);
        setupDropdown(sanitationInput, R.array.sa_sanitation_types);

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
                        currentImageUrl = listing.optString("image_url", "");

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            if (nameInput != null) nameInput.setText(listing.optString("property_name", ""));
                            if (locationInput != null) locationInput.setText(listing.optString("location", ""));
                            if (priceInput != null) priceInput.setText(listing.optString("price", ""));
                            if (roomsInput != null) roomsInput.setText(listing.optString("rooms", ""));
                            if (typeInput != null) typeInput.setText(listing.optString("property_type", ""), false);
                            if (waterInput != null) waterInput.setText(listing.optString("water_source", ""), false);
                            if (electricityInput != null) electricityInput.setText(listing.optString("electricity", ""), false);
                            if (sanitationInput != null) sanitationInput.setText(listing.optString("sanitation", ""), false);
                            if (descriptionInput != null) descriptionInput.setText(listing.optString("description", ""));
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
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (imagePreview != null) {
                                imagePreview.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load existing image", e);
            }
        });
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

        executor.execute(() -> {
            String finalImageUrl = currentImageUrl;

            if (selectedImageUri != null) {
                String imagePath = userId + "/" + listingId + "/property_edit_" + System.currentTimeMillis() + ".jpg";
                boolean uploaded = uploadImageToStorage(selectedImageUri, imagePath);

                if (uploaded) {
                    finalImageUrl = SupabaseHelper.getPublicStorageUrl("property-images", imagePath);
                } else {
                    showMessage(getString(R.string.warn_image_upload_failed_saving_text));
                }
            }

            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings?id=eq." + encode(listingId) + "&landlord_uid=eq." + encode(userId);
                connection = SupabaseHelper.createConnection(endpoint, "PATCH", accessToken);
                connection.setRequestProperty("Prefer", "return=representation");

                JSONObject body = new JSONObject();
                body.put("property_name", name);
                body.put("location", location);
                body.put("price", parsedPrice);
                body.put("rooms", parsedRooms);
                body.put("property_type", type);
                body.put("description", description);

                String water = waterInput != null ? waterInput.getText().toString().trim() : "";
                String electricity = electricityInput != null ? electricityInput.getText().toString().trim() : "";
                String sanitation = sanitationInput != null ? sanitationInput.getText().toString().trim() : "";

                if (!water.isEmpty()) body.put("water_source", water);
                if (!electricity.isEmpty()) body.put("electricity", electricity);
                if (!sanitation.isEmpty()) body.put("sanitation", sanitation);

                body.put("status", "PENDING");
                body.put("availability", "Pending");

                if (finalImageUrl != null && !finalImageUrl.isEmpty()) {
                    body.put("image_url", finalImageUrl);
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
                        Toast.makeText(EditListingActivity.this, R.string.msg_listing_updated_success, Toast.LENGTH_SHORT).show();

                        SupabaseHelper.createNotification(
                                userId,
                                "Listing Under Review",
                                "Your listing '" + name + "' has been submitted and is pending admin approval.",
                                accessToken
                        );

                        SupabaseHelper.notifyAdmins(
                                "New Listing Edit",
                                "A landlord has updated '" + name + "'. It requires review.",
                                accessToken
                        );

                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(EditListingActivity.this, getString(R.string.error_update_failed_http) + " " + code, Toast.LENGTH_LONG).show();
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

    @SuppressWarnings("CharsetObjectCanBeUsed")
    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20");
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