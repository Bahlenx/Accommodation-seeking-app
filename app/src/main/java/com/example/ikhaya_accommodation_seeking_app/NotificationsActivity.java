package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout containerChatRequests;
    private TextView tvChatRequestsHeader;
    private TextView tvNoNotifications;
    private TextView btnClearAllNotifications;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvNotifications;
    private NotificationsAdapter adapter;

    private String currentUserId = "";
    private String userRole = "";
    private String token = "";

    private final List<JSONObject> notificationList = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        currentUserId = SupabaseHelper.getUserId(this);
        token = SupabaseHelper.getAccessToken(this);

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        userRole = prefs.getString("USER_ROLE", "tenant");

        containerChatRequests = findViewById(R.id.containerChatRequests);
        tvChatRequestsHeader = findViewById(R.id.tvChatRequestsHeader);
        tvNoNotifications = findViewById(R.id.tvNoNotifications);
        btnClearAllNotifications = findViewById(R.id.btnClearAllNotifications);
        swipeRefresh = findViewById(R.id.swipeRefreshNotifications);
        rvNotifications = findViewById(R.id.rvNotifications);

        if (rvNotifications != null) {
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            adapter = new NotificationsAdapter();
            rvNotifications.setAdapter(adapter);

            // Enable swipe to dismiss on either LEFT or RIGHT direction
            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    int pos = viewHolder.getBindingAdapterPosition();
                    if (pos >= 0 && pos < notificationList.size()) {
                        JSONObject item = notificationList.get(pos);
                        String notifId = item.optString("id", "");
                        deleteNotificationDirect(notifId, pos);
                    }
                }
            });
            itemTouchHelper.attachToRecyclerView(rvNotifications);
        }

        View btnBack = findViewById(R.id.btnBackNotifications);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnClearAllNotifications != null) {
            btnClearAllNotifications.setOnClickListener(v -> confirmClearAll());
        }

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(Color.parseColor("#BA5536"));
            swipeRefresh.setOnRefreshListener(this::loadAllNotifications);
        }

        loadAllNotifications();
    }

    private void loadAllNotifications() {
        if (currentUserId == null || currentUserId.isEmpty()) return;

        executor.execute(() -> {
            loadChatRequests();
            loadGeneralNotifications();
        });
    }

    private void loadChatRequests() {
        try {
            String endpoint = "/rest/v1/chat_requests?select=*&receiver_id=eq." + SupabaseHelper.encode(currentUserId)
                    + "&status=eq.pending&order=created_at.desc";

            HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "GET", token);
            int code = conn.getResponseCode();
            String res = SupabaseHelper.readResponse(conn);
            conn.disconnect();

            if (code >= 200 && code < 300) {
                JSONArray requests = new JSONArray(res);
                runOnUiThread(() -> renderChatRequests(requests));
            }
        } catch (Exception ignored) {}
    }

    private void renderChatRequests(JSONArray requests) {
        if (containerChatRequests == null) return;
        containerChatRequests.removeAllViews();

        if (requests == null || requests.length() == 0) {
            if (tvChatRequestsHeader != null) tvChatRequestsHeader.setVisibility(View.GONE);
            return;
        }

        if (tvChatRequestsHeader != null) tvChatRequestsHeader.setVisibility(View.VISIBLE);

        for (int i = 0; i < requests.length(); i++) {
            JSONObject req = requests.optJSONObject(i);
            if (req == null) continue;

            String reqId = req.optString("id", "");
            String senderId = req.optString("sender_id", "");
            String senderName = req.optString("sender_name", "Resident");
            String senderRole = req.optString("sender_role", "Resident");

            CardView card = createCardWrapper();
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(dp(16), dp(16), dp(16), dp(16));

            TextView tvTitle = new TextView(this);
            tvTitle.setText(senderName + " (" + senderRole + ") wants to start a chat with you.");
            tvTitle.setTextColor(Color.parseColor("#1F1614"));
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            inner.addView(tvTitle);

            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setPadding(0, dp(12), 0, 0);

            Button btnAccept = new Button(this);
            btnAccept.setText("Accept");
            btnAccept.setTextColor(Color.WHITE);
            btnAccept.setBackgroundColor(Color.parseColor("#BA5536"));
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(42), 1f);
            p1.setMarginEnd(dp(8));
            btnAccept.setLayoutParams(p1);

            Button btnDecline = new Button(this);
            btnDecline.setText("Decline");
            btnDecline.setTextColor(Color.WHITE);
            btnDecline.setBackgroundColor(Color.parseColor("#8C7A70"));
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
            btnDecline.setLayoutParams(p2);

            btnAccept.setOnClickListener(v -> respondChatRequest(reqId, senderId, senderName, true));
            btnDecline.setOnClickListener(v -> respondChatRequest(reqId, senderId, senderName, false));

            actionRow.addView(btnAccept);
            actionRow.addView(btnDecline);
            inner.addView(actionRow);

            card.addView(inner);
            containerChatRequests.addView(card);
        }
    }

    private void respondChatRequest(String reqId, String senderId, String senderName, boolean accept) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/chat_requests?id=eq." + SupabaseHelper.encode(reqId);
                JSONObject body = new JSONObject();
                body.put("status", accept ? "accepted" : "declined");

                SupabaseHelper.patch(endpoint, body, token);

                runOnUiThread(() -> {
                    Toast.makeText(this, accept ? "Chat accepted!" : "Chat declined", Toast.LENGTH_SHORT).show();
                    loadAllNotifications();

                    if (accept) {
                        Intent chatIntent = new Intent(this, ChatActivity.class);
                        chatIntent.putExtra("RECEIVER_ID", senderId);
                        chatIntent.putExtra("RECEIVER_NAME", senderName);
                        startActivity(chatIntent);
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    private void loadGeneralNotifications() {
        try {
            String endpoint = "/rest/v1/notifications?select=*&user_uid=eq." + SupabaseHelper.encode(currentUserId)
                    + "&order=created_at.desc&limit=50";

            HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "GET", token);
            int code = conn.getResponseCode();
            String res = SupabaseHelper.readResponse(conn);
            conn.disconnect();

            if (code >= 200 && code < 300) {
                JSONArray arr = new JSONArray(res);
                List<JSONObject> tempList = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) tempList.add(obj);
                }

                runOnUiThread(() -> {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    notificationList.clear();
                    notificationList.addAll(tempList);

                    if (adapter != null) adapter.notifyDataSetChanged();

                    if (notificationList.isEmpty()) {
                        if (tvNoNotifications != null) tvNoNotifications.setVisibility(View.VISIBLE);
                        if (btnClearAllNotifications != null) btnClearAllNotifications.setVisibility(View.GONE);
                    } else {
                        if (tvNoNotifications != null) tvNoNotifications.setVisibility(View.GONE);
                        if (btnClearAllNotifications != null) btnClearAllNotifications.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                runOnUiThread(() -> {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                });
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            });
        }
    }

    private void deleteNotificationDirect(String notifId, int position) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/notifications?id=eq." + SupabaseHelper.encode(notifId);
                HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "DELETE", token);
                conn.setRequestProperty("Prefer", "return=representation");
                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        if (position >= 0 && position < notificationList.size()) {
                            notificationList.remove(position);
                            adapter.notifyItemRemoved(position);
                        }
                        Toast.makeText(this, "Notification dismissed", Toast.LENGTH_SHORT).show();
                        if (notificationList.isEmpty() && tvNoNotifications != null) {
                            tvNoNotifications.setVisibility(View.VISIBLE);
                            if (btnClearAllNotifications != null) btnClearAllNotifications.setVisibility(View.GONE);
                        }
                    } else {
                        adapter.notifyItemChanged(position); // Restore row if failed
                        Toast.makeText(this, "Failed to dismiss: HTTP " + code, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    adapter.notifyItemChanged(position);
                    Toast.makeText(this, "Error deleting: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Notifications")
                .setMessage("Are you sure you want to dismiss all notifications?")
                .setPositiveButton("Clear All", (d, w) -> {
                    executor.execute(() -> {
                        try {
                            String endpoint = "/rest/v1/notifications?user_uid=eq." + SupabaseHelper.encode(currentUserId);
                            HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "DELETE", token);
                            int code = conn.getResponseCode();
                            conn.disconnect();

                            runOnUiThread(() -> {
                                if (code >= 200 && code < 300) {
                                    notificationList.clear();
                                    adapter.notifyDataSetChanged();
                                    if (tvNoNotifications != null) tvNoNotifications.setVisibility(View.VISIBLE);
                                    if (btnClearAllNotifications != null) btnClearAllNotifications.setVisibility(View.GONE);
                                    Toast.makeText(this, "All notifications cleared.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Failed to clear all: HTTP " + code, Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleNotificationDeepLink(String title, String message) {
        String combined = (title + " " + message).toLowerCase();

        if ("admin".equalsIgnoreCase(userRole)) {
            if (combined.contains("listing") || combined.contains("edit") || combined.contains("submitted") || combined.contains("risk")) {
                startActivity(new Intent(this, AdminPendingQueueActivity.class));
                return;
            } else if (combined.contains("report") || combined.contains("ticket")) {
                startActivity(new Intent(this, AdminReportsActivity.class));
                return;
            }
        }

        if ("landlord".equalsIgnoreCase(userRole)) {
            if (combined.contains("suspended") || combined.contains("re-verification") || combined.contains("action required")) {
                Intent intent = new Intent(this, MyListingsActivity.class);
                intent.putExtra("FILTER_STATUS", "PENDING");
                startActivity(intent);
                return;
            } else if (combined.contains("approved") && combined.contains("listing")) {
                Intent intent = new Intent(this, MyListingsActivity.class);
                intent.putExtra("FILTER_STATUS", "APPROVED");
                startActivity(intent);
                return;
            } else if (combined.contains("enquiry") || combined.contains("contact")) {
                startActivity(new Intent(this, EnquiriesActivity.class));
                return;
            } else if (combined.contains("application") || combined.contains("lease")) {
                Intent intent = new Intent(this, ApplicationsActivity.class);
                intent.putExtra("isLandlord", true);
                startActivity(intent);
                return;
            } else if (combined.contains("viewing")) {
                startActivity(new Intent(this, LandlordViewingsActivity.class));
                return;
            }
        }

        if (combined.contains("viewing")) {
            startActivity(new Intent(this, ViewingsActivity.class));
        } else if (combined.contains("application")) {
            Intent intent = new Intent(this, ApplicationsActivity.class);
            intent.putExtra("isLandlord", false);
            startActivity(intent);
        } else if (combined.contains("chat") || combined.contains("message")) {
            startActivity(new Intent(this, UserSelectionActivity.class));
        } else {
            Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
        }
    }

    private class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CardView card = createCardWrapper();
            LinearLayout inner = new LinearLayout(NotificationsActivity.this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            inner.setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout textCol = new LinearLayout(NotificationsActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(NotificationsActivity.this);
            tvTitle.setId(View.generateViewId());
            tvTitle.setTextColor(Color.parseColor("#1F1614"));
            tvTitle.setTextSize(15f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvTitle);

            TextView tvMsg = new TextView(NotificationsActivity.this);
            tvMsg.setId(View.generateViewId());
            tvMsg.setTextColor(Color.parseColor("#6C757D"));
            tvMsg.setTextSize(13f);
            tvMsg.setPadding(0, dp(4), 0, dp(4));
            textCol.addView(tvMsg);

            inner.addView(textCol);

            // '✕' Close Button instead of trash icon
            TextView btnClose = new TextView(NotificationsActivity.this);
            btnClose.setId(View.generateViewId());
            btnClose.setText("✕");
            btnClose.setTextSize(18f);
            btnClose.setTypeface(null, Typeface.BOLD);
            btnClose.setTextColor(Color.parseColor("#A09893"));
            btnClose.setPadding(dp(12), dp(8), dp(8), dp(8));
            inner.addView(btnClose);

            card.addView(inner);
            return new ViewHolder(card, tvTitle, tvMsg, btnClose);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject notif = notificationList.get(position);
            String title = notif.optString("title", "Notification");
            String message = notif.optString("message", "");
            String notifId = notif.optString("id", "");

            holder.tvTitle.setText(title);
            holder.tvMsg.setText(message);

            holder.itemView.setOnClickListener(v -> handleNotificationDeepLink(title, message));

            holder.btnClose.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                deleteNotificationDirect(notifId, pos);
            });
        }

        @Override
        public int getItemCount() {
            return notificationList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMsg, btnClose;

            public ViewHolder(@NonNull View itemView, TextView tvTitle, TextView tvMsg, TextView btnClose) {
                super(itemView);
                this.tvTitle = tvTitle;
                this.tvMsg = tvMsg;
                this.btnClose = btnClose;
            }
        }
    }

    private CardView createCardWrapper() {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
