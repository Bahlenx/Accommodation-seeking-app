package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout pendingRequestsContainer;
    private LinearLayout notificationsListContainer;
    private String userId;
    private String accessToken;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        pendingRequestsContainer = findViewById(R.id.pendingRequestsContainer);
        notificationsListContainer = findViewById(R.id.notificationsListContainer);
        Button btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        userId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        if (userId != null && accessToken != null) {
            loadPendingChatRequests();
            loadNotifications();
        }
    }

    private void loadPendingChatRequests() {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String encId = SupabaseHelper.encode(userId);
                String endpoint = "/rest/v1/chat_requests?receiver_id=eq." + encId + "&status=eq.pending&order=created_at.desc";
                
                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray requests = new JSONArray(response);
                    runOnUiThread(() -> renderPendingRequests(requests));
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error loading chat requests", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void renderPendingRequests(JSONArray requests) {
        pendingRequestsContainer.removeAllViews();
        if (requests.length() == 0) return;

        try {
            TextView title = new TextView(this);
            title.setText("Pending Chat Requests");
            title.setTextSize(18f);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(Color.parseColor("#D9480F"));
            title.setPadding(0, 0, 0, 16);
            pendingRequestsContainer.addView(title);

            for (int i = 0; i < requests.length(); i++) {
                JSONObject reqObj = requests.getJSONObject(i);
                renderRequestCard(reqObj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderRequestCard(JSONObject reqObj) {
        try {
            String rowId = reqObj.optString("id");
            String senderId = reqObj.optString("sender_id");
            String senderName = reqObj.optString("sender_name", "User");
            String senderRole = reqObj.optString("sender_role", "Member");
            
            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);
            card.setRadius(16f);
            card.setCardElevation(4f);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(32, 24, 32, 24);

            TextView txtBody = new TextView(this);
            txtBody.setText(senderName + " (" + senderRole + ") wants to start a chat with you.");
            txtBody.setTextSize(15f);
            txtBody.setTextColor(Color.BLACK);
            inner.addView(txtBody);

            LinearLayout btnLayout = new LinearLayout(this);
            btnLayout.setOrientation(LinearLayout.HORIZONTAL);
            btnLayout.setPadding(0, 16, 0, 0);

            Button btnAccept = new Button(this);
            btnAccept.setText("Accept");
            btnAccept.setBackgroundColor(Color.parseColor("#4CAF50"));
            btnAccept.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMargins(0, 0, 8, 0);
            btnAccept.setLayoutParams(btnParams);
            
            Button btnDecline = new Button(this);
            btnDecline.setText("Decline");
            btnDecline.setBackgroundColor(Color.parseColor("#F44336"));
            btnDecline.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams2.setMargins(8, 0, 0, 0);
            btnDecline.setLayoutParams(btnParams2);

            btnAccept.setOnClickListener(v -> updateRequestStatus(reqObj, "accepted", card, senderId, senderName));
            btnDecline.setOnClickListener(v -> updateRequestStatus(reqObj, "declined", card, null, null));

            btnLayout.addView(btnAccept);
            btnLayout.addView(btnDecline);
            inner.addView(btnLayout);
            card.addView(inner);
            
            pendingRequestsContainer.addView(card);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateRequestStatus(JSONObject fullRow, String newStatus, CardView card, String targetUserId, String targetName) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // We use POST with on_conflict to UPSERT because PATCH throws ProtocolException on many Android versions
                conn = SupabaseHelper.createConnection("/rest/v1/chat_requests?on_conflict=id", "POST", accessToken);
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");

                fullRow.put("status", newStatus);
                SupabaseHelper.writeJson(conn, fullRow);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        pendingRequestsContainer.removeView(card);
                        if ("accepted".equals(newStatus)) {
                            Toast.makeText(this, "Chat request accepted!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(this, ChatActivity.class);
                            intent.putExtra("RECEIVER_ID", targetUserId);
                            intent.putExtra("RECEIVER_NAME", targetName);
                            intent.putExtra("IS_COMMUNITY", false);
                            
                            // Re-fetch current user role and name from prefs
                            android.content.SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
                            intent.putExtra("SENDER_ID", userId);
                            intent.putExtra("SENDER_NAME", prefs.getString("USER_NAME", "User"));
                            intent.putExtra("SENDER_ROLE", prefs.getString("USER_ROLE", "Member"));
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "Chat request declined.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    String err = SupabaseHelper.readResponse(conn);
                    runOnUiThread(() -> Toast.makeText(this, "Failed to update: " + err, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error updating request.", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void loadNotifications() {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint =
                        "/rest/v1/notifications" +
                                "?select=*" +
                                "&user_uid=eq." +
                                SupabaseHelper.encode(userId) +
                                "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                String response =
                        SupabaseHelper.readResponse(connection);

                JSONArray notifications =
                        new JSONArray(response);

                runOnUiThread(() -> {
                    notificationsListContainer.removeAllViews();
                    if (notifications.length() > 0) {
                        try {
                            for (int i = 0; i < notifications.length(); i++) {
                                JSONObject notifObj = notifications.getJSONObject(i);
                                String title = notifObj.optString("title", "Notification");
                                String message = notifObj.optString("message", "");
                                
                                CardView card = new CardView(NotificationsActivity.this);
                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                );
                                params.setMargins(0, 0, 0, 16);
                                card.setLayoutParams(params);
                                card.setRadius(12f);
                                card.setCardElevation(2f);
                                card.setCardBackgroundColor(Color.WHITE);

                                LinearLayout inner = new LinearLayout(NotificationsActivity.this);
                                inner.setOrientation(LinearLayout.VERTICAL);
                                inner.setPadding(24, 20, 24, 20);

                                TextView txtTitle = new TextView(NotificationsActivity.this);
                                txtTitle.setText(title);
                                txtTitle.setTextSize(16f);
                                txtTitle.setTypeface(null, Typeface.BOLD);
                                txtTitle.setTextColor(Color.parseColor("#212529"));

                                TextView txtBody = new TextView(NotificationsActivity.this);
                                txtBody.setText(message);
                                txtBody.setTextSize(14f);
                                txtBody.setTextColor(Color.parseColor("#6C757D"));
                                txtBody.setPadding(0, 4, 0, 0);

                                inner.addView(txtTitle);
                                inner.addView(txtBody);
                                card.addView(inner);

                                notificationsListContainer.addView(card);
                            }
                        } catch (Exception e) {
                            // Ignored
                        }
                    } else {
                        TextView textView = new TextView(NotificationsActivity.this);
                        textView.setText("No new general notifications.");
                        textView.setTextSize(15f);
                        notificationsListContainer.addView(textView);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                NotificationsActivity.this,
                                "Could not load notifications.",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}