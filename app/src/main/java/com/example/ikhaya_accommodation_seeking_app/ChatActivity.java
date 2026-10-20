package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private LinearLayout messagesContainer;
    private ScrollView chatScrollView;
    private EditText editMessage;

    private String senderId;
    private String senderName;
    private String senderRole;
    private String receiverId;
    private boolean isCommunity;
    private String accessToken;

    private int lastMessageCount = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMessages();
            pollHandler.postDelayed(this, 3000); // Refresh every 3 seconds
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messagesContainer = findViewById(R.id.messagesContainer);
        chatScrollView = findViewById(R.id.chatScrollView);
        editMessage = findViewById(R.id.editMessage);
        Button btnSend = findViewById(R.id.btnSendMessage);
        Button btnBack = findViewById(R.id.btnBackChat);
        TextView txtTitle = findViewById(R.id.txtChatTitle);
        TextView txtSubtitle = findViewById(R.id.txtChatSubtitle);

        senderId = getIntent().getStringExtra("SENDER_ID");
        senderName = getIntent().getStringExtra("SENDER_NAME");
        senderRole = getIntent().getStringExtra("SENDER_ROLE");
        if (senderRole == null) senderRole = "Member";
        
        receiverId = getIntent().getStringExtra("RECEIVER_ID");
        String receiverName = getIntent().getStringExtra("RECEIVER_NAME");
        isCommunity = getIntent().getBooleanExtra("IS_COMMUNITY", false);
        accessToken = SupabaseHelper.getAccessToken(this);

        txtTitle.setText(receiverName != null ? receiverName : "Chat");
        if (isCommunity) {
            txtSubtitle.setText("Public Community Room (All Users)");
        } else {
            String roleText = (getIntent().getStringExtra("RECEIVER_ROLE") != null) 
                    ? getIntent().getStringExtra("RECEIVER_ROLE").toUpperCase() : "MEMBER";
            txtSubtitle.setText("Direct Message • " + roleText + " \u25CF");
            txtSubtitle.setTextColor(SupabaseHelper.getRoleTextColor(roleText));
        }

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void fetchMessages() {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint;
                if (isCommunity) {
                    endpoint = "/rest/v1/messages?receiver_id=eq.COMMUNITY&order=created_at.asc&limit=150";
                } else {
                    String encSender = SupabaseHelper.encode(senderId);
                    String encReceiver = SupabaseHelper.encode(receiverId);
                    endpoint = "/rest/v1/messages?or=(and(sender_id.eq." + encSender + ",receiver_id.eq." + encReceiver + "),and(sender_id.eq." + encReceiver + ",receiver_id.eq." + encSender + "))&order=created_at.asc&limit=150";
                }

                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray messages = new JSONArray(response);
                    runOnUiThread(() -> renderMessages(messages));
                } else if (code >= 400) {
                    // Stop polling if there's an RLS issue or table not found
                    pollHandler.removeCallbacks(pollRunnable);
                    runOnUiThread(() -> Toast.makeText(this, "Failed to load messages: " + response, Toast.LENGTH_LONG).show());
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void renderMessages(JSONArray messages) {
        if (messages.length() == 0) {
            messagesContainer.removeAllViews();
            TextView emptyText = new TextView(this);
            emptyText.setText("No messages yet. Say hello! 👋");
            emptyText.setTextColor(Color.parseColor("#6C757D"));
            emptyText.setTextSize(14f);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, 100, 0, 0);
            messagesContainer.addView(emptyText);
            return;
        }

        if (messages.length() == lastMessageCount) return; // Only redraw if new messages arrived
        lastMessageCount = messages.length();
        messagesContainer.removeAllViews();

        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                String msgSenderId = msg.optString("sender_id", "");
                String msgSenderName = msg.optString("sender_name", "User");
                String msgSenderRole = msg.optString("sender_role", "Member");
                String text = msg.optString("message_text", "");
                String createdAt = msg.optString("created_at", "");
                boolean isMe = msgSenderId.equals(senderId);

                // Parse time
                String timeStr = "00:00";
                try {
                    if (createdAt != null && !createdAt.isEmpty()) {
                        // Supabase returns 2026-10-14T19:05:00+00:00 or similar
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        java.util.Date date = sdf.parse(createdAt);
                        
                        java.text.SimpleDateFormat outSdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                        outSdf.setTimeZone(java.util.TimeZone.getDefault());
                        timeStr = outSdf.format(date);
                    }
                } catch (Exception e) {
                    // Ignored, keep default
                }

                addMessageBubble(msgSenderName, msgSenderRole, text, timeStr, isMe);
            }
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addMessageBubble(String authorName, String authorRole, String messageText, String timeStr, boolean isMe) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isMe ? Gravity.END : Gravity.START);

        CardView bubble = new CardView(this);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.setMargins(isMe ? 100 : 0, 8, isMe ? 0 : 100, 16);
        bubble.setLayoutParams(bubbleParams);
        bubble.setRadius(30f);
        bubble.setCardElevation(3f);
        bubble.setCardBackgroundColor(Color.parseColor(isMe ? "#FFE8CC" : "#FFFFFF"));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 20, 32, 20);

        if (!isMe && isCommunity) {
            TextView txtAuthor = new TextView(this);
            txtAuthor.setText(authorName + " • " + authorRole.toUpperCase() + " \u25CF");
            txtAuthor.setTextSize(12f);
            txtAuthor.setTypeface(null, Typeface.BOLD);
            txtAuthor.setTextColor(SupabaseHelper.getRoleTextColor(authorRole));
            content.addView(txtAuthor);
        }

        TextView txtMsg = new TextView(this);
        txtMsg.setText(messageText);
        txtMsg.setTextSize(15f);
        txtMsg.setTextColor(Color.parseColor("#212529"));
        content.addView(txtMsg);

        TextView txtTime = new TextView(this);
        txtTime.setText(timeStr);
        txtTime.setTextSize(11f);
        txtTime.setTextColor(Color.parseColor("#6C757D"));
        txtTime.setGravity(Gravity.END);
        content.addView(txtTime);

        bubble.addView(content);
        wrapper.addView(bubble);
        messagesContainer.addView(wrapper);
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        editMessage.setText("");

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/messages", "POST", accessToken);
                conn.setRequestProperty("Prefer", "return=minimal");

                JSONObject body = new JSONObject();
                body.put("sender_id", senderId);
                body.put("sender_name", senderName);
                body.put("sender_role", senderRole);
                body.put("receiver_id", receiverId);
                body.put("message_text", text);

                SupabaseHelper.writeJson(conn, body);

                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    fetchMessages();
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Send failed (HTTP " + code + "): " + response, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error sending message: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}