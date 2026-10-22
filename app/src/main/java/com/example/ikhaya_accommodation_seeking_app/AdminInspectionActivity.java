package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminInspectionActivity extends AppCompatActivity {

    private ImageView ivInspectImage;
    private TextView tvInspectTitle;
    private TextView tvInspectDetails;
    private TextView tvInspectLandlord;
    private TextView tvInspectDescription;
    private Button btnRequireChanges;
    private Button btnApprove;

    private String listingId = "";
    private String landlordUid = "";
    private String propertyTitle = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_inspection);

        listingId = getIntent().getStringExtra("LISTING_ID");
        if (listingId == null || listingId.isEmpty()) {
            Toast.makeText(this, R.string.error_missing_listing_id, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ivInspectImage = findViewById(R.id.ivInspectImage);
        tvInspectTitle = findViewById(R.id.tvInspectTitle);
        tvInspectDetails = findViewById(R.id.tvInspectDetails);
        tvInspectLandlord = findViewById(R.id.tvInspectLandlord);
        tvInspectDescription = findViewById(R.id.tvInspectDescription);

        btnRequireChanges = findViewById(R.id.btnRequireChanges);
        btnApprove = findViewById(R.id.btnApprove);

        View btnBack = findViewById(R.id.btnBackInspection);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Apply fast intent extras if supplied
        String passedTitle = getIntent().getStringExtra("LISTING_TITLE");
        if (passedTitle != null && !passedTitle.isEmpty()) {
            tvInspectTitle.setText(passedTitle);
        }

        String passedDetails = getIntent().getStringExtra("LISTING_DETAILS");
        if (passedDetails != null && !passedDetails.isEmpty()) {
            tvInspectDetails.setText(passedDetails);
        }

        String passedLandlord = getIntent().getStringExtra("LANDLORD_NAME");
        if (passedLandlord != null && !passedLandlord.isEmpty()) {
            tvInspectLandlord.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.label_submitted_by), passedLandlord));
        }

        String status = getIntent().getStringExtra("LISTING_STATUS");

        if ("APPROVED".equalsIgnoreCase(status)) {
            if (btnApprove != null) {
                btnApprove.setVisibility(View.GONE);
            }
            if (btnRequireChanges != null) {
                btnRequireChanges.setText(R.string.btn_revoke_live_status);
                btnRequireChanges.setOnClickListener(v -> showComplianceDialog());
            }
        } else {
            if (btnRequireChanges != null) {
                btnRequireChanges.setText(R.string.btn_require_fixes);
                btnRequireChanges.setOnClickListener(v -> showComplianceDialog());
            }
            if (btnApprove != null) {
                btnApprove.setVisibility(View.VISIBLE);
                btnApprove.setOnClickListener(v -> submitModerationDecision("APPROVED", null));
            }
        }

        fetchFullListingDetails();
    }

    private void fetchFullListingDetails() {
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = "/rest/v1/listings?select=*&id=eq." + URLEncoder.encode(listingId, StandardCharsets.UTF_8.name()) + "&limit=1";
                conn = SupabaseHelper.createConnection(endpoint, "GET", token);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (arr.length() > 0) {
                        JSONObject listing = arr.getJSONObject(0);

                        propertyTitle = listing.optString("property_name", getString(R.string.default_untitled_property));
                        landlordUid = listing.optString("landlord_uid", "");

                        String price = listing.optString("price", "0");
                        String loc = listing.optString("location", getString(R.string.default_unknown_location));
                        String type = listing.optString("property_type", getString(R.string.filter_chip_rooms));
                        String rooms = listing.optString("rooms", "1");
                        String desc = listing.optString("description", getString(R.string.default_no_description));
                        String imageUrl = listing.optString("image_url", "");

                        String detailsStr = String.format(Locale.getDefault(), "%s • %s %s\n%s • R %s",
                                type, rooms, getString(R.string.label_rooms_count), loc, price);
                        String landlordShortId = landlordUid.length() > 8 ? landlordUid.substring(0, 8) : landlordUid;
                        String landlordStr = String.format(Locale.getDefault(), "%s: %s (%s...)",
                                getString(R.string.label_submitted_by), getString(R.string.role_landlord), landlordShortId);

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            tvInspectTitle.setText(propertyTitle);
                            tvInspectDetails.setText(detailsStr);
                            tvInspectLandlord.setText(landlordStr);
                            tvInspectDescription.setText(desc);
                        });

                        if (!imageUrl.isEmpty()) {
                            loadPropertyImageAsync(imageUrl);
                        }
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.error_failed_load_listing_data, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void loadPropertyImageAsync(String urlStr) {
        executor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = conn.getInputStream()) {
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        if (bmp != null) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                ivInspectImage.setImageBitmap(bmp);
                            });
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) { }
        });
    }

    private void showComplianceDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.hint_compliance_reason);
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_require_changes)
                .setMessage(R.string.msg_require_changes_instructions)
                .setView(input)
                .setPositiveButton(R.string.btn_send_back, (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, R.string.error_must_provide_instructions, Toast.LENGTH_LONG).show();
                        return;
                    }
                    submitModerationDecision("REJECTED", reason);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void submitModerationDecision(String newStatus, String rejectionReason) {
        String token = SupabaseHelper.getAccessToken(this);
        if (btnApprove != null) btnApprove.setEnabled(false);
        if (btnRequireChanges != null) btnRequireChanges.setEnabled(false);

        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/listings?id=eq." + listingId;
                JSONObject body = new JSONObject();
                body.put("status", newStatus);
                body.put("availability", "APPROVED".equals(newStatus) ? "Available" : "Rejected");

                if (rejectionReason != null && !rejectionReason.isEmpty()) {
                    body.put("rejection_reason", rejectionReason);
                }

                SupabaseHelper.patch(endpoint, body, token);

                if (!landlordUid.isEmpty()) {
                    if ("REJECTED".equals(newStatus)) {
                        String notifTitle = String.format(Locale.getDefault(), "%s: %s", getString(R.string.notif_title_action_required), propertyTitle);
                        String notifMsg = String.format(Locale.getDefault(), "%s: %s", getString(R.string.notif_msg_requires_updates_prefix), rejectionReason);
                        SupabaseHelper.createNotification(landlordUid, notifTitle, notifMsg, token);
                    } else if ("APPROVED".equals(newStatus)) {
                        String notifTitle = String.format(Locale.getDefault(), "%s: %s", getString(R.string.notif_title_listing_approved), propertyTitle);
                        String notifMsg = getString(R.string.notif_msg_listing_approved_body);
                        SupabaseHelper.createNotification(landlordUid, notifTitle, notifMsg, token);
                    }
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String statusLabel = "APPROVED".equals(newStatus)
                            ? getString(R.string.status_approved_lower)
                            : getString(R.string.status_rejected_lower);
                    Toast.makeText(this, String.format(Locale.getDefault(), "%s %s", getString(R.string.listing_prefix), statusLabel), Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, String.format(Locale.getDefault(), "%s: %s", getString(R.string.action_failed_prefix), e.getMessage()), Toast.LENGTH_LONG).show();
                    if (btnApprove != null) btnApprove.setEnabled(true);
                    if (btnRequireChanges != null) btnRequireChanges.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}