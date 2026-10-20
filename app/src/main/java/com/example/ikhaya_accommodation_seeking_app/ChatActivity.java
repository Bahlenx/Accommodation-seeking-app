
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
    private Button btnSend;

    private String senderId;
    private String senderName;
    private String senderRole;
    private String receiverId;
    private String accessToken;

    private boolean isCommunity;
    private boolean fetchingMessages;
    private boolean sendingMessage;

    private String lastMessagesSnapshot = "";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler pollHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMessages();
            pollHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messagesContainer = findViewById(R.id.messagesContainer);
        chatScrollView = findViewById(R.id.chatScrollView);
        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSendMessage);

        Button btnBack = findViewById(R.id.btnBackChat);
        TextView txtTitle = findViewById(R.id.txtChatTitle);
        TextView txtSubtitle = findViewById(R.id.txtChatSubtitle);

        senderId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        senderName = getIntent().getStringExtra("SENDER_NAME");
        senderRole = getIntent().getStringExtra("SENDER_ROLE");
        receiverId = getIntent().getStringExtra("RECEIVER_ID");

        String receiverName =
                getIntent().getStringExtra("RECEIVER_NAME");

        isCommunity = getIntent().getBooleanExtra(
                "IS_COMMUNITY",
                false
        );

        if (senderName == null || senderName.trim().isEmpty()) {
            senderName = "iKhaya User";
        }

        if (senderRole == null || senderRole.trim().isEmpty()) {
            senderRole = "Member";
        }

        if (isCommunity) {
            receiverId = "COMMUNITY";
        }

        if (txtTitle != null) {
            txtTitle.setText(
                    receiverName != null
                            ? receiverName
                            : "Chat"
            );
        }

        if (txtSubtitle != null) {
            txtSubtitle.setText(
                    isCommunity
                            ? "Public Community Room (All Users)"
                            : "Direct Message"
            );
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendMessage());
        }

        UserProfileLoader.load(this, (name, email, phone) -> {
            if (!name.isEmpty()) {
                senderName = name;
            }
        });

        if (senderId == null || senderId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()
                || receiverId == null || receiverId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Please sign in to use chat.",
                    Toast.LENGTH_LONG
            ).show();

            if (btnSend != null) {
                btnSend.setEnabled(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (senderId != null && !senderId.isEmpty()
                && receiverId != null && !receiverId.isEmpty()
                && accessToken != null && !accessToken.isEmpty()) {

            pollHandler.removeCallbacks(pollRunnable);
            pollHandler.post(pollRunnable);
        }
    }

    @Override
    protected void onPause() {
        pollHandler.removeCallbacks(pollRunnable);
        super.onPause();
    }

    private void fetchMessages() {
        if (fetchingMessages || executor.isShutdown()) {
            return;
        }

        fetchingMessages = true;

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint;

                if (isCommunity) {
                    endpoint =
                            "/rest/v1/messages?receiver_id=eq.COMMUNITY"
                                    + "&order=created_at.asc&limit=150";
                } else {
                    String encodedSender =
                            SupabaseHelper.encode(senderId);

                    String encodedReceiver =
                            SupabaseHelper.encode(receiverId);

                    endpoint =
                            "/rest/v1/messages?or=(and(sender_id.eq."
                                    + encodedSender
                                    + ",receiver_id.eq."
                                    + encodedReceiver
                                    + "),and(sender_id.eq."
                                    + encodedReceiver
                                    + ",receiver_id.eq."
                                    + encodedSender
                                    + "))&order=created_at.asc&limit=150";
                }

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int code = connection.getResponseCode();
                String response =
                        SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    JSONArray messages = new JSONArray(response);
                    String snapshot = messages.toString();

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            if (!snapshot.equals(lastMessagesSnapshot)) {
                                lastMessagesSnapshot = snapshot;
                                renderMessages(messages);
                            }
                        }
                    });

                } else {
                    runOnUiThread(() -> {
                        pollHandler.removeCallbacks(pollRunnable);

                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(
                                    this,
                                    "Failed to load messages (HTTP "
                                            + code + "): " + response,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }

            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                fetchingMessages = false;
            }
        });
    }

    private void renderMessages(JSONArray messages) {
        if (messagesContainer == null) {
            return;
        }

        messagesContainer.removeAllViews();

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);

            if (message == null) {
                continue;
            }

            String messageSenderId =
                    message.optString("sender_id", "");

            String messageSenderName =
                    message.optString("sender_name", "User");

            String messageSenderRole =
                    message.optString("sender_role", "Member");

            String messageText =
                    message.optString("message_text", "");

            boolean isMe = messageSenderId.equals(senderId);

            addMessageBubble(
                    messageSenderName,
                    messageSenderRole,
                    messageText,
                    isMe
            );
        }

        if (chatScrollView != null) {
            chatScrollView.post(() ->
                    chatScrollView.fullScroll(View.FOCUS_DOWN)
            );
        }
    }

    private void addMessageBubble(
            String authorName,
            String authorRole,
            String messageText,
            boolean isMe
    ) {
        LinearLayout wrapper = new LinearLayout(this);

        wrapper.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isMe ? Gravity.END : Gravity.START);

        CardView bubble = new CardView(this);

        LinearLayout.LayoutParams bubbleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        int horizontalMargin = dpToPx(40);

        bubbleParams.setMargins(
                isMe ? horizontalMargin : 0,
                dpToPx(4),
                isMe ? 0 : horizontalMargin,
                dpToPx(8)
        );

        bubble.setLayoutParams(bubbleParams);
        bubble.setRadius(dpToPx(15));
        bubble.setCardElevation(dpToPx(2));

        bubble.setCardBackgroundColor(
                Color.parseColor(
                        isMe ? "#FFE8CC" : "#FFFFFF"
                )
        );

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.setPadding(
                dpToPx(16),
                dpToPx(10),
                dpToPx(16),
                dpToPx(10)
        );

        if (!isMe && isCommunity) {
            TextView txtAuthor = new TextView(this);

            txtAuthor.setText(
                    authorName + " • "
                            + authorRole.toUpperCase()
            );

            txtAuthor.setTextSize(12f);
            txtAuthor.setTypeface(null, Typeface.BOLD);

            txtAuthor.setTextColor(
                    Color.parseColor("#D9480F")
            );

            content.addView(txtAuthor);
        }

        TextView txtMessage = new TextView(this);

        txtMessage.setText(messageText);
        txtMessage.setTextSize(15f);

        txtMessage.setTextColor(
                Color.parseColor("#212529")
        );

        content.addView(txtMessage);
        bubble.addView(content);
        wrapper.addView(bubble);
        messagesContainer.addView(wrapper);
    }

    private void sendMessage() {
        if (sendingMessage || editMessage == null) {
            return;
        }

        String messageText =
                editMessage.getText().toString().trim();

        if (messageText.isEmpty()) {
            return;
        }

        if (senderId == null || senderId.isEmpty()
                || receiverId == null || receiverId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please sign in to send messages.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        sendingMessage = true;

        if (btnSend != null) {
            btnSend.setEnabled(false);
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            boolean success = false;
            String error = "";

            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/messages",
                        "POST",
                        accessToken
                );

                connection.setRequestProperty(
                        "Prefer",
                        "return=minimal"
                );

                JSONObject body = new JSONObject();

                body.put("sender_id", senderId);
                body.put("sender_name", senderName);
                body.put("sender_role", senderRole);
                body.put("receiver_id", receiverId);
                body.put("message_text", messageText);

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                success = code >= 200 && code < 300;

                if (!success) {
                    error = "Send failed (HTTP "
                            + code + "): " + response;
                }

            } catch (Exception e) {
                error = "Error sending message: "
                        + e.getMessage();

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            boolean sent = success;
            String failureMessage = error;

            runOnUiThread(() -> {
                sendingMessage = false;

                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (btnSend != null) {
                    btnSend.setEnabled(true);
                }

                if (sent) {
                    if (messageText.equals(
                            editMessage.getText().toString().trim()
                    )) {
                        editMessage.setText("");
                    }

                    fetchMessages();
                } else {
                    Toast.makeText(
                            this,
                            failureMessage,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });
    }

    private int dpToPx(int dp) {
        return Math.round(
                dp * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onDestroy() {
        pollHandler.removeCallbacks(pollRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }
}
