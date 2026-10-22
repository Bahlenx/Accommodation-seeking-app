
package com.example.ikhaya_accommodation_seeking_app;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
    private android.view.View btnSend;
    private TextView txtCharCount;
    private android.view.View btnAttachFile;
    private android.view.View btnClearChat;
    private android.view.View btnChatOptions;
    
    private androidx.activity.result.ActivityResultLauncher<Intent> filePickerLauncher;

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
        txtCharCount = findViewById(R.id.txtCharCount);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        btnClearChat = findViewById(R.id.btnClearChat);
        btnChatOptions = findViewById(R.id.btnChatOptions);

        android.view.View btnBack = findViewById(R.id.btnBackChat);
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

        if (editMessage != null && txtCharCount != null) {
            editMessage.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    txtCharCount.setText(s.length() + "/1000");
                }
            });
        }
        
        if (isCommunity) {
            if (btnChatOptions != null) btnChatOptions.setVisibility(View.GONE);
            if (btnClearChat != null) btnClearChat.setVisibility(View.GONE);
        } else {
            if (btnClearChat != null) btnClearChat.setVisibility(View.GONE); // Use options menu instead
            if (btnChatOptions != null) {
                btnChatOptions.setOnClickListener(v -> {
                    android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
                    popup.getMenu().add("Delete Chat Messages");
                    popup.getMenu().add("Delete Contact & Chat");
                    popup.setOnMenuItemClickListener(item -> {
                        if ("Delete Chat Messages".equals(item.getTitle())) {
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Delete Messages")
                                    .setMessage("Delete all messages with this user?")
                                    .setPositiveButton("Delete", (dialog, which) -> deleteChatMessagesOnly())
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else if ("Delete Contact & Chat".equals(item.getTitle())) {
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Delete Contact")
                                    .setMessage("Delete this contact and all chat history?")
                                    .setPositiveButton("Delete", (dialog, which) -> deleteContactAndChat())
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                        return true;
                    });
                    popup.show();
                });
            }
        }
        
        filePickerLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleFileSelection(result.getData().getData());
                    }
                }
        );
        
        if (btnAttachFile != null) {
            btnAttachFile.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Attach File")
                        .setItems(new String[]{"Send Picture", "Send PDF Document"}, (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                            if (which == 0) {
                                intent.setType("image/*");
                            } else {
                                intent.setType("application/pdf");
                            }
                            filePickerLauncher.launch(intent);
                        })
                        .show();
            });
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
                            Log.e("ChatActivity", "Fetch messages HTTP " + code + ": " + response);
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
        String lastDateLabel = "";

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);

            if (message == null) {
                continue;
            }

            String messageId = message.optString("id", "");

            String messageSenderId =
                    message.optString("sender_id", "");

            String messageSenderName =
                    message.optString("sender_name", "User");

            String messageSenderRole =
                    message.optString("sender_role", "Member");

            String messageText =
                    message.optString("message_text", "");
                    
            String createdAt = 
                    message.optString("created_at", "");

            String timeFormatted = "";
            String dateLabel = "";

            if (!createdAt.isEmpty()) {
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(createdAt);
                    ZonedDateTime localZdt = odt.atZoneSameInstant(ZoneId.systemDefault());
                    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

                    timeFormatted = localZdt.format(DateTimeFormatter.ofPattern("HH:mm"));

                    if (localZdt.toLocalDate().equals(now.toLocalDate())) {
                        dateLabel = "Today";
                    } else if (localZdt.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                        dateLabel = "Yesterday";
                    } else {
                        dateLabel = localZdt.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.getDefault()));
                    }
                } catch (Exception e) {
                    timeFormatted = "";
                    dateLabel = "";
                }
            }

            if (!dateLabel.isEmpty() && !dateLabel.equals(lastDateLabel)) {
                addDatePill(dateLabel);
                lastDateLabel = dateLabel;
            }

            boolean isMe = messageSenderId.equals(senderId);

            addMessageBubble(
                    messageId,
                    messageSenderName,
                    messageSenderRole,
                    messageText,
                    timeFormatted,
                    isMe
            );
        }

        if (chatScrollView != null) {
            chatScrollView.post(() ->
                    chatScrollView.scrollTo(0, messagesContainer.getBottom())
            );
        }
    }

    private void addDatePill(String dateText) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(0, dpToPx(8), 0, dpToPx(8));

        CardView pill = new CardView(this);
        pill.setCardBackgroundColor(Color.parseColor("#E9ECEF"));
        pill.setRadius(dpToPx(12));
        pill.setCardElevation(0f);

        TextView text = new TextView(this);
        text.setText(dateText);
        text.setTextColor(Color.parseColor("#495057"));
        text.setTextSize(11f);
        text.setTypeface(null, Typeface.BOLD);
        text.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));

        pill.addView(text);
        wrapper.addView(pill);
        messagesContainer.addView(wrapper);
    }

    private void addMessageBubble(
            String messageId,
            String authorName,
            String authorRole,
            String messageText,
            String timeFormatted,
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

        if (messageText.startsWith("[IMAGE_BASE64]:")) {
            String base64Image = messageText.substring("[IMAGE_BASE64]:".length());
            try {
                byte[] decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ImageView imageView = new ImageView(this);
                imageView.setImageBitmap(decodedByte);
                imageView.setAdjustViewBounds(true);
                imageView.setMaxWidth(dpToPx(220));
                
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                imageView.setLayoutParams(imgParams);
                content.addView(imageView);
            } catch (Exception e) {
                TextView txtMessage = new TextView(this);
                txtMessage.setText("[Image missing]");
                content.addView(txtMessage);
            }
        } else if (messageText.startsWith("[PDF_FILE]:")) {
            String[] parts = messageText.substring("[PDF_FILE]:".length()).split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : "Document.pdf";
            String base64Data = parts.length > 1 ? parts[1] : "";
            
            TextView txtMessage = new TextView(this);
            txtMessage.setText("📄 PDF: " + fileName + "\n(Tap to open)");
            txtMessage.setTextSize(14f);
            txtMessage.setTextColor(Color.parseColor("#0056b3"));
            txtMessage.setTypeface(null, Typeface.ITALIC);
            
            txtMessage.setOnClickListener(v -> {
                // To keep it simple without FileProvider setup, just toast or save
                Toast.makeText(this, "Opening PDF: " + fileName, Toast.LENGTH_SHORT).show();
            });
            content.addView(txtMessage);
        } else {
            TextView txtMessage = new TextView(this);
            
            int maxBubbleWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            txtMessage.setMaxWidth(maxBubbleWidth);

            txtMessage.setText(messageText);
            txtMessage.setTextSize(15f);

            txtMessage.setTextColor(
                    Color.parseColor("#212529")
            );

            content.addView(txtMessage);
        }

        if (!timeFormatted.isEmpty()) {
            TextView txtTime = new TextView(this);
            txtTime.setText(timeFormatted);
            txtTime.setTextSize(11f);
            txtTime.setTextColor(Color.parseColor(isMe ? "#5C5F66" : "#868E96"));
            
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            timeParams.gravity = Gravity.END;
            timeParams.topMargin = dpToPx(4);
            txtTime.setLayoutParams(timeParams);
            
            content.addView(txtTime);
        }
        
        bubble.addView(content);
        
        if (!isCommunity || isMe) {
            bubble.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete Message")
                        .setMessage("Delete this message?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            executor.execute(() -> {
                                try {
                                    HttpURLConnection conn = SupabaseHelper.createConnection("/rest/v1/messages?id=eq." + SupabaseHelper.encode(messageId), "DELETE", accessToken);
                                    int code = conn.getResponseCode();
                                    conn.disconnect();
                                    if (code >= 200 && code < 300) {
                                        runOnUiThread(this::fetchMessages);
                                    }
                                } catch (Exception ignored) {}
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }
        
        wrapper.addView(bubble);
        messagesContainer.addView(wrapper);
    }

    private void deleteChatMessagesOnly() {
        if (senderId == null || receiverId == null) return;
        executor.execute(() -> {
            try {
                String encodedSender = SupabaseHelper.encode(senderId);
                String encodedReceiver = SupabaseHelper.encode(receiverId);
                String endpoint = "/rest/v1/messages?or=(and(sender_id.eq." + encodedSender + ",receiver_id.eq." + encodedReceiver + "),and(sender_id.eq." + encodedReceiver + ",receiver_id.eq." + encodedSender + "))";
                HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "DELETE", accessToken);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code >= 200 && code < 300) {
                    lastMessagesSnapshot = "";
                    runOnUiThread(this::fetchMessages);
                }
            } catch (Exception e) {
                Log.e("ChatActivity", "Failed to delete messages", e);
            }
        });
    }

    private void deleteContactAndChat() {
        if (senderId == null || receiverId == null) return;
        executor.execute(() -> {
            try {
                String encodedSender = SupabaseHelper.encode(senderId);
                String encodedReceiver = SupabaseHelper.encode(receiverId);
                
                // Delete messages
                String endpointMsg = "/rest/v1/messages?or=(and(sender_id.eq." + encodedSender + ",receiver_id.eq." + encodedReceiver + "),and(sender_id.eq." + encodedReceiver + ",receiver_id.eq." + encodedSender + "))";
                HttpURLConnection connMsg = SupabaseHelper.createConnection(endpointMsg, "DELETE", accessToken);
                connMsg.getResponseCode();
                connMsg.disconnect();
                
                // Delete chat request
                String endpointReq = "/rest/v1/chat_requests?or=(and(sender_id.eq." + encodedSender + ",receiver_id.eq." + encodedReceiver + "),and(sender_id.eq." + encodedReceiver + ",receiver_id.eq." + encodedSender + "))";
                HttpURLConnection connReq = SupabaseHelper.createConnection(endpointReq, "DELETE", accessToken);
                int code = connReq.getResponseCode();
                connReq.disconnect();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e("ChatActivity", "Failed to delete contact", e);
            }
        });
    }

    private void handleFileSelection(android.net.Uri uri) {
        if (uri == null) return;
        
        executor.execute(() -> {
            try {
                String mimeType = getContentResolver().getType(uri);
                if (mimeType != null && mimeType.startsWith("image/")) {
                    // Handle Image
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap originalBitmap = BitmapFactory.decodeStream(is);
                    if (originalBitmap != null) {
                        // Scale down to max 480px width
                        int width = originalBitmap.getWidth();
                        int height = originalBitmap.getHeight();
                        if (width > 480) {
                            height = (int) (height * (480.0f / width));
                            width = 480;
                        }
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);
                        
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos);
                        byte[] imageBytes = baos.toByteArray();
                        String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
                        
                        String payload = "[IMAGE_BASE64]:" + base64Image;
                        runOnUiThread(() -> sendPayload(payload));
                    }
                } else if (mimeType != null && mimeType.equals("application/pdf")) {
                    // Handle PDF (Using Base64 for simplicity, but ideally should upload to Storage)
                    String fileName = "Document.pdf";
                    android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex);
                        }
                        cursor.close();
                    }
                    
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    byte[] pdfBytes = baos.toByteArray();
                    String base64Pdf = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.NO_WRAP);
                    
                    String payload = "[PDF_FILE]:" + fileName + "|" + base64Pdf;
                    runOnUiThread(() -> sendPayload(payload));
                }
            } catch (Exception e) {
                Log.e("ChatActivity", "Error handling file", e);
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Failed to attach file", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendMessage() {
        if (sendingMessage || editMessage == null) {
            return;
        }

        String messageText =
                editMessage.getText().toString(); // remove the trim here since trailing newlines might be intentional during composing, but generally trim is okay

        if (messageText.trim().isEmpty()) {
            return;
        }

        sendPayload(messageText);
    }
    
    private void sendPayload(String payloadText) {
        if (senderId == null || senderId.trim().isEmpty()) {
            senderId = SupabaseHelper.getUserId(this);
        }

        if (senderName == null || senderName.trim().isEmpty() || "iKhaya User".equals(senderName)) {
            senderName = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_NAME", "User");
        }

        if (senderRole == null || senderRole.trim().isEmpty() || "Member".equals(senderRole)) {
            senderRole = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_ROLE", "tenant");
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
                body.put("message_text", payloadText);

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                success = code >= 200 && code < 300;

                if (!success) {
                    error = "Send failed (HTTP "
                            + code + "): " + response;
                    Log.e("ChatActivity", error);
                }

            } catch (Exception e) {
                error = "Error sending message: "
                        + e.getMessage();
                Log.e("ChatActivity", error);

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
                    if (payloadText.equals(
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
