package com.example.ikhaya_accommodation_seeking_app;


import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminReportsActivity extends AppCompatActivity {

    private LinearLayout reportsContainer;
    private ProgressBar progressReports;
    private TextView tvEmptyReports;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_reports);

        View btnBack = findViewById(R.id.btnBackToHub);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        reportsContainer = findViewById(R.id.reportsContainer);
        progressReports = findViewById(R.id.progressReports);
        tvEmptyReports = findViewById(R.id.tvEmptyReports);

        loadReports();
    }

    private void loadReports() {
        String token = SupabaseHelper.getAccessToken(this);

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (progressReports != null) {
            progressReports.setVisibility(View.VISIBLE);
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/reports?select=*&status=in.(OPEN,open,PENDING,pending)&order=created_at.desc", "GET", token);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(response);
                    runOnUiThread(() -> {
                        if (progressReports != null) progressReports.setVisibility(View.GONE);
                        displayReports(arr);
                    });
                } else {
                    runOnUiThread(() -> {
                        if (progressReports != null) progressReports.setVisibility(View.GONE);
                        if (tvEmptyReports != null) tvEmptyReports.setVisibility(View.VISIBLE);
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (progressReports != null) progressReports.setVisibility(View.GONE);
                    if (tvEmptyReports != null) tvEmptyReports.setVisibility(View.VISIBLE);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void displayReports(JSONArray arr) {
        if (reportsContainer == null) return;
        reportsContainer.removeAllViews();

        if (arr.length() == 0) {
            if (tvEmptyReports != null) tvEmptyReports.setVisibility(View.VISIBLE);
            return;
        }

        if (tvEmptyReports != null) tvEmptyReports.setVisibility(View.GONE);

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                addReportCard(obj, i);
            } catch (Exception e) {
                android.util.Log.e("iKhaya", "Error", e);
            }
        }
    }

    private void addReportCard(JSONObject obj, int position) {
        String ticketId = obj.optString("id", "");
        String reporterUid = obj.optString("reporter_uid", "");
        String reporterName = obj.optString("reporter_name", "User");
        String reporterRole = obj.optString("reporter_role", "Member");
        String reason = obj.optString("reason", "Support Ticket");
        String description = obj.optString("description", "No description provided");
        String status = obj.optString("status", "OPEN");

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);
        card.setRadius(24f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 28, 32, 28);

        TextView tvReason = new TextView(this);
        tvReason.setText(reason);
        tvReason.setTextSize(16f);
        tvReason.setTypeface(null, Typeface.BOLD);
        tvReason.setTextColor(Color.parseColor("#BA5536"));

        TextView tvReporter = new TextView(this);
        tvReporter.setText("By: " + reporterName + " (" + reporterRole.toUpperCase() + ")");
        tvReporter.setTextSize(12f);
        tvReporter.setTextColor(Color.parseColor("#8C7A70"));

        TextView tvDesc = new TextView(this);
        tvDesc.setText(description);
        tvDesc.setTextSize(14f);
        tvDesc.setTextColor(Color.parseColor("#1F1614"));
        tvDesc.setPadding(0, 12, 0, 12);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Status: " + status);
        tvStatus.setTextSize(12f);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setTextColor(Color.parseColor("#2DCE89"));

        layout.addView(tvReason);
        layout.addView(tvReporter);
        layout.addView(tvDesc);
        layout.addView(tvStatus);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 16, 0, 0);

        Button btnResolve = new Button(this);
        btnResolve.setText("Resolve");
        btnResolve.setOnClickListener(v -> showResponseDialog(ticketId, reporterUid, reason, "RESOLVED", "Resolution Message", card));
        actionRow.addView(btnResolve);

        Button btnDismiss = new Button(this);
        btnDismiss.setText("Dismiss");
        btnDismiss.setOnClickListener(v -> showResponseDialog(ticketId, reporterUid, reason, "DISMISSED", "Reason for Dismissal", card));
        actionRow.addView(btnDismiss);

        layout.addView(actionRow);
        card.addView(layout);

        reportsContainer.addView(card);
    }

    private void showResponseDialog(String ticketId, String reporterUid, String ticketReason, String newStatus, String prompt, View cardView) {
        EditText input = new EditText(this);
        input.setHint("Enter response to the user");
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle(prompt)
                .setView(input)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String responseText = input.getText().toString().trim();
                    updateTicketStatus(ticketId, reporterUid, ticketReason, newStatus, responseText, cardView);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTicketStatus(String ticketId, String reporterUid, String ticketReason, String newStatus, String adminResponse, View cardView) {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || ticketId.isEmpty()) return;

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject patchBody = new JSONObject();
                patchBody.put("status", newStatus);
                patchBody.put("admin_response", adminResponse);

                conn = SupabaseHelper.createConnection(
                        "/rest/v1/reports?id=eq." + ticketId,
                        "PATCH",
                        token
                );
                conn.setRequestProperty("Prefer", "return=minimal");
                SupabaseHelper.writeJson(conn, patchBody);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {

                    // Trigger the notification to the user who reported the problem
                    if (!reporterUid.isEmpty()) {
                        SupabaseHelper.createNotification(
                                reporterUid,
                                "Support Ticket Updated",
                                "An admin has responded to your ticket regarding: " + ticketReason + ".",
                                token
                        );
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Ticket marked as " + newStatus, Toast.LENGTH_SHORT).show();
                        if (reportsContainer != null) {
                            reportsContainer.removeView(cardView);
                        }
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed to update ticket (HTTP " + code + ")", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error connecting to server", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}