package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountRestrictedAppealActivity extends AppCompatActivity {

    private String appealToken;
    private String userId;
    private String caseId;
    private Uri userEvidenceUri;
    private ExecutorService executor;

    private TextView tvRestrictionType, tvAdminReason, tvUserAttachmentName;
    private Button btnViewAdminAttachment, btnAttachUserEvidence, btnSubmitAppeal, btnLogout;
    private EditText etUserAppeal;
    private String adminAttachmentUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_restricted_appeal);

        appealToken = getIntent().getStringExtra("APPEAL_TOKEN");
        userId = getIntent().getStringExtra("USER_ID");
        executor = Executors.newSingleThreadExecutor();

        tvRestrictionType = findViewById(R.id.tvRestrictionType);
        tvAdminReason = findViewById(R.id.tvAdminReason);
        tvUserAttachmentName = findViewById(R.id.tvUserAttachmentName);
        btnViewAdminAttachment = findViewById(R.id.btnViewAdminAttachment);
        btnAttachUserEvidence = findViewById(R.id.btnAttachUserEvidence);
        btnSubmitAppeal = findViewById(R.id.btnSubmitAppeal);
        btnLogout = findViewById(R.id.btnLogout);
        etUserAppeal = findViewById(R.id.etUserAppeal);

        loadCaseDetails();

        btnViewAdminAttachment.setOnClickListener(v -> {
            if (adminAttachmentUrl != null && !adminAttachmentUrl.isEmpty()) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(adminAttachmentUrl));
                startActivity(browserIntent);
            }
        });

        ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        userEvidenceUri = uri;
                        tvUserAttachmentName.setVisibility(View.VISIBLE);
                        tvUserAttachmentName.setText("Evidence Selected");
                    }
                }
        );

        btnAttachUserEvidence.setOnClickListener(v -> {
            filePicker.launch(new String[]{"image/*", "application/pdf"});
        });

        btnSubmitAppeal.setOnClickListener(v -> submitAppeal());

        btnLogout.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadCaseDetails() {
        executor.execute(() -> {
            try {
                java.net.HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/moderation_cases?select=*&user_id=eq." + userId + "&order=created_at.desc&limit=1",
                        "GET", appealToken);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (arr.length() > 0) {
                        JSONObject caseObj = arr.getJSONObject(0);
                        caseId = caseObj.optString("id");
                        String actionType = caseObj.optString("action_type");
                        long endTime = caseObj.optLong("suspension_end_time", 0);
                        String reason = caseObj.optString("admin_reason", "No reason provided.");
                        adminAttachmentUrl = caseObj.optString("admin_attachment_url", null);

                        runOnUiThread(() -> {
                            if ("BANNED".equals(actionType) || endTime == 0) {
                                tvRestrictionType.setText("Permanent Ban");
                            } else {
                                long remaining = endTime - System.currentTimeMillis();
                                long days = remaining / (1000 * 60 * 60 * 24);
                                long hours = (remaining / (1000 * 60 * 60)) % 24;
                                tvRestrictionType.setText("Suspended (" + days + "d " + hours + "h remaining)");
                            }
                            tvAdminReason.setText("Reason: " + reason);
                            if (adminAttachmentUrl != null && !adminAttachmentUrl.isEmpty()) {
                                btnViewAdminAttachment.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void submitAppeal() {
        String appealText = etUserAppeal.getText().toString().trim();
        if (appealText.isEmpty()) {
            Toast.makeText(this, "Please enter your appeal", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitAppeal.setEnabled(false);

        executor.execute(() -> {
            try {
                String attachmentUrl = "";
                if (userEvidenceUri != null) {
                    byte[] data = SupabaseHelper.getBytesFromUri(this, userEvidenceUri);
                    String fileName = System.currentTimeMillis() + "_user_evidence";
                    attachmentUrl = SupabaseHelper.uploadStorageBytes("moderation-evidence", fileName, data, appealToken);
                }

                JSONObject patchData = new JSONObject();
                patchData.put("user_appeal_text", appealText);
                patchData.put("appeal_status", "PENDING");
                if (!attachmentUrl.isEmpty()) {
                    patchData.put("user_attachment_url", attachmentUrl);
                }

                java.net.HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/moderation_cases?id=eq." + caseId, "PATCH", appealToken);
                SupabaseHelper.writeJson(conn, patchData);
                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        Toast.makeText(this, "Appeal submitted successfully", Toast.LENGTH_LONG).show();
                        btnSubmitAppeal.setText("Appeal Pending");
                    } else {
                        Toast.makeText(this, "Failed to submit appeal", Toast.LENGTH_SHORT).show();
                        btnSubmitAppeal.setEnabled(true);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmitAppeal.setEnabled(true);
                });
            }
        });
    }
}