package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    private LinearLayout messagesContainer;
    private ScrollView chatScrollView;
    private EditText editMessage;
    private View btnSend;
    private TextView txtCharCount;
    private View btnAttachFile;
    private View btnClearChat;
    private View btnChatOptions;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    private String senderId;
    private String senderName;
    private String senderRole;
    private String receiverId;
    private String accessToken;

    private boolean isCommunity;
    private boolean fetchingMessages;
    private boolean sendingMessage;

    private String lastMessagesSnapshot = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Handler pollHandler = new Handler(Looper.getMainLooper());

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

        View btnBack = findViewById(R.id.btnBackChat);
        TextView txtTitle = findViewById(R.id.txtChatTitle);
        TextView txtSubtitle = findViewById(R.id.txtChatSubtitle);

        senderId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        senderName = getIntent().getStringExtra("SENDER_NAME");
        senderRole = getIntent().getStringExtra("SENDER_ROLE");
        receiverId = getIntent().getStringExtra("RECEIVER_ID");

        String receiverName = getIntent().getStringExtra("RECEIVER_NAME");
        isCommunity = getIntent().getBooleanExtra("IS_COMMUNITY", false);

        if (senderName == null || senderName.trim().isEmpty()) {
            senderName = getString(R.string.default_chat_user_name);
        }

        if (senderRole == null || senderRole.trim().isEmpty()) {
            senderRole = getString(R.string.default_chat_member_role);
        }

        if (isCommunity) {
            receiverId = "COMMUNITY";
        }

        if (txtTitle != null) {
            txtTitle.setText(receiverName != null ? receiverName : getString(R.string.title_chat_fallback));
        }

        if (txtSubtitle != null) {
            txtSubtitle.setText(isCommunity
                    ? getString(R.string.subtitle_community_room)
                    : getString(R.string.subtitle_direct_message));
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendMessage());
        }

        if (editMessage != null && txtCharCount != null) {
            editMessage.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    txtCharCount.setText(String.format(Locale.getDefault(), "%d/1000", s.length()));
                }
            });
        }

        if (isCommunity) {
            if (btnChatOptions != null) btnChatOptions.setVisibility(View.GONE);
            if (btnClearChat != null) btnClearChat.setVisibility(View.GONE);
        } else {
            if (btnClearChat != null) btnClearChat.setVisibility(View.GONE);
            if (btnChatOptions != null) {
                btnChatOptions.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(this, v);
                    popup.getMenu().add(getString(R.string.menu_delete_chat_messages));
                    popup.getMenu().add(getString(R.string.menu_delete_contact_and_chat));
                    popup.setOnMenuItemClickListener(item -> {
                        if (getString(R.string.menu_delete_chat_messages).contentEquals(item.getTitle())) {
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.dialog_title_delete_messages)
                                    .setMessage(R.string.dialog_msg_delete_messages_confirm)
                                    .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteChatMessagesOnly())
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        } else if (getString(R.string.menu_delete_contact_and_chat).contentEquals(item.getTitle())) {
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.dialog_title_delete_contact)
                                    .setMessage(R.string.dialog_msg_delete_contact_confirm)
                                    .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteContactAndChat())
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        }
                        return true;
                    });
                    popup.show();
                });
            }
        }

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleFileSelection(result.getData().getData());
                    }
                }
        );

        if (btnAttachFile != null) {
            btnAttachFile.setOnClickListener(v -> {
                String[] attachOptions = {
                        getString(R.string.attach_option_picture),
                        getString(R.string.attach_option_pdf)
                };
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title_attach_file)
                        .setItems(attachOptions, (dialog, which) -> {
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

            Toast.makeText(this, R.string.error_sign_in_for_chat, Toast.LENGTH_LONG).show();
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
                    endpoint = "/rest/v1/messages?receiver_id=eq.COMMUNITY"
                            + "&order=created_at.asc&limit=150";
                } else {
                    String encodedSender = SupabaseHelper.encode(senderId);
                    String encodedReceiver = SupabaseHelper.encode(receiverId);

                    endpoint = "/rest/v1/messages?or=(and(sender_id.eq."
                            + encodedSender
                            + ",receiver_id.eq."
                            + encodedReceiver
                            + "),and(sender_id.eq."
                            + encodedReceiver
                            + ",receiver_id.eq."
                            + encodedSender
                            + "))&order=created_at.asc&limit=150";
                }

                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

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
                            Toast.makeText(this, getString(R.string.error_load_messages_http) + " " + code, Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Fetch messages HTTP " + code + ": " + response);
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

        if (messages.length() == 0) {
            TextView txtEmptyChat = new TextView(this);
            txtEmptyChat.setText(R.string.msg_no_messages_say_hello);
            txtEmptyChat.setTextColor(Color.parseColor("#8C7A70"));
            txtEmptyChat.setTextSize(14f);
            txtEmptyChat.setGravity(Gravity.CENTER);
            txtEmptyChat.setPadding(0, dpToPx(32), 0, dpToPx(32));
            messagesContainer.addView(txtEmptyChat);
            return;
        }

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;

            String messageId = message.optString("id", "");
            String messageSenderId = message.optString("sender_id", "");
            String messageSenderName = message.optString("sender_name", getString(R.string.default_chat_user_name));
            String messageSenderRole = message.optString("sender_role", getString(R.string.default_chat_member_role));
            String messageText = message.optString("message_text", "");
            String createdAt = message.optString("created_at", "");

            String timeFormatted = "";
            String dateLabel = "";

            if (!createdAt.isEmpty()) {
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(createdAt);
                    ZonedDateTime localZdt = odt.atZoneSameInstant(ZoneId.systemDefault());
                    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

                    timeFormatted = localZdt.format(DateTimeFormatter.ofPattern("HH:mm"));

                    if (localZdt.toLocalDate().equals(now.toLocalDate())) {
                        dateLabel = getString(R.string.date_label_today);
                    } else if (localZdt.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                        dateLabel = getString(R.string.date_label_yesterday);
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
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
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
        pill.setCardBackgroundColor(Color.parseColor("#EFEBE4"));
        pill.setRadius(dpToPx(12));
        pill.setCardElevation(0f);

        TextView text = new TextView(this);
        text.setText(dateText);
        text.setTextColor(Color.parseColor("#8C7A70"));
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
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isMe ? Gravity.END : Gravity.START);
        wrapper.setPadding(dpToPx(12), 0, dpToPx(12), 0);

        CardView bubble = new CardView(this);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.setMargins(0, dpToPx(4), 0, dpToPx(8));
        bubble.setLayoutParams(bubbleParams);
        bubble.setRadius(dpToPx(16));
        bubble.setCardElevation(dpToPx(2));
        bubble.setCardBackgroundColor(Color.parseColor(isMe ? "#FFE8CC" : "#FFFFFF"));
        bubble.setPreventCornerOverlap(false);

        LinearLayout content = new LinearLayout(this);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        content.setLayoutParams(contentParams);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        if (!isMe && isCommunity) {
            TextView txtAuthor = new TextView(this);
            txtAuthor.setText(String.format(Locale.getDefault(), "%s • %s", authorName, authorRole.toUpperCase(Locale.getDefault())));
            txtAuthor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            txtAuthor.setTypeface(null, Typeface.BOLD);
            txtAuthor.setTextColor(Color.parseColor("#BA5536"));
            content.addView(txtAuthor);
        }

        if (messageText.startsWith("[IMAGE_BASE64]:")) {
            String base64Image = messageText.substring("[IMAGE_BASE64]:".length());
            try {
                byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
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
                txtMessage.setText(R.string.placeholder_image_missing);
                content.addView(txtMessage);
            }
        } else if (messageText.startsWith("[PDF_FILE]:")) {
            String[] parts = messageText.substring("[PDF_FILE]:".length()).split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : getString(R.string.default_document_filename);
            TextView txtMessage = new TextView(this);
            txtMessage.setText(String.format(Locale.getDefault(), "📄 PDF: %s\n(%s)", fileName, getString(R.string.label_tap_to_open)));
            txtMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            txtMessage.setTextColor(Color.parseColor("#0056b3"));
            txtMessage.setTypeface(null, Typeface.ITALIC);
            txtMessage.setOnClickListener(v ->
                    Toast.makeText(this, getString(R.string.toast_opening_pdf) + " " + fileName, Toast.LENGTH_SHORT).show()
            );
            content.addView(txtMessage);
        } else {
            TextView txtMessage = new TextView(this);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            txtMessage.setLayoutParams(textParams);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxTextWidth = (int) (screenWidth * 0.80f) - dpToPx(32);
            txtMessage.setMaxWidth(maxTextWidth);

            txtMessage.setText(messageText);
            txtMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            txtMessage.setTextColor(Color.parseColor("#1F1614"));
            txtMessage.setLineSpacing(0, 1.2f);
            content.addView(txtMessage);
        }

        TextView txtTime = new TextView(this);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.gravity = Gravity.END;
        txtTime.setLayoutParams(timeParams);
        txtTime.setText(timeFormatted);
        txtTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        txtTime.setTextColor(Color.parseColor(isMe ? "#998A7A" : "#8A8D91"));
        txtTime.setPadding(0, dpToPx(4), 0, 0);
        content.addView(txtTime);

        bubble.addView(content);

        if (!isCommunity || isMe) {
            bubble.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title_delete_single_message)
                        .setMessage(R.string.dialog_msg_delete_single_message)
                        .setPositiveButton(R.string.btn_delete, (dialog, which) -> executor.execute(() -> {
                            try {
                                HttpURLConnection conn = SupabaseHelper.createConnection(
                                        "/rest/v1/messages?id=eq." + SupabaseHelper.encode(messageId),
                                        "DELETE",
                                        accessToken
                                );
                                int code = conn.getResponseCode();
                                conn.disconnect();
                                if (code >= 200 && code < 300) {
                                    runOnUiThread(this::fetchMessages);
                                }
                            } catch (Exception ignored) {}
                        }))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show();
                return true;
            });
        }

        wrapper.addView(bubble);
        messagesContainer.addView(wrapper);

        if (chatScrollView != null) {
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void deleteChatMessagesOnly() {
        if (senderId == null || receiverId == null) return;
        executor.execute(() -> {
            try {
                String encodedSender = SupabaseHelper.encode(senderId);
                String encodedReceiver = SupabaseHelper.encode(receiverId);
                String endpoint = "/rest/v1/messages?or=(and(sender_id.eq." + encodedSender
                        + ",receiver_id.eq." + encodedReceiver
                        + "),and(sender_id.eq." + encodedReceiver
                        + ",receiver_id.eq." + encodedSender + "))";
                HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "DELETE", accessToken);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code >= 200 && code < 300) {
                    lastMessagesSnapshot = "";
                    runOnUiThread(this::fetchMessages);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete messages", e);
            }
        });
    }

    private void deleteContactAndChat() {
        if (senderId == null || receiverId == null) return;
        executor.execute(() -> {
            try {
                String encodedSender = SupabaseHelper.encode(senderId);
                String encodedReceiver = SupabaseHelper.encode(receiverId);

                String endpointMsg = "/rest/v1/messages?or=(and(sender_id.eq." + encodedSender
                        + ",receiver_id.eq." + encodedReceiver
                        + "),and(sender_id.eq." + encodedReceiver
                        + ",receiver_id.eq." + encodedSender + "))";
                HttpURLConnection connMsg = SupabaseHelper.createConnection(endpointMsg, "DELETE", accessToken);
                connMsg.getResponseCode();
                connMsg.disconnect();

                String endpointReq = "/rest/v1/chat_requests?or=(and(sender_id.eq." + encodedSender
                        + ",receiver_id.eq." + encodedReceiver
                        + "),and(sender_id.eq." + encodedReceiver
                        + ",receiver_id.eq." + encodedSender + "))";
                HttpURLConnection connReq = SupabaseHelper.createConnection(endpointReq, "DELETE", accessToken);
                connReq.getResponseCode();
                connReq.disconnect();

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.toast_chat_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete contact", e);
            }
        });
    }

    private void handleFileSelection(Uri uri) {
        if (uri == null) return;

        executor.execute(() -> {
            try {
                String mimeType = getContentResolver().getType(uri);
                if (mimeType != null && mimeType.startsWith("image/")) {
                    InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap originalBitmap = BitmapFactory.decodeStream(is);
                    if (originalBitmap != null) {
                        int width = originalBitmap.getWidth();
                        int height = originalBitmap.getHeight();
                        if (width > 480) {
                            height = (int) (height * (480.0f / width));
                            width = 480;
                        }
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos);
                        byte[] imageBytes = baos.toByteArray();
                        String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                        String payload = "[IMAGE_BASE64]:" + base64Image;
                        runOnUiThread(() -> sendPayload(payload));
                    }
                } else if (mimeType != null && mimeType.equals("application/pdf")) {
                    String fileName = getString(R.string.default_document_filename);
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex);
                        }
                        cursor.close();
                    }

                    InputStream is = getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    byte[] pdfBytes = baos.toByteArray();
                    String base64Pdf = Base64.encodeToString(pdfBytes, Base64.NO_WRAP);

                    String payload = "[PDF_FILE]:" + fileName + "|" + base64Pdf;
                    runOnUiThread(() -> sendPayload(payload));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling file", e);
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, R.string.error_failed_to_attach_file, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendMessage() {
        if (sendingMessage || editMessage == null) {
            return;
        }

        String messageText = editMessage.getText().toString();
        if (messageText.trim().isEmpty()) {
            return;
        }

        sendPayload(messageText);
    }

    private void sendPayload(String payloadText) {
        if (senderId == null || senderId.trim().isEmpty()) {
            senderId = SupabaseHelper.getUserId(this);
        }

        if (senderName == null || senderName.trim().isEmpty() || getString(R.string.default_chat_user_name).equals(senderName)) {
            senderName = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_NAME", getString(R.string.default_chat_user_name));
        }

        if (senderRole == null || senderRole.trim().isEmpty() || getString(R.string.default_chat_member_role).equals(senderRole)) {
            senderRole = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_ROLE", "tenant");
        }

        if (senderId == null || senderId.isEmpty()
                || receiverId == null || receiverId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {

            Toast.makeText(this, R.string.error_sign_in_for_chat, Toast.LENGTH_SHORT).show();
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
                connection = SupabaseHelper.createConnection("/rest/v1/messages", "POST", accessToken);
                connection.setRequestProperty("Prefer", "return=minimal");

                JSONObject body = new JSONObject();
                body.put("sender_id", senderId);
                body.put("sender_name", senderName);
                body.put("sender_role", senderRole);
                body.put("receiver_id", receiverId);
                body.put("message_text", payloadText);

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                success = code >= 200 && code < 300;
                if (!success) {
                    error = getString(R.string.error_send_failed_http) + " " + code + ": " + response;
                    Log.e(TAG, error);
                }

            } catch (Exception e) {
                error = getString(R.string.error_sending_message_prefix) + " " + e.getMessage();
                Log.e(TAG, error);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            boolean sent = success;
            String failureMessage = error;

            runOnUiThread(() -> {
                sendingMessage = false;

                if (isFinishing() || isDestroyed()) return;

                if (btnSend != null) {
                    btnSend.setEnabled(true);
                }

                if (sent) {
                    if (payloadText.equals(editMessage.getText().toString().trim())) {
                        editMessage.setText("");
                    }
                    fetchMessages();
                } else {
                    Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        pollHandler.removeCallbacks(pollRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }
}