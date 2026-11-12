package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private TextInputEditText etSaIdNumber;
    private TextView tvIdentityStatus;
    private TextView tvIdDocStatus;
    private TextView tvSelfieStatus;
    private Button btnUploadIdDoc;
    private Button btnUploadSelfie;
    private Button btnSubmitIdentity;

    private String currentUserId;
    private String accessToken;

    private String uploadedIdDocUrl = null;
    private String uploadedSelfieUrl = null;

    private Uri selectedIdDocUri = null;
    private Uri selectedSelfieUri = null;
    private Uri tempCameraUri = null;
    private boolean isCapturingSelfieForCamera = false;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Uri> takePhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && tempCameraUri != null) {
                    if (isCapturingSelfieForCamera) {
                        selectedSelfieUri = tempCameraUri;
                        if (tvSelfieStatus != null) tvSelfieStatus.setText("Selected: Camera Selfie");
                        uploadFileToSupabase(tempCameraUri, "identity/selfie_" + currentUserId + "_" + System.currentTimeMillis() + ".jpg", false);
                    } else {
                        selectedIdDocUri = tempCameraUri;
                        if (tvIdDocStatus != null) tvIdDocStatus.setText("Selected: Camera ID Document");
                        uploadFileToSupabase(tempCameraUri, "identity/doc_" + currentUserId + "_" + System.currentTimeMillis() + ".jpg", true);
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> pickGalleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    if (isCapturingSelfieForCamera) {
                        selectedSelfieUri = uri;
                        if (tvSelfieStatus != null) tvSelfieStatus.setText("Selected: Gallery Selfie");
                        uploadFileToSupabase(uri, "identity/selfie_" + currentUserId + "_" + System.currentTimeMillis() + ".jpg", false);
                    } else {
                        selectedIdDocUri = uri;
                        if (tvIdDocStatus != null) tvIdDocStatus.setText("Selected: Gallery ID Document");
                        uploadFileToSupabase(uri, "identity/doc_" + currentUserId + "_" + System.currentTimeMillis() + ".jpg", true);
                    }
                }
            }
    );

    private void showImageSourceDialog(boolean isSelfie) {
        isCapturingSelfieForCamera = isSelfie;
        String[] options = {"Take Photo with Camera", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle(isSelfie ? "Take Verification Selfie" : "Upload ID Document")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        try {
                            File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                    (isSelfie ? "selfie_" : "id_") + System.currentTimeMillis() + ".jpg");
                            tempCameraUri = FileProvider.getUriForFile(this,
                                    getApplicationContext().getPackageName() + ".provider", photoFile);
                            takePhotoLauncher.launch(tempCameraUri);
                        } catch (Exception e) {
                            Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        pickGalleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        currentUserId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        View btnBack = findViewById(R.id.btnBackEditProfile);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        etSaIdNumber = findViewById(R.id.etSaIdNumber);
        tvIdentityStatus = findViewById(R.id.tvIdentityStatus);
        tvIdDocStatus = findViewById(R.id.tvIdDocStatus);
        tvSelfieStatus = findViewById(R.id.tvSelfieStatus);
        btnUploadIdDoc = findViewById(R.id.btnUploadIdDoc);
        btnUploadSelfie = findViewById(R.id.btnUploadSelfie);
        btnSubmitIdentity = findViewById(R.id.btnSubmitIdentity);

        btnUploadIdDoc.setOnClickListener(v -> showImageSourceDialog(false));
        btnUploadSelfie.setOnClickListener(v -> showImageSourceDialog(true));
        btnSubmitIdentity.setOnClickListener(v -> submitIdentityVerification());

        loadCurrentProfile();
    }

    private void loadCurrentProfile() {
        if (currentUserId == null || currentUserId.isEmpty()) return;

        SupabaseHelper.fetchProfileById(currentUserId, (profileObj, error) -> {
            if (profileObj != null) {
                runOnUiThread(() -> {
                    if (profileObj.has("sa_id_number")) {
                        String existingSaId = profileObj.optString("sa_id_number", "");
                        if (!existingSaId.isEmpty()) {
                            etSaIdNumber.setText(existingSaId);
                            // Lock the field so the user cannot enter a different ID number
                            etSaIdNumber.setEnabled(false);
                            etSaIdNumber.setFocusable(false);
                        }
                    }
                    String status = profileObj.optString("identity_status", "UNVERIFIED");
                    uploadedIdDocUrl = profileObj.optString("id_document_url", null);
                    uploadedSelfieUrl = profileObj.optString("selfie_url", null);

                    tvIdentityStatus.setText("Status: " + status);
                    updateStatusColor(status);

                    if (uploadedIdDocUrl != null && !uploadedIdDocUrl.isEmpty()) {
                        tvIdDocStatus.setText("ID Document uploaded");
                    }
                    if (uploadedSelfieUrl != null && !uploadedSelfieUrl.isEmpty()) {
                        tvSelfieStatus.setText("Selfie uploaded");
                    }
                });
            }
        });
    }

    private void updateStatusColor(String status) {
        if ("VERIFIED".equalsIgnoreCase(status)) {
            tvIdentityStatus.setTextColor(Color.parseColor("#28A745"));
        } else if ("PENDING_VERIFICATION".equalsIgnoreCase(status)) {
            tvIdentityStatus.setTextColor(Color.parseColor("#FFC107"));
        } else {
            tvIdentityStatus.setTextColor(Color.parseColor("#DC3545"));
        }
    }

    private void uploadFileToSupabase(Uri uri, String storagePath, boolean isIdDoc) {
        Toast.makeText(this, "Uploading file...", Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = SupabaseHelper.createStorageConnection(
                        "property-images",
                        storagePath,
                        "POST",
                        accessToken
                );

                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "image/jpeg";

                connection.setRequestProperty("Content-Type", mimeType);
                connection.setRequestProperty("x-upsert", "true");
                connection.setDoOutput(true);

                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = connection.getOutputStream()) {
                    if (in == null) return;
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.flush();
                }

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    String publicUrl = SupabaseHelper.getPublicStorageUrl("property-images", storagePath);
                    runOnUiThread(() -> {
                        if (isIdDoc) {
                            uploadedIdDocUrl = publicUrl;
                            tvIdDocStatus.setText("ID Document uploaded successfully");
                        } else {
                            uploadedSelfieUrl = publicUrl;
                            tvSelfieStatus.setText("Selfie uploaded successfully");
                        }
                        Toast.makeText(this, "Upload successful!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    String err = SupabaseHelper.readResponse(connection);
                    runOnUiThread(() -> Toast.makeText(this, "Upload failed: " + err, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Upload error", e);
                runOnUiThread(() -> Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void submitIdentityVerification() {
        String saId = etSaIdNumber.getText().toString().trim();

        if (!IdValidationUtils.isValidSAID(saId)) {
            Toast.makeText(this, "Please enter a valid 13-digit South African ID number.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uploadedIdDocUrl == null || uploadedSelfieUrl == null) {
            Toast.makeText(this, "Please upload both your ID document and verification selfie.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject profilePayload = new JSONObject();
        try {
            profilePayload.put("sa_id_number", saId);
            profilePayload.put("id_document_url", uploadedIdDocUrl);
            profilePayload.put("selfie_url", uploadedSelfieUrl);
            profilePayload.put("verification_selfie_url", uploadedSelfieUrl);
            profilePayload.put("identity_status", "PENDING_VERIFICATION");
            profilePayload.put("verification_status", "PENDING");
            profilePayload.put("is_identity_verified", false);
            profilePayload.put("is_verified", false);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SupabaseHelper.updateProfile(currentUserId, profilePayload, (success, error) -> {
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "Identity submitted for admin verification!", Toast.LENGTH_SHORT).show();
                    tvIdentityStatus.setText("Status: PENDING_VERIFICATION");
                    updateStatusColor("PENDING_VERIFICATION");

                    // Notify admins about landlord identity submission
                    String token = SupabaseHelper.getAccessToken(this);
                    SupabaseHelper.notifyAdmins("Landlord Identity Submitted", "Landlord submitted identity documents for verification.", token);
                } else {
                    Toast.makeText(this, "Submission failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}
