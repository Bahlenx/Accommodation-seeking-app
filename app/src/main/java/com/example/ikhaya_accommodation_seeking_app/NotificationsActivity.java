package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "Notifications";

    private LinearLayout pendingRequestsContainer;
    private LinearLayout notificationsListContainer;

    private String userId;
    private String accessToken;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean activityVisible = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!activityVisible) return;

            if (userId != null && !userId.isEmpty() && accessToken != null && !accessToken.isEmpty()) {
                loadPendingChatRequests();
                loadNotifications();
                loadUnseenEnquiries();
            }
            pollHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        pendingRequestsContainer = findViewById(R.id.pendingRequestsContainer);
        notificationsListContainer = findViewById(R.id.notificationsListContainer);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        userId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        activityVisible = false;
        pollHandler.removeCallbacks(pollRunnable);
        super.onPause();
    }

    private void loadUnseenEnquiries() {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/enquiries?select=id&landlord_uid=eq." + SupabaseHelper.encode(userId);
                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    return;
                }

                JSONArray rows = new JSONArray(response);
                Set<String> seen = LandlordNotificationState.seen(this, "enquiries", userId);

                int unseen = 0;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row != null) {
                        String id = row.optString("id", "");
                        if (!id.isEmpty() && !seen.contains(id)) {
                            unseen++;
                        }
                    }
                }

                final int count = unseen;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || pendingRequestsContainer == null) {
                        return;
                    }

                    View previous = pendingRequestsContainer.findViewWithTag("unseen_enquiries_shortcut");
                    if (previous != null) {
                        pendingRequestsContainer.removeView(previous);
                    }

                    if (count == 0) {
                        return;
                    }

                    Button enquiries = new Button(this);
                    enquiries.setTag("unseen_enquiries_shortcut");
                    enquiries.setAllCaps(false);
                    enquiries.setText(getString(R.string.unseen_enquiries_count, count));
                    enquiries.setBackgroundResource(R.drawable.selector_regular_button);
                    enquiries.setTextColor(Color.WHITE);

                    enquiries.setOnClickListener(v ->
                            startActivity(new Intent(this, EnquiriesActivity.class))
                    );

                    pendingRequestsContainer.addView(enquiries, 0);
                });

            } catch (Exception e) {
                Log.w(TAG, "Unable to check new enquiries", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void loadPendingChatRequests() {
        executor.execute(() -> {
            HttpURLConnection conn = null;

            try {
                String endpoint = "/rest/v1/chat_requests?receiver_id=eq." + SupabaseHelper.encode(userId)
                        + "&status=eq.pending&order=created_at.desc";

                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray requests = new JSONArray(response);
                    runOnUiThread(() -> renderPendingRequests(requests));
                }

            } catch (Exception e) {
                showMessage(R.string.error_loading_chat_requests);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void renderPendingRequests(JSONArray requests) {
        if (pendingRequestsContainer == null) {
            return;
        }

        pendingRequestsContainer.removeAllViews();
        LandlordNotificationState.markSeen(this, "chat", userId, requests);

        if (requests.length() == 0) {
            return;
        }

        TextView title = new TextView(this);
        title.setText(R.string.pending_chat_requests);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#BA5536"));
        title.setPadding(0, 0, 0, dp(12));

        pendingRequestsContainer.addView(title);

        for (int i = 0; i < requests.length(); i++) {
            JSONObject request = requests.optJSONObject(i);
            if (request != null) {
                renderRequestCard(request);
            }
        }
    }

    private void renderRequestCard(JSONObject request) {
        String senderId = request.optString("sender_id", "");
        String senderName = request.optString("sender_name", getString(R.string.notification_user));
        String senderRole = request.optString("sender_role", getString(R.string.notification_member));

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(12);

        card.setLayoutParams(cardParams);
        card.setRadius(dp(14));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView body = new TextView(this);
        body.setText(getString(R.string.chat_request_message, senderName, senderRole));
        body.setTextSize(15f);
        body.setTextColor(Color.parseColor("#1F1614"));

        inner.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);

        Button accept = new Button(this);
        accept.setText(R.string.accept);
        accept.setAllCaps(false);
        accept.setBackgroundResource(R.drawable.selector_regular_button);
        accept.setTextColor(Color.WHITE);

        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        acceptParams.setMarginEnd(dp(5));
        accept.setLayoutParams(acceptParams);

        Button decline = new Button(this);
        decline.setText(R.string.decline);
        decline.setAllCaps(false);
        decline.setBackgroundResource(R.drawable.selector_regular_button);
        decline.setTextColor(Color.WHITE);

        LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        declineParams.setMarginStart(dp(5));
        decline.setLayoutParams(declineParams);

        accept.setOnClickListener(v -> {
            accept.setEnabled(false);
            decline.setEnabled(false);

            updateRequestStatus(
                    request,
                    "accepted",
                    card,
                    senderId,
                    senderName,
                    accept,
                    decline
            );
        });

        decline.setOnClickListener(v -> {
            accept.setEnabled(false);
            decline.setEnabled(false);
            card.setVisibility(View.GONE);

            executor.execute(() -> {
                try {
                    String reqId = request.optString("id", "");
                    String endpoint = "/rest/v1/chat_requests?id=eq." + SupabaseHelper.encode(reqId);
                    HttpURLConnection connection = SupabaseHelper.createConnection(endpoint, "DELETE", accessToken);
                    int code = connection.getResponseCode();
                    connection.disconnect();

                    if (code >= 200 && code < 300) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            pendingRequestsContainer.removeView(card);
                            Toast.makeText(this, R.string.chat_request_declined, Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception ignored) {
                }
            });
        });

        actions.addView(accept);
        actions.addView(decline);

        inner.addView(actions);
        card.addView(inner);

        pendingRequestsContainer.addView(card);
    }

    private void updateRequestStatus(
            JSONObject fullRow,
            String newStatus,
            CardView card,
            String targetUserId,
            String targetName,
            Button acceptButton,
            Button declineButton) {

        executor.execute(() -> {
            HttpURLConnection conn = null;

            try {
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/chat_requests?on_conflict=id",
                        "POST",
                        accessToken
                );

                conn.setRequestProperty(
                        "Prefer",
                        "resolution=merge-duplicates,return=minimal"
                );

                JSONObject updatedRow = new JSONObject(fullRow.toString());
                updatedRow.put("status", newStatus);

                SupabaseHelper.writeJson(conn, updatedRow);

                int code = conn.getResponseCode();

                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        pendingRequestsContainer.removeView(card);

                        if ("accepted".equals(newStatus)) {
                            Toast.makeText(this, R.string.chat_request_accepted, Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(this, ChatActivity.class);
                            intent.putExtra("RECEIVER_ID", targetUserId);
                            intent.putExtra("RECEIVER_NAME", targetName);
                            intent.putExtra("IS_COMMUNITY", false);

                            SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
                            intent.putExtra("SENDER_ID", userId);
                            intent.putExtra("SENDER_NAME", prefs.getString("USER_NAME", getString(R.string.notification_user)));
                            intent.putExtra("SENDER_ROLE", prefs.getString("USER_ROLE", getString(R.string.notification_member)));

                            startActivity(intent);
                        } else {
                            Toast.makeText(this, R.string.chat_request_declined, Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    String error = SupabaseHelper.readResponse(conn);
                    Log.e(TAG, "Chat request update failed: " + error);

                    runOnUiThread(() -> {
                        acceptButton.setEnabled(true);
                        declineButton.setEnabled(true);
                        Toast.makeText(this, R.string.chat_request_update_failed, Toast.LENGTH_LONG).show();
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating chat request", e);

                runOnUiThread(() -> {
                    acceptButton.setEnabled(true);
                    declineButton.setEnabled(true);
                    Toast.makeText(this, R.string.chat_request_update_failed, Toast.LENGTH_SHORT).show();
                });

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void loadNotifications() {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = "/rest/v1/notifications?select=*&user_uid=eq."
                        + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage(R.string.could_not_load_notifications);
                    return;
                }

                JSONArray notifications = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || notificationsListContainer == null) {
                        return;
                    }

                    notificationsListContainer.removeAllViews();

                    for (int i = 0; i < notifications.length(); i++) {
                        JSONObject notification = notifications.optJSONObject(i);
                        if (notification != null) {
                            renderNotificationCard(notification);
                        }
                    }

                    if (notifications.length() == 0) {
                        TextView empty = new TextView(this);
                        empty.setText(R.string.no_general_notifications);
                        empty.setTextSize(15f);
                        empty.setTextColor(Color.parseColor("#8C7A70"));
                        notificationsListContainer.addView(empty);
                    }

                    LandlordNotificationState.markSeen(this, "notifications", userId, notifications);
                });

            } catch (Exception e) {
                Log.e(TAG, "Could not load notifications", e);
                showMessage(R.string.could_not_load_notifications);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void renderNotificationCard(JSONObject notification) {
        String title = notification.optString("title", getString(R.string.notifications));
        String message = notification.optString("message", "");

        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(12);

        card.setLayoutParams(params);
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#1F1614"));

        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextSize(14f);
        messageView.setTextColor(Color.parseColor("#8C7A70"));
        messageView.setPadding(0, dp(4), 0, 0);

        inner.addView(titleView);
        inner.addView(messageView);

        card.addView(inner);
        notificationsListContainer.addView(card);
    }

    private void showMessage(int messageResource) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, messageResource, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        activityVisible = false;
        pollHandler.removeCallbacks(pollRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }
}