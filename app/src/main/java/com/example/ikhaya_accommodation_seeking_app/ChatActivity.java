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
    
    private boolean isGuestSupportChat = false;
    private String supportGuestId = "";

    private String receiverNameField = "";
    
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
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        TextView txtTitle = findViewById(R.id.txtChatTitle);
        TextView txtSubtitle = findViewById(R.id.txtChatSubtitle);

        senderId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        senderName = getIntent().getStringExtra("SENDER_NAME");
        senderRole = getIntent().getStringExtra("SENDER_ROLE");
        receiverId = getIntent().getStringExtra("RECEIVER_ID");

        String receiverName = getIntent().getStringExtra("RECEIVER_NAME");
        receiverNameField = receiverName;
        isCommunity = getIntent().getBooleanExtra("IS_COMMUNITY", false);
        
        boolean isAdminSupportIntent = getIntent().getBooleanExtra("IS_ADMIN_SUPPORT", false);
        isGuestSupportChat = isAdminSupportIntent
                || "ADMIN_SUPPORT".equals(receiverId)
                || (receiverId != null && receiverId.startsWith("guest_"))
                || (senderId != null && senderId.startsWith("guest_"));

        if (isGuestSupportChat) {
            if (receiverId != null && receiverId.startsWith("guest_")) {
                supportGuestId = receiverId;
                senderId = "ADMIN_SUPPORT";
                senderName = "iKhaya Admin Support";
                senderRole = "ADMIN";
                if (txtTitle != null && receiverName != null) {
                    txtTitle.setText(receiverName);
                }
                if (txtSubtitle != null) {
                    txtSubtitle.setText("Guest Support Request");
                }
            } else {
                supportGuestId = getIntent().getStringExtra("GUEST_ID");
                if (supportGuestId == null || supportGuestId.trim().isEmpty()) {
                    supportGuestId = getSharedPreferences("iKhayaSupport", MODE_PRIVATE)
                            .getString("GUEST_SUPPORT_ID", "");
                }
                if (supportGuestId == null || supportGuestId.trim().isEmpty()) {
                    supportGuestId = "guest_" + java.util.UUID.randomUUID().toString();
                    getSharedPreferences("iKhayaSupport", MODE_PRIVATE)
                            .edit()
                            .putString("GUEST_SUPPORT_ID", supportGuestId)
                            .apply();
                }
                senderId = supportGuestId;
                String hint = getIntent().getStringExtra("SUPPORT_USER_HINT");
                if (senderName == null || senderName.trim().isEmpty() || !senderName.startsWith("Guest")) {
                    senderName = (hint != null && !hint.trim().isEmpty())
                            ? "Guest (" + hint.trim() + ")"
                            : "Guest (" + supportGuestId.substring(0, Math.min(12, supportGuestId.length())) + ")";
                }
                senderRole = "GUEST";
                receiverId = "ADMIN_SUPPORT";
                receiverName = "iKhaya Admin Support";

                if (txtTitle != null) txtTitle.setText(receiverName);
                if (txtSubtitle != null) txtSubtitle.setText("Admin Support Team");
            }
        }


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
                    
                    if (isGuestSupportChat && "ADMIN_SUPPORT".equals(senderId)) {
                        popup.getMenu().add("Resolve Support Chat");
                    }
                    
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
                        } else if ("Resolve Support Chat".contentEquals(item.getTitle())) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Resolve Guest Support Chat")
                                    .setMessage("Mark this support request as resolved? This will close the active guest thread and return you to the dashboard.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Mark Resolved", (dialog, which) -> resolveGuestSupportChatInsideChat())
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
            btnAttachFile.setOnClickListener(v -> showStyledAttachmentPickerDialog());
        }

        if (!isGuestSupportChat) {
            UserProfileLoader.load(this, (name, email, phone) -> {
                if (!name.isEmpty()) {
                    senderName = name;
                }
            });
        }

        if (!isGuestSupportChat && (senderId == null || senderId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()
                || receiverId == null || receiverId.trim().isEmpty())) {

            Toast.makeText(this, R.string.error_sign_in_for_chat, Toast.LENGTH_LONG).show();
            if (btnSend != null) {
                btnSend.setEnabled(false);
            }
        }

        if (isGuestSupportChat) {
            if (editMessage != null) {
                editMessage.setEnabled(true);
                editMessage.setFocusable(true);
                editMessage.setFocusableInTouchMode(true);
            }
            if (btnSend != null) {
                btnSend.setEnabled(true);
                btnSend.setAlpha(1.0f);
                btnSend.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showStyledAttachmentPickerDialog() {
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable rootBg = new android.graphics.drawable.GradientDrawable();
        rootBg.setColor(Color.parseColor("#FAF6F2"));
        rootBg.setCornerRadius(18 * density);
        root.setBackground(rootBg);

        TextView badge = new TextView(this);
        badge.setText("SHARE ATTACHMENT");
        badge.setTextColor(Color.parseColor("#B85032"));
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        root.addView(badge);

        TextView title = new TextView(this);
        title.setText("Attach a Photo or Document");
        title.setTextColor(Color.parseColor("#1F1614"));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, Math.round(4 * density), 0, Math.round(4 * density));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose what you would like to send in this chat:");
        subtitle.setTextColor(Color.parseColor("#786F6B"));
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, 0, 0, Math.round(14 * density));
        root.addView(subtitle);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        // Option Card 1: Photo / Image
        LinearLayout photoCard = buildAttachmentOptionCard(
                "IMG", "#FDE8E1", "#B85032",
                "Photo or Picture",
                "Share an image from your gallery (JPG / PNG)"
        );
        photoCard.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            filePickerLauncher.launch(intent);
        });
        root.addView(photoCard);

        // Option Card 2: PDF Document
        LinearLayout pdfCard = buildAttachmentOptionCard(
                "PDF", "#FEE2E2", "#B91C1C",
                "PDF Document",
                "Share a document from your device (PDF)"
        );
        LinearLayout.LayoutParams pdfParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pdfParams.topMargin = Math.round(10 * density);
        pdfCard.setLayoutParams(pdfParams);
        pdfCard.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            filePickerLauncher.launch(intent);
        });
        root.addView(pdfCard);

        dialog.show();
    }

    private LinearLayout buildAttachmentOptionCard(String badgeText, String badgeBgHex, String badgeTextHex, String titleText, String subText) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(14 * density);
        card.setPadding(pad, pad, pad, pad);
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(12 * density);
        bg.setStroke(Math.round(1 * density), Color.parseColor("#E6DDD6"));
        card.setBackground(bg);
        card.setClickable(true);
        card.setFocusable(true);
        
        TextView badge = new TextView(this);
        badge.setText(badgeText);
        badge.setTextColor(Color.parseColor(badgeTextHex));
        badge.setTextSize(12f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bBg = new android.graphics.drawable.GradientDrawable();
        bBg.setColor(Color.parseColor(badgeBgHex));
        bBg.setCornerRadius(8 * density);
        badge.setBackground(bBg);
        LinearLayout.LayoutParams bParams = new LinearLayout.LayoutParams(Math.round(44 * density), Math.round(44 * density));
        bParams.rightMargin = Math.round(12 * density);
        badge.setLayoutParams(bParams);
        card.addView(badge);
        
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        
        TextView t = new TextView(this);
        t.setText(titleText);
        t.setTextColor(Color.parseColor("#1F1614"));
        t.setTextSize(15f);
        t.setTypeface(null, Typeface.BOLD);
        meta.addView(t);
        
        TextView s = new TextView(this);
        s.setText(subText);
        s.setTextColor(Color.parseColor("#786F6B"));
        s.setTextSize(12f);
        meta.addView(s);
        
        card.addView(meta);
        return card;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (senderId != null && !senderId.isEmpty()
                && receiverId != null && !receiverId.isEmpty()
                && (isGuestSupportChat || (accessToken != null && !accessToken.isEmpty()))) {
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
                if (isGuestSupportChat) {
                    String encodedGuestId = java.net.URLEncoder.encode(supportGuestId, "UTF-8");
                    endpoint = "/rest/v1/guest_support_messages?select=*&guest_id=eq." + encodedGuestId + "&order=created_at.asc";
                } else if (isCommunity) {
                    endpoint = "/rest/v1/messages?select=*&receiver_id=eq.COMMUNITY&order=created_at.asc";
                } else {
                    String encodedSender = java.net.URLEncoder.encode(senderId, "UTF-8");
                    String encodedReceiver = java.net.URLEncoder.encode(receiverId, "UTF-8");
                    endpoint = "/rest/v1/messages?select=*&or=(and(sender_id.eq." + encodedSender + ",receiver_id.eq." + encodedReceiver + "),and(sender_id.eq." + encodedReceiver + ",receiver_id.eq." + encodedSender + "))&order=created_at.asc";
                }
                String authToken = ((isGuestSupportChat && senderId != null && senderId.startsWith("guest_")) || accessToken == null || accessToken.isEmpty()) ? null : accessToken;
                connection = SupabaseHelper.createConnection(endpoint, "GET", authToken);
                
                if (isGuestSupportChat && supportGuestId != null) {
                    connection.setRequestProperty("x-guest-id", supportGuestId);
                    if ("ADMIN_SUPPORT".equals(senderId)) {
                        connection.setRequestProperty("x-admin-panel", "true");
                    }
                }

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
                
                androidx.cardview.widget.CardView imgCard = new androidx.cardview.widget.CardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dpToPx(220), LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.topMargin = dpToPx(6);
                imgCard.setLayoutParams(cardParams);
                imgCard.setRadius(dpToPx(12));
                imgCard.setCardElevation(0f);
                imgCard.setCardBackgroundColor(isMe ? Color.parseColor("#9E3F24") : Color.parseColor("#F3EDE7"));

                LinearLayout imgContainer = new LinearLayout(this);
                imgContainer.setOrientation(LinearLayout.VERTICAL);

                ImageView imageView = new ImageView(this);
                imageView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(220), dpToPx(180)));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageBitmap(decodedByte);
                imgContainer.addView(imageView);

                TextView tvImgCaption = new TextView(this);
                tvImgCaption.setText("📷 Photo • Tap to view full screen");
                tvImgCaption.setTextSize(11f);
                tvImgCaption.setTextColor(isMe ? Color.parseColor("#FDE8E1") : Color.parseColor("#6D635E"));
                tvImgCaption.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
                imgContainer.addView(tvImgCaption);

                imgCard.addView(imgContainer);
                imgCard.setOnClickListener(v -> showFullScreenImageDialog(decodedByte));
                content.addView(imgCard);
            } catch (Exception e) {
                TextView txtMessage = new TextView(this);
                txtMessage.setText(R.string.placeholder_image_missing);
                content.addView(txtMessage);
            }
        } else if (messageText.startsWith("[PDF_FILE]:")) {
            String[] parts = messageText.substring("[PDF_FILE]:".length()).split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : getString(R.string.default_document_filename);
            String base64Pdf = parts.length > 1 ? parts[1] : "";
            
            LinearLayout pdfCard = new LinearLayout(this);
            pdfCard.setOrientation(LinearLayout.HORIZONTAL);
            pdfCard.setGravity(Gravity.CENTER_VERTICAL);
            pdfCard.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            LinearLayout.LayoutParams pdfCardParams = new LinearLayout.LayoutParams(dpToPx(240), LinearLayout.LayoutParams.WRAP_CONTENT);
            pdfCardParams.topMargin = dpToPx(6);
            pdfCard.setLayoutParams(pdfCardParams);

            android.graphics.drawable.GradientDrawable pdfCardBg = new android.graphics.drawable.GradientDrawable();
            pdfCardBg.setColor(isMe ? Color.parseColor("#963B20") : Color.parseColor("#F5EFEA"));
            pdfCardBg.setCornerRadius(dpToPx(10));
            pdfCardBg.setStroke(dpToPx(1), isMe ? Color.parseColor("#D47A60") : Color.parseColor("#DFD5CC"));
            pdfCard.setBackground(pdfCardBg);

            TextView pdfBadge = new TextView(this);
            pdfBadge.setText("PDF");
            pdfBadge.setTextColor(Color.WHITE);
            pdfBadge.setTextSize(11f);
            pdfBadge.setTypeface(null, Typeface.BOLD);
            pdfBadge.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
            badgeBg.setColor(Color.parseColor("#DC2626"));
            badgeBg.setCornerRadius(dpToPx(8));
            pdfBadge.setBackground(badgeBg);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
            badgeParams.rightMargin = dpToPx(10);
            pdfBadge.setLayoutParams(badgeParams);
            pdfCard.addView(pdfBadge);

            LinearLayout metaCol = new LinearLayout(this);
            metaCol.setOrientation(LinearLayout.VERTICAL);
            metaCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvFileName = new TextView(this);
            tvFileName.setText(fileName);
            tvFileName.setTextColor(isMe ? Color.WHITE : Color.parseColor("#1F1614"));
            tvFileName.setTextSize(13f);
            tvFileName.setTypeface(null, Typeface.BOLD);
            tvFileName.setSingleLine(true);
            tvFileName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            metaCol.addView(tvFileName);

            int approxKb = Math.max(1, (base64Pdf.length() * 3 / 4) / 1024);
            TextView tvFileSub = new TextView(this);
            tvFileSub.setText(String.format(Locale.getDefault(), "%d KB • Tap to open PDF", approxKb));
            tvFileSub.setTextColor(isMe ? Color.parseColor("#FDE8E1") : Color.parseColor("#786F6B"));
            tvFileSub.setTextSize(11f);
            metaCol.addView(tvFileSub);

            pdfCard.addView(metaCol);
            
            pdfCard.setOnClickListener(v -> openBase64PdfInApp(fileName, base64Pdf));
            
            content.addView(pdfCard);
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

    private void showFullScreenImageDialog(Bitmap bitmap) {
        if (bitmap == null) return;
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView fullScreenImage = new ImageView(this);
        fullScreenImage.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        fullScreenImage.setImageBitmap(bitmap);
        fullScreenImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullScreenImage.setOnClickListener(v -> dialog.dismiss());
        root.addView(fullScreenImage);

        TextView btnClose = new TextView(this);
        btnClose.setText("✕ Close");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(14f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        android.graphics.drawable.GradientDrawable closeBg = new android.graphics.drawable.GradientDrawable();
        closeBg.setColor(Color.parseColor("#99000000"));
        closeBg.setCornerRadius(dpToPx(20));
        btnClose.setBackground(closeBg);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        closeLp.setMargins(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(16));
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose);

        dialog.setContentView(root);
        dialog.show();
    }

    private void openBase64PdfInApp(String fileName, String base64Pdf) {
        if (base64Pdf == null || base64Pdf.isEmpty()) {
            Toast.makeText(this, "PDF content is empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.toast_opening_pdf) + " " + fileName, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                byte[] pdfBytes = Base64.decode(base64Pdf, Base64.DEFAULT);
                java.io.File pdfFile = new java.io.File(getCacheDir(), "chat_doc_" + System.currentTimeMillis() + ".pdf");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pdfFile)) {
                    fos.write(pdfBytes);
                    fos.flush();
                }

                android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(pfd);
                int pageCount = renderer.getPageCount();
                java.util.List<Bitmap> pageBitmaps = new java.util.ArrayList<>();
                int renderWidth = getResources().getDisplayMetrics().widthPixels - dpToPx(24);

                for (int i = 0; i < pageCount; i++) {
                    android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(i);
                    int w = Math.max(1, renderWidth);
                    int h = Math.max(1, (int) ((float) w * page.getHeight() / page.getWidth()));
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    pageBitmaps.add(bmp);
                }
                renderer.close();
                pfd.close();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                    LinearLayout root = new LinearLayout(this);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setBackgroundColor(Color.parseColor("#2B2623"));

                    LinearLayout topBar = new LinearLayout(this);
                    topBar.setOrientation(LinearLayout.HORIZONTAL);
                    topBar.setGravity(Gravity.CENTER_VERTICAL);
                    topBar.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                    topBar.setBackgroundColor(Color.parseColor("#1F1614"));

                    TextView tvTitle = new TextView(this);
                    tvTitle.setText("📄 " + fileName + " (" + pageBitmaps.size() + "p)");
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTextSize(15f);
                    tvTitle.setTypeface(null, Typeface.BOLD);
                    tvTitle.setSingleLine(true);
                    tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    topBar.addView(tvTitle);

                    TextView btnClose = new TextView(this);
                    btnClose.setText("✕ Close");
                    btnClose.setTextColor(Color.WHITE);
                    btnClose.setTextSize(13f);
                    btnClose.setTypeface(null, Typeface.BOLD);
                    btnClose.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
                    android.graphics.drawable.GradientDrawable closeBg = new android.graphics.drawable.GradientDrawable();
                    closeBg.setColor(Color.parseColor("#B85032"));
                    closeBg.setCornerRadius(dpToPx(16));
                    btnClose.setBackground(closeBg);
                    btnClose.setOnClickListener(v -> dialog.dismiss());
                    topBar.addView(btnClose);
                    root.addView(topBar);

                    ScrollView scrollView = new ScrollView(this);
                    scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                    ));
                    LinearLayout pagesContainer = new LinearLayout(this);
                    pagesContainer.setOrientation(LinearLayout.VERTICAL);
                    pagesContainer.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(24));

                    for (Bitmap pageBmp : pageBitmaps) {
                        ImageView pageView = new ImageView(this);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        lp.bottomMargin = dpToPx(12);
                        pageView.setLayoutParams(lp);
                        pageView.setAdjustViewBounds(true);
                        pageView.setImageBitmap(pageBmp);
                        pagesContainer.addView(pageView);
                    }
                    scrollView.addView(pagesContainer);
                    root.addView(scrollView);

                    dialog.setContentView(root);
                    dialog.show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to open PDF", e);
                runOnUiThread(() -> Toast.makeText(this, "Could not render PDF file.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void resolveGuestSupportChatInsideChat() {
        if (supportGuestId == null || supportGuestId.isEmpty() || accessToken == null || accessToken.isEmpty()) return;

        executor.execute(() -> {
            HttpURLConnection delConn = null;
            try {
                try {
                    HttpURLConnection archiveConn = SupabaseHelper.createConnection("/rest/v1/reports", "POST", accessToken);
                    archiveConn.setRequestProperty("Prefer", "return=minimal");
                    JSONObject archiveBody = new JSONObject();
                    archiveBody.put("reporter_uid", supportGuestId);
                    archiveBody.put("reporter_id", supportGuestId);
                    archiveBody.put("reporter_name", receiverNameField != null ? receiverNameField : supportGuestId);
                    archiveBody.put("reporter_role", "GUEST");
                    archiveBody.put("reason", "GUEST CHAT RESOLVED (" + (receiverNameField != null ? receiverNameField : supportGuestId) + ")");
                    archiveBody.put("issue_type", "GUEST CHAT RESOLVED");
                    archiveBody.put("description", "Admin resolved guest support thread manually from inside ChatActivity.");
                    archiveBody.put("status", "RESOLVED");
                    SupabaseHelper.writeJson(archiveConn, archiveBody);
                    archiveConn.getResponseCode();
                    archiveConn.disconnect();
                } catch (Exception ignored) {}

                String encodedGuestId = java.net.URLEncoder.encode(supportGuestId, "UTF-8");
                delConn = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?guest_id=eq." + encodedGuestId,
                        "DELETE",
                        accessToken
                );
                delConn.setRequestProperty("x-admin-panel", "true");
                delConn.setRequestProperty("x-guest-id", supportGuestId);

                int code = delConn.getResponseCode();
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Guest support chat marked as resolved.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to resolve chat (HTTP " + code + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error resolving guest chat.", Toast.LENGTH_SHORT).show());
            } finally {
                if (delConn != null) delConn.disconnect();
            }
        });
    }

    private void sendMessage() {
        if (sendingMessage || editMessage == null) {
            return;
        }

        String messageText = editMessage.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }

        editMessage.setText("");
        sendPayload(messageText);
    }

    private void sendPayload(String payloadText) {
        if (isGuestSupportChat) {
            if (supportGuestId == null || supportGuestId.trim().isEmpty()) {
                supportGuestId = getSharedPreferences("iKhayaSupport", MODE_PRIVATE)
                        .getString("GUEST_SUPPORT_ID", "");
                if (supportGuestId == null || supportGuestId.trim().isEmpty()) {
                    supportGuestId = "guest_" + java.util.UUID.randomUUID().toString();
                    getSharedPreferences("iKhayaSupport", MODE_PRIVATE)
                            .edit()
                            .putString("GUEST_SUPPORT_ID", supportGuestId)
                            .apply();
                }
            }
            if (receiverId != null && receiverId.startsWith("guest_")) {
                senderId = "ADMIN_SUPPORT";
                senderName = "iKhaya Admin Support";
                senderRole = "ADMIN";
            } else {
                senderId = supportGuestId;
                receiverId = "ADMIN_SUPPORT";
                if (senderName == null || senderName.trim().isEmpty()) {
                    senderName = "Guest (" + supportGuestId.substring(0, Math.min(12, supportGuestId.length())) + ")";
                }
                senderRole = "GUEST";
            }
        } else {
            if (senderId == null || senderId.trim().isEmpty()) {
                senderId = SupabaseHelper.getUserId(this);
            }
            if (senderName == null || senderName.trim().isEmpty() || getString(R.string.default_chat_user_name).equals(senderName)) {
                senderName = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_NAME", getString(R.string.default_chat_user_name));
            }
            if (senderRole == null || senderRole.trim().isEmpty() || getString(R.string.default_chat_member_role).equals(senderRole)) {
                senderRole = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_ROLE", "tenant");
            }
        }

        if (!isGuestSupportChat && (senderId == null || senderId.isEmpty()
                || receiverId == null || receiverId.isEmpty()
                || accessToken == null || accessToken.isEmpty())) {
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
                String postEndpoint = isGuestSupportChat ? "/rest/v1/guest_support_messages" : "/rest/v1/messages";
                String authToken = ((isGuestSupportChat && senderId != null && senderId.startsWith("guest_")) || accessToken == null || accessToken.isEmpty()) ? null : accessToken;
                connection = SupabaseHelper.createConnection(postEndpoint, "POST", authToken);
                connection.setRequestProperty("Prefer", "return=minimal");

                if (isGuestSupportChat && "ADMIN_SUPPORT".equals(senderId)) {
                    connection.setRequestProperty("x-admin-panel", "true");
                }

                JSONObject body = new JSONObject();
                if (isGuestSupportChat) {
                    boolean isFromGuest = senderId != null && senderId.startsWith("guest_");
                    body.put("guest_id", supportGuestId);
                    body.put("guest_hint", isFromGuest ? senderName : (receiverNameField != null && !receiverNameField.isEmpty() ? receiverNameField : supportGuestId));
                    body.put("sender_id", isFromGuest ? supportGuestId : "ADMIN_SUPPORT");
                    body.put("sender_name", isFromGuest ? senderName : "iKhaya Admin Support");
                    body.put("receiver_id", isFromGuest ? "ADMIN_SUPPORT" : supportGuestId);
                    body.put("message_text", payloadText);
                } else {
                    body.put("sender_id", senderId);
                    body.put("sender_name", senderName);
                    body.put("sender_role", senderRole);
                    body.put("receiver_id", isCommunity ? "COMMUNITY" : receiverId);
                    body.put("message_text", payloadText);
                }

                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                success = code >= 200 && code < 300;
                
                if (success && !isGuestSupportChat && !isCommunity && receiverId != null && !receiverId.isEmpty() && !"ADMIN_SUPPORT".equals(receiverId)) {
                    String preview = payloadText.startsWith("[IMAGE") ? "Sent an image"
                            : (payloadText.startsWith("[PDF_FILE]") ? "Sent a PDF document" : payloadText);
                    SupabaseHelper.createNotification(
                            receiverId,
                            "New message from " + (senderName != null && !senderName.isEmpty() ? senderName : "iKhaya User"),
                            preview,
                            accessToken
                    );
                }
                
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