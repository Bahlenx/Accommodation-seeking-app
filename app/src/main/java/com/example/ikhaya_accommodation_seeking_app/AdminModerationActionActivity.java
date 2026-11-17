package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminModerationActionActivity extends AppCompatActivity {

    private String targetUserId;
    private Uri evidenceUri;
    private ExecutorService executor;

    private RadioGroup rgActionType;
    private LinearLayout llSuspensionDuration;
    private EditText etDuration;
    private Spinner spinnerDurationType;
    private EditText etAdminReason;
    private TextView tvAttachmentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_moderation_action);

        targetUserId = getIntent().getStringExtra("USER_ID");
        executor = Executors.newSingleThreadExecutor();

        rgActionType = findViewById(R.id.rgActionType);
        llSuspensionDuration = findViewById(R.id.llSuspensionDuration);
        etDuration = findViewById(R.id.etDuration);
        spinnerDurationType = findViewById(R.id.spinnerDurationType);
        etAdminReason = findViewById(R.id.etAdminReason);
        tvAttachmentName = findViewById(R.id.tvAttachmentName);

        rgActionType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbSuspend) {
                llSuspensionDuration.setVisibility(View.VISIBLE);
            } else {
                llSuspensionDuration.setVisibility(View.GONE);
            }
        });

        ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        evidenceUri = uri;
                        tvAttachmentName.setVisibility(View.VISIBLE);
                        tvAttachmentName.setText("Attachment Selected");
                    }
                }
        );

        findViewById(R.id.btnAttachEvidence).setOnClickListener(v -> {
            filePicker.launch(new String[]{"image/*", "application/pdf"});
        });

        findViewById(R.id.btnSubmitAction).setOnClickListener(v -> submitModerationAction());
    }

    private void submitModerationAction() {
        String reason = etAdminReason.getText().toString().trim();
        if (reason.isEmpty()) {
            Toast.makeText(this, "Reason is required", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isBan = rgActionType.getCheckedRadioButtonId() == R.id.rbBan;
        long suspensionEndTime = 0;
        String actionType = isBan ? "BANNED" : "SUSPENDED";

        if (!isBan) {
            String durStr = etDuration.getText().toString();
            if (durStr.isEmpty()) {
                Toast.makeText(this, "Duration required", Toast.LENGTH_SHORT).show();
                return;
            }
            int duration = Integer.parseInt(durStr);
            String type = spinnerDurationType.getSelectedItem().toString();
            long millis = type.equals("Days") ? duration * 86400000L : duration * 3600000L;
            suspensionEndTime = System.currentTimeMillis() + millis;
        }

        long finalSuspensionEndTime = suspensionEndTime;

        executor.execute(() -> {
            try {
                String token = SupabaseHelper.getAccessToken(this);
                String attachmentUrl = "";

                if (evidenceUri != null) {
                    byte[] data = SupabaseHelper.getBytesFromUri(this, evidenceUri);
                    String fileName = System.currentTimeMillis() + "_evidence";
                    attachmentUrl = SupabaseHelper.uploadStorageBytes("property-images", "moderation/" + fileName, data, token);
                }

                // POST to moderation_cases
                JSONObject postData = new JSONObject();
                postData.put("user_id", targetUserId);
                postData.put("action_type", actionType);
                postData.put("admin_reason", reason);
                postData.put("suspension_end_time", finalSuspensionEndTime);
                if (!attachmentUrl.isEmpty()) {
                    postData.put("admin_file_url", attachmentUrl);
                }

                java.net.HttpURLConnection conn = SupabaseHelper.createConnection("/rest/v1/moderation_cases", "POST", token);
                SupabaseHelper.writeJson(conn, postData);
                int code = conn.getResponseCode();
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    // PATCH user profile
                    String encUid = SupabaseHelper.encode(targetUserId);
                    JSONObject patchData = new JSONObject();
                    patchData.put("is_suspended", true);
                    SupabaseHelper.patch("/rest/v1/profiles?or=(id.eq." + encUid + ",firebase_uid.eq." + encUid + ")", patchData, token);

                    runOnUiThread(() -> {
                        Toast.makeText(this, "User restricted successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to submit", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
}