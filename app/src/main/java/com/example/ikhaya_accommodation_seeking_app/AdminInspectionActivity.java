package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminInspectionActivity extends AppCompatActivity {

    private ImageView ivInspectImage;
    private TextView tvInspectTitle, tvInspectDetails, tvInspectLandlord, tvInspectDescription;
    private Button btnRequireChanges, btnApprove;

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
            Toast.makeText(this, "Error: Missing Listing ID", Toast.LENGTH_SHORT).show();
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

        findViewById(R.id.btnBackInspection).setOnClickListener(v -> finish());

        String status = getIntent().getStringExtra("LISTING_STATUS");

        if ("APPROVED".equalsIgnoreCase(status)) {
            // Hide the Approve button since it's already live
            btnApprove.setVisibility(View.GONE);

            // Change the text and action of the red button to "Revoke"
            btnRequireChanges.setText("Revoke Live Status");
            btnRequireChanges.setOnClickListener(v -> showComplianceDialog());
        } else {
            // Standard Pending View
            btnRequireChanges.setText("Require Fixes");
            btnApprove.setVisibility(View.VISIBLE);

            btnApprove.setOnClickListener(v -> submitModerationDecision("APPROVED", null));
            btnRequireChanges.setOnClickListener(v -> showComplianceDialog());
        }

        fetchFullListingDetails();
    }

    private void fetchFullListingDetails() {
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = "/rest/v1/listings?select=*&id=eq." + URLEncoder.encode(listingId, "UTF-8") + "&limit=1";
                conn = SupabaseHelper.createConnection(endpoint, "GET", token);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (arr.length() > 0) {
                        JSONObject listing = arr.getJSONObject(0);

                        propertyTitle = listing.optString("property_name", "Untitled Property");
                        landlordUid = listing.optString("landlord_uid", "");

                        String price = listing.optString("price", "0");
                        String loc = listing.optString("location", "Unknown Location");
                        String type = listing.optString("property_type", "Room");
                        String rooms = listing.optString("rooms", "1");
                        String desc = listing.optString("description", "No description provided.");
                        String imageUrl = listing.optString("image_url", "");

                        String detailsStr = type + " • " + rooms + " Room(s)\n" + loc + " • R " + price;
                        String landlordStr = "Submitted by: Landlord (" + (landlordUid.length() > 8 ? landlordUid.substring(0, 8) : landlordUid) + "...)";

                        runOnUiThread(() -> {
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
                runOnUiThread(() -> Toast.makeText(this, "Failed to load listing data.", Toast.LENGTH_SHORT).show());
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
                            runOnUiThread(() -> ivInspectImage.setImageBitmap(bmp));
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) { }
        });
    }

    private void showComplianceDialog() {
        EditText input = new EditText(this);
        input.setHint("e.g. Please upload clear photos and fix the address.");
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle("Require Changes")
                .setMessage("Force the landlord to correct this listing. Provide the exact reason/instructions:")
                .setView(input)
                .setPositiveButton("Send Back", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "You must provide instructions to the landlord.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    submitModerationDecision("REJECTED", reason);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitModerationDecision(String newStatus, String rejectionReason) {
        String token = SupabaseHelper.getAccessToken(this);
        btnApprove.setEnabled(false);
        btnRequireChanges.setEnabled(false);

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

                // Notify Landlord for BOTH Approval and Rejection
                if (!landlordUid.isEmpty()) {
                    if ("REJECTED".equals(newStatus)) {
                        sendLandlordNotification(token, "Action Required: " + propertyTitle, "Your listing requires updates. Admin notes: " + rejectionReason);
                    } else if ("APPROVED".equals(newStatus)) {
                        sendLandlordNotification(token, "Listing Approved: " + propertyTitle, "Congratulations! Your property is now live on the platform.");
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, "Listing " + newStatus.toLowerCase(), Toast.LENGTH_SHORT).show();
                    finish(); // Return to queue
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnApprove.setEnabled(true);
                    btnRequireChanges.setEnabled(true);
                });
            }
        });
    }

    private void sendLandlordNotification(String token, String title, String message) {
        HttpURLConnection conn = null;
        try {
            conn = SupabaseHelper.createConnection("/rest/v1/notifications", "POST", token);
            JSONObject notif = new JSONObject();
            notif.put("user_uid", landlordUid);
            notif.put("title", title);
            notif.put("message", message);

            SupabaseHelper.writeJson(conn, notif);
            conn.getResponseCode();
        } catch (Exception ignored) { }
        finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}