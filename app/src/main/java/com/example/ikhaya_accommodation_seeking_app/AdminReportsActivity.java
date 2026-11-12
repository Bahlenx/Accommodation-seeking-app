package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
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
    
    private TextView tabIssueTickets, tabGuestChats;
    private boolean showingGuestChats = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private android.graphics.drawable.GradientDrawable roundedBg(String fillHex, int radiusDp) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

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
        
        View layoutSupportTabs = findViewById(R.id.layoutSupportTabs);
        if (layoutSupportTabs != null) {
            layoutSupportTabs.setBackground(roundedBg("#EAE3DC", 12));
        }

        tabIssueTickets = findViewById(R.id.tabIssueTickets);
        tabGuestChats = findViewById(R.id.tabGuestChats);
        
        tabIssueTickets.setBackground(roundedBg("#B85032", 9));
        tabGuestChats.setBackground(roundedBg("#00000000", 9));
        
        tabIssueTickets.setOnClickListener(v -> {
            if (showingGuestChats) {
                showingGuestChats = false;
                tabIssueTickets.setBackground(roundedBg("#B85032", 9));
                tabIssueTickets.setTextColor(Color.WHITE);
                tabGuestChats.setBackground(roundedBg("#00000000", 9));
                tabGuestChats.setTextColor(Color.parseColor("#6D635E"));
                loadReports();
            }
        });
        
        tabGuestChats.setOnClickListener(v -> {
            if (!showingGuestChats) {
                showingGuestChats = true;
                tabGuestChats.setBackground(roundedBg("#B85032", 9));
                tabGuestChats.setTextColor(Color.WHITE);
                tabIssueTickets.setBackground(roundedBg("#00000000", 9));
                tabIssueTickets.setTextColor(Color.parseColor("#6D635E"));
                loadGuestSupportChats();
            }
        });

        loadReports();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (showingGuestChats) {
            loadGuestSupportChats();
        } else {
            loadReports();
        }
        refreshTabCounts();
    }

    private void loadReports() {
        String token = SupabaseHelper.getAccessToken(this);

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.please_log_in_again, Toast.LENGTH_SHORT).show();
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
                e.printStackTrace();
            }
        }
    }

    private void addReportCard(JSONObject obj, int position) {
        String ticketId = obj.optString("id", "");
        String reporterUid = obj.optString("reporter_uid", "");
        String reporterName = obj.optString("reporter_name", obj.optString("reporter_email", "Deleted User"));
        String reporterRole = obj.optString("reporter_role", "Member");
        String reason = obj.optString("reason", "Support Ticket");
        String issueType = obj.optString("issue_type", reason);
        String description = obj.optString("description", "No description provided");
        String status = obj.optString("status", "OPEN");

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(14));
        card.setCardElevation(4f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView tvReason = new TextView(this);
        tvReason.setText(reason);
        tvReason.setTextSize(16f);
        tvReason.setTypeface(null, Typeface.BOLD);
        tvReason.setTextColor(Color.parseColor("#BA5536"));
        
        if (issueType.contains("ACCOUNT DELETED") || reason.contains("ACCOUNT DELETED")) {
            tvReason.setText("ACCOUNT DELETED");
            tvReason.setTextColor(Color.parseColor("#B91C1C"));
            tvReason.setBackground(roundedBg("#FEE2E2", 6));
            tvReason.setPadding(dp(8), dp(4), dp(8), dp(4));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tvReason.setLayoutParams(badgeParams);
        }

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
        actionRow.setPadding(0, dp(16), 0, 0);

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

    private void refreshTabCounts() {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || token.isEmpty()) return;
        
        executor.execute(() -> {
            try {
                HttpURLConnection connRpt = SupabaseHelper.createConnection(
                        "/rest/v1/reports?select=id,status&status=in.(OPEN,open,PENDING,pending)", 
                        "GET", token);
                int rptCount = 0;
                if (connRpt.getResponseCode() >= 200 && connRpt.getResponseCode() < 300) {
                    rptCount = new JSONArray(SupabaseHelper.readResponse(connRpt)).length();
                }
                connRpt.disconnect();

                HttpURLConnection connChats = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?select=guest_id",
                        "GET", token);
                connChats.setRequestProperty("x-admin-panel", "true");
                java.util.HashSet<String> uniqueGuests = new java.util.HashSet<>();
                if (connChats.getResponseCode() >= 200 && connChats.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(connChats));
                    for (int i = 0; i < arr.length(); i++) {
                        String gId = arr.getJSONObject(i).optString("guest_id", "");
                        if (!gId.isEmpty()) uniqueGuests.add(gId);
                    }
                }
                connChats.disconnect();

                final int fRptCount = rptCount;
                final int fGuestCount = uniqueGuests.size();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (tabIssueTickets != null) tabIssueTickets.setText("Issue Tickets (" + fRptCount + ")");
                    if (tabGuestChats != null) tabGuestChats.setText("Guest Chats (" + fGuestCount + ")");
                });
            } catch (Exception ignored) {}
        });
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
                        Toast.makeText(this, getString(R.string.msg_ticket_marked_as_prefix) + newStatus, Toast.LENGTH_SHORT).show();
                        if (reportsContainer != null) {
                            reportsContainer.removeView(cardView);
                        }
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, getString(R.string.error_failed_update_ticket_http) + code + ")", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.password_connection_error, Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void loadGuestSupportChats() {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || token.isEmpty()) return;

        progressReports.setVisibility(View.VISIBLE);
        tvEmptyReports.setVisibility(View.GONE);
        reportsContainer.removeAllViews();

        executor.execute(() -> {
            try {
                HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?select=guest_id,guest_hint,sender_name,message_text,created_at&order=created_at.desc",
                        "GET", token);
                conn.setRequestProperty("x-admin-panel", "true");
                
                int code = conn.getResponseCode();
                String res = SupabaseHelper.readResponse(conn);
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(res);
                    java.util.HashSet<String> seenGuests = new java.util.HashSet<>();
                    final JSONArray uniqueChats = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject msg = arr.getJSONObject(i);
                        String gId = msg.optString("guest_id", "");
                        if (!gId.isEmpty() && !seenGuests.contains(gId)) {
                            seenGuests.add(gId);
                            uniqueChats.put(msg);
                        }
                    }

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progressReports.setVisibility(View.GONE);
                        tabGuestChats.setText("Guest Support Chats (" + uniqueChats.length() + ")");
                        
                        if (uniqueChats.length() == 0) {
                            tvEmptyReports.setText("No active guest support chats found.");
                            tvEmptyReports.setVisibility(View.VISIBLE);
                        } else {
                            for (int i = 0; i < uniqueChats.length(); i++) {
                                JSONObject msg = uniqueChats.optJSONObject(i);
                                if (msg != null) addGuestChatCard(msg);
                            }
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        progressReports.setVisibility(View.GONE);
                        Toast.makeText(this, "Failed to load chats.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressReports.setVisibility(View.GONE);
                    Toast.makeText(this, "Error fetching chats.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addGuestChatCard(JSONObject msg) {
        String gId = msg.optString("guest_id", "");
        String gHint = msg.optString("guest_hint", "");
        if (gHint.isEmpty() || gHint.startsWith("guest_")) {
            gHint = msg.optString("sender_name", "");
        }
        if (gHint.isEmpty() || gHint.startsWith("guest_")) {
            gHint = "Guest (" + gId.substring(0, Math.min(12, gId.length())) + ")";
        }
        
        String latestText = msg.optString("message_text", "Sent a message");
        String senderName = msg.optString("sender_name", "");

        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(16), dp(16), dp(16), dp(12));
        card.setLayoutParams(params);
        card.setCardElevation(4f);
        card.setRadius(dp(14));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView tvBadge = new TextView(this);
        tvBadge.setText("PRE-LOGIN GUEST SUPPORT");
        tvBadge.setTextColor(Color.parseColor("#B85032"));
        tvBadge.setTextSize(10f);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setBackground(roundedBg("#FDE8E1", 6));
        tvBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tvBadge.setLayoutParams(badgeParams);
        layout.addView(tvBadge);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(gHint);
        tvTitle.setTextColor(Color.parseColor("#212529"));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, 8, 0, 8);
        layout.addView(tvTitle);

        TextView tvBody = new TextView(this);
        tvBody.setText((senderName.equals(gHint) ? "" : senderName + ": ") + latestText);
        tvBody.setTextColor(Color.parseColor("#495057"));
        tvBody.setTextSize(14f);
        layout.addView(tvBody);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(16), 0, 0);
        actionRow.setLayoutParams(rowParams);

        Button btnReply = new Button(this);
        LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(
                0,
                dp(42),
                1.2f
        );
        replyParams.setMargins(0, 0, dp(8), 0);
        btnReply.setLayoutParams(replyParams);
        btnReply.setText("Open Chat & Reply");
        btnReply.setAllCaps(false);
        btnReply.setTextSize(13f);
        btnReply.setBackground(roundedBg("#B85032", 10));
        btnReply.setTextColor(Color.WHITE);
        btnReply.setTypeface(null, Typeface.BOLD);

        final String finalGHint = gHint;
        btnReply.setOnClickListener(v -> {
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("IS_ADMIN_SUPPORT", true);
            chatIntent.putExtra("GUEST_ID", gId);
            chatIntent.putExtra("SENDER_ID", "ADMIN_SUPPORT");
            chatIntent.putExtra("SENDER_NAME", "iKhaya Admin Support");
            chatIntent.putExtra("SENDER_ROLE", "ADMIN");
            chatIntent.putExtra("RECEIVER_ID", gId);
            chatIntent.putExtra("RECEIVER_NAME", finalGHint);
            startActivity(chatIntent);
        });

        Button btnResolveChat = new Button(this);
        LinearLayout.LayoutParams resolveParams = new LinearLayout.LayoutParams(
                0,
                dp(42),
                0.8f
        );
        btnResolveChat.setLayoutParams(resolveParams);
        btnResolveChat.setText("Resolve");
        btnResolveChat.setAllCaps(false);
        btnResolveChat.setTextSize(13f);
        btnResolveChat.setBackground(roundedBg("#15803D", 10));
        btnResolveChat.setTextColor(Color.WHITE);
        btnResolveChat.setTypeface(null, Typeface.BOLD);

        btnResolveChat.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Resolve Guest Support Chat")
                    .setMessage("Mark " + finalGHint + "'s support request as resolved? This will close the active guest thread.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Mark Resolved", (dialog, which) -> {
                        btnResolveChat.setEnabled(false);
                        resolveGuestSupportChat(gId, finalGHint, latestText, card, btnResolveChat);
                    })
                    .show();
        });

        actionRow.addView(btnReply);
        actionRow.addView(btnResolveChat);
        layout.addView(actionRow);
        card.addView(layout);
        reportsContainer.addView(card);
    }

    private void resolveGuestSupportChat(String guestId, String guestHint, String lastMessage, View cardView, Button btnResolve) {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || token.isEmpty() || guestId == null || guestId.isEmpty()) {
            if (btnResolve != null) btnResolve.setEnabled(true);
            return;
        }

        executor.execute(() -> {
            HttpURLConnection delConn = null;
            try {
                // 1. Save a RESOLVED audit entry in `reports` so historical records are preserved
                try {
                    HttpURLConnection archiveConn = SupabaseHelper.createConnection("/rest/v1/reports", "POST", token);
                    archiveConn.setRequestProperty("Prefer", "return=minimal");
                    JSONObject archiveBody = new JSONObject();
                    archiveBody.put("reporter_uid", guestId);
                    archiveBody.put("reporter_id", guestId);
                    archiveBody.put("reporter_name", guestHint);
                    archiveBody.put("reporter_role", "GUEST");
                    archiveBody.put("reason", "GUEST CHAT RESOLVED (" + guestHint + ")");
                    archiveBody.put("issue_type", "GUEST CHAT RESOLVED");
                    archiveBody.put("description", "Resolved guest support thread. Last message: " + lastMessage);
                    archiveBody.put("status", "RESOLVED");
                    SupabaseHelper.writeJson(archiveConn, archiveBody);
                    archiveConn.getResponseCode();
                    archiveConn.disconnect();
                } catch (Exception ignored) {}

                // 2. Delete the active temporary thread from `guest_support_messages`
                String encodedGuestId = java.net.URLEncoder.encode(guestId, "UTF-8");
                delConn = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?guest_id=eq." + encodedGuestId,
                        "DELETE",
                        token
                );
                delConn.setRequestProperty("x-admin-panel", "true");
                delConn.setRequestProperty("x-guest-id", guestId);

                int code = delConn.getResponseCode();
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, "Guest support chat marked as resolved.", Toast.LENGTH_SHORT).show();
                        if (reportsContainer != null) {
                            reportsContainer.removeView(cardView);
                            if (reportsContainer.getChildCount() == 0 && tvEmptyReports != null) {
                                tvEmptyReports.setText("No active guest support chats found.");
                                tvEmptyReports.setVisibility(View.VISIBLE);
                            }
                        }
                        refreshTabCounts();
                    });
                } else {
                    runOnUiThread(() -> {
                        if (btnResolve != null) btnResolve.setEnabled(true);
                        Toast.makeText(this, "Failed to resolve chat (HTTP " + code + ")", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (btnResolve != null) btnResolve.setEnabled(true);
                    Toast.makeText(this, "Error resolving guest chat.", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (delConn != null) delConn.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}