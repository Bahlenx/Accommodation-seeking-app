
package com.example.ikhaya_accommodation_seeking_app;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddListingActivity extends AppCompatActivity {

    private EditText propertyNameInput;
    private EditText locationInput;
    private EditText priceInput;
    private EditText roomsInput;
    private EditText descriptionInput;
    private Spinner accommodationTypeInput;

    private LinearLayout imagePreviewContainer;
    private TextView txtSelectedImages;
    private Button submitButton;

    private final List<Uri> selectedImageUris = new ArrayList<>();
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
                        displaySelectedImages();

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
        accommodationTypeInput = findViewById(R.id.spinnerAccommodationType);

        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        txtSelectedImages = findViewById(R.id.txtSelectedImages);
        submitButton = findViewById(R.id.btnSubmitListing);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnUploadImage).setOnClickListener(
                v -> imagePicker.launch("image/*")
        );

        submitButton.setOnClickListener(v -> addListing());
    }

    private void displaySelectedImages() {
        imagePreviewContainer.removeAllViews();

        for (Uri uri : selectedImageUris) {
            ImageView preview = new ImageView(this);
            int size = (int) (120 * getResources().getDisplayMetrics().density);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, 12, 0);

            preview.setLayoutParams(params);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setImageURI(uri);
            preview.setContentDescription("Selected property photo");

            imagePreviewContainer.addView(preview);
        }

        txtSelectedImages.setText(
                selectedImageUris.size() == 0
                        ? getString(R.string.no_images_selected)
                        : selectedImageUris.size() + " photo(s) selected"
        );

        txtSelectedImages.setVisibility(View.VISIBLE);
        imagePreviewContainer.setVisibility(
                selectedImageUris.isEmpty() ? View.GONE : View.VISIBLE
        );
    }

    private void addListing() {
        String propertyName = propertyNameInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();
        String roomsText = roomsInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        String propertyType = accommodationTypeInput.getSelectedItem() == null
                ? ""
                : accommodationTypeInput.getSelectedItem().toString().trim();

        if (propertyName.isEmpty() || location.isEmpty()
                || priceText.isEmpty() || roomsText.isEmpty()
                || description.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (propertyType.isEmpty()
                || propertyType.equalsIgnoreCase("Select accommodation type")) {
            Toast.makeText(this, "Please select an accommodation type.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, "Please select at least one property photo.", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        int rooms;

        try {
            price = Double.parseDouble(priceText);
            rooms = Integer.parseInt(roomsText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a valid price and number of rooms.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (price <= 0 || rooms <= 0) {
            Toast.makeText(this, "Price and rooms must be greater than zero.", Toast.LENGTH_SHORT).show();
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

        List<Uri> photosToUpload = new ArrayList<>(selectedImageUris);
        submitButton.setEnabled(false);

        executor.execute(() -> {
            try {
                HttpURLConnection connection = SupabaseHelper.createConnection(
                        "/rest/v1/listings",
                        "POST",
                        accessToken
                );

                connection.setRequestProperty("Prefer", "return=representation");

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

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (code < 200 || code >= 300) {
                    Log.e("iKhayaListing", "Create listing failed: " + response);
                    showError("Listing could not be created. Check the details and try again.");
                    return;
                }

                JSONArray returned = new JSONArray(response);
                if (returned.length() == 0) {
                    showError("The new listing could not be confirmed.");
                    return;
                }

                String listingId = returned.getJSONObject(0).getString("id");
                List<String> uploadedUrls = new ArrayList<>();

                for (int i = 0; i < photosToUpload.size(); i++) {
                    Uri photoUri = photosToUpload.get(i);
                    String mimeType = getContentResolver().getType(photoUri);

                    if (mimeType == null || !mimeType.startsWith("image/")) {
                        mimeType = "image/jpeg";
                    }

                    String extension = extensionForMimeType(mimeType);
                    String imagePath = userId + "/" + listingId
                            + "/image_" + (i + 1) + extension;

                    if (!uploadImage(accessToken, imagePath, photoUri, mimeType)) {
                        Log.e("iKhayaImage", "Upload failed for " + imagePath);
                        showError(
                                "The listing was created, but photo "
                                        + (i + 1) + " failed to upload. Please check Storage permissions."
                        );
                        return;
                    }

                    String publicUrl = SupabaseHelper.getPublicStorageUrl(
                            "property-images",
                            imagePath
                    );

                    if (!saveImageRecord(accessToken, listingId, publicUrl)) {
                        showError(
                                "A photo uploaded, but its database record could not be saved. Check listing_images permissions."
                        );
                        return;
                    }

                    uploadedUrls.add(publicUrl);
                }

                if (!uploadedUrls.isEmpty()) {
                    updateListingImagePath(accessToken, listingId, uploadedUrls.get(0));
                }

                runOnUiThread(() -> {
                    Toast.makeText(
                            AddListingActivity.this,
                            "Listing added with " + uploadedUrls.size() + " photo(s).",
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

    private String extensionForMimeType(String mimeType) {
        if ("image/png".equalsIgnoreCase(mimeType)) return ".png";
        if ("image/webp".equalsIgnoreCase(mimeType)) return ".webp";
        if ("image/gif".equalsIgnoreCase(mimeType)) return ".gif";
        return ".jpg";
    }

    private boolean uploadImage(
            String accessToken,
            String imagePath,
            Uri imageUri,
            String mimeType
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createStorageConnection(
                    "property-images",
                    imagePath,
                    "POST",
                    accessToken
            );

            connection.setRequestProperty("Content-Type", mimeType);
            connection.setRequestProperty("x-upsert", "true");
            connection.setDoOutput(true);

            try (InputStream input = getContentResolver().openInputStream(imageUri)) {
                if (input == null) return false;

                try (OutputStream output = connection.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }

                    output.flush();
                }
            }

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            Log.d("iKhayaImage", "Upload code: " + code + " response: " + response);
            return code >= 200 && code < 300;

        } catch (Exception e) {
            Log.e("iKhayaImage", "Image upload failed", e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean saveImageRecord(
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

            JSONObject body = new JSONObject();
            body.put("listing_id", listingId);
            body.put("image_url", imageUrl);

            SupabaseHelper.writeJson(connection, body);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e("iKhayaImage", "Image record failed: " + response);
                return false;
            }

            return true;
        } catch (Exception e) {
            Log.e("iKhayaImage", "Could not save image record", e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
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
                            + URLEncoder.encode(listingId, "UTF-8"),
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
            Log.e("iKhayaListing", "Could not update listing image URL", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            submitButton.setEnabled(true);
            Toast.makeText(
                    AddListingActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}