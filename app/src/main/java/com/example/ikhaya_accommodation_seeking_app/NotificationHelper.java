package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "ikhaya_alerts_channel";
    private static final String PREFS_NAME = "iKhayaNotificationState";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean pollerStarted = false;

    private NotificationHelper() {}

    private static android.graphics.Bitmap getAppIconBitmap(Context context) {
        try {
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher);
            if (d == null) return null;
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 128;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 128;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            d.draw(canvas);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "iKhaya Alerts & Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Verified real-time accommodation alerts for iKhaya messages, property viewings, rental applications, and identity verification");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(android.graphics.Color.parseColor("#B85032"));
            channel.setShowBadge(true);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void markUnreadDot(Context context, boolean hasUnread) {
        context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("HAS_UNREAD_NOTIFICATIONS", hasUnread)
                .apply();
    }

    public static boolean hasUnreadDot(Context context) {
        return context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getBoolean("HAS_UNREAD_NOTIFICATIONS", false);
    }

    public static void showPhoneNotification(Context context, String title, String message, Class<?> targetActivity, int notificationId) {
        Context appContext = context.getApplicationContext();
        createChannel(appContext);
        markUnreadDot(appContext, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent intent = new Intent(appContext, targetActivity != null ? targetActivity : NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String domain;
        String category;
        String groupKey;
        
        if (title.contains("Message") || title.contains("Chat") || title.contains("Enquiry")) {
            domain = "Direct Messaging";
            category = NotificationCompat.CATEGORY_MESSAGE;
            groupKey = "IKHAYA_MESSAGES";
        } else if (title.contains("Viewing") || title.contains("📅")) {
            domain = "Property Viewing Schedule";
            category = NotificationCompat.CATEGORY_EVENT;
            groupKey = "IKHAYA_VIEWINGS";
        } else if (title.contains("Application") || title.contains("Rent") || title.contains("Lease")) {
            domain = "Rental & Lease Status";
            category = NotificationCompat.CATEGORY_STATUS;
            groupKey = "IKHAYA_APPLICATIONS";
        } else if (title.contains("Ticket") || title.contains("Verification") || title.contains("ID") || title.contains("Listing")) {
            domain = "Trust & Verification";
            category = NotificationCompat.CATEGORY_STATUS;
            groupKey = "IKHAYA_VERIFICATION";
        } else {
            domain = "iKhaya Accommodation";
            category = NotificationCompat.CATEGORY_RECOMMENDATION;
            groupKey = "IKHAYA_GENERAL";
        }

        android.os.Bundle aiExtras = new android.os.Bundle();
        aiExtras.putString("ikhaya_app_name", "iKhaya");
        aiExtras.putString("ikhaya_domain", domain);
        aiExtras.putString("ikhaya_full_title", title);
        aiExtras.putString("ikhaya_full_details", message);
        aiExtras.putBoolean("ikhaya_verified_alert", true);

        android.graphics.Bitmap largeLogo = getAppIconBitmap(appContext);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ikhaya_app_icon_monochrome)
                .setColor(android.graphics.Color.parseColor("#B85032"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addExtras(aiExtras)
                .setSubText("iKhaya • " + domain)
                .setCategory(category)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setGroup(groupKey);

        if (largeLogo != null) {
            builder.setLargeIcon(largeLogo);
        }

        if (NotificationCompat.CATEGORY_MESSAGE.equals(category)) {
            androidx.core.app.Person userMe = new androidx.core.app.Person.Builder().setName("You (iKhaya User)").build();
            String senderNameClean = title.replace("💬", "").replace("🏠", "").replace("New Message from", "").replace("Enquiry from", "").trim();
            if (senderNameClean.isEmpty()) senderNameClean = "iKhaya Contact";
            androidx.core.app.Person.Builder senderBuilder = new androidx.core.app.Person.Builder().setName(senderNameClean).setImportant(true);
            if (largeLogo != null) {
                senderBuilder.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(largeLogo));
            }
            androidx.core.app.Person senderPerson = senderBuilder.build();
            NotificationCompat.MessagingStyle msgStyle = new NotificationCompat.MessagingStyle(userMe)
                    .setConversationTitle("iKhaya • " + senderNameClean)
                    .addMessage(message, System.currentTimeMillis(), senderPerson);
            builder.setStyle(msgStyle);
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message).setBigContentTitle(title).setSummaryText("iKhaya Verified • " + domain));
            builder.setContentTitle(title);
            builder.setContentText(message);
        }

        try {
            NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {}
    }

    public static void startBackgroundPoller(Context context) {
        Context appContext = context.getApplicationContext();
        createChannel(appContext);
        if (pollerStarted) return;
        pollerStarted = true;

        if (context instanceof android.app.Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((android.app.Activity) context, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Runnable pollTask = new Runnable() {
            @Override
            public void run() {
                checkAndTriggerNotifications(appContext);
                handler.postDelayed(this, 10000L); // Poll every 10 seconds for faster live testing
            }
        };
        handler.postDelayed(pollTask, 1000L);
    }

    public static boolean isCategoryEnabled(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences("iKhayaNotifSettings", Context.MODE_PRIVATE);
        boolean master = prefs.getBoolean("PREF_MASTER_ENABLED", true);
        if (!master) return false;
        return prefs.getBoolean(key, true);
    }

    public static String getPreferencesSummaryLabel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("iKhayaNotifSettings", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("PREF_MASTER_ENABLED", true)) return "Muted";
        
        String[] keys = {
            "PREF_CHAT_MESSAGES", "PREF_ENQUIRIES", "PREF_APPLICATIONS", 
            "PREF_VIEWINGS", "PREF_RENT_REMINDERS", "PREF_LISTING_AND_VERIFY"
        };
        int count = 0;
        for (String k : keys) {
            if (prefs.getBoolean(k, true)) count++;
        }
        if (count == keys.length) return "All On";
        return count + "/6 On";
    }

    public static void openNotificationSettingsDialog(android.app.Activity activity, Runnable onUpdated) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences("iKhayaNotifSettings", Context.MODE_PRIVATE);
        
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Light_NoTitleBar);
        float density = activity.getResources().getDisplayMetrics().density;
        
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F9F6F3"));
        
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(16 * density);
        topBar.setPadding(pad, pad, pad, pad);
        
        ImageButton btnBack = new ImageButton(activity);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(Color.WHITE);
        backBg.setShape(GradientDrawable.OVAL);
        btnBack.setBackground(backBg);
        btnBack.setImageResource(R.drawable.chevron_left);
        btnBack.setPadding(Math.round(8 * density), Math.round(8 * density), Math.round(8 * density), Math.round(8 * density));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(Math.round(38 * density), Math.round(38 * density));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            if (onUpdated != null) onUpdated.run();
        });
        topBar.addView(btnBack);
        
        TextView header = new TextView(activity);
        header.setText(R.string.title_notification_settings);
        header.setTextColor(Color.parseColor("#786F6B"));
        header.setTextSize(12f);
        header.setTypeface(null, Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headLp.rightMargin = Math.round(38 * density);
        header.setLayoutParams(headLp);
        topBar.addView(header);
        root.addView(topBar);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout scrollContent = new LinearLayout(activity);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setPadding(Math.round(20 * density), Math.round(8 * density), Math.round(20 * density), Math.round(32 * density));
        
        TextView title = new TextView(activity);
        title.setText(R.string.title_notification_preferences);
        title.setTextColor(Color.parseColor("#1F1F1F"));
        title.setTextSize(26f);
        title.setTypeface(null, Typeface.BOLD);
        scrollContent.addView(title);
        
        TextView badge = new TextView(activity);
        badge.setText(R.string.badge_customize_device_alerts);
        badge.setTextColor(Color.parseColor("#B85032"));
        badge.setTextSize(10f);
        badge.setTypeface(null, Typeface.BOLD);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#FDE8E1"));
        badgeBg.setCornerRadius(Math.round(6 * density));
        badge.setBackground(badgeBg);
        badge.setPadding(Math.round(8 * density), Math.round(4 * density), Math.round(8 * density), Math.round(4 * density));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = Math.round(8 * density);
        badgeLp.bottomMargin = Math.round(24 * density);
        badge.setLayoutParams(badgeLp);
        scrollContent.addView(badge);

        List<SwitchCompat> allSwitches = new ArrayList<>();
        
        CardView card1 = new CardView(activity);
        card1.setRadius(16 * density);
        card1.setCardElevation(0f);
        LinearLayout c1 = new LinearLayout(activity);
        c1.setOrientation(LinearLayout.VERTICAL);
        SwitchCompat swMaster = buildSwitchRow(activity, c1, activity.getString(R.string.pref_allow_push_notifications), activity.getString(R.string.desc_allow_push_notifications), prefs, "PREF_MASTER_ENABLED");
        card1.addView(c1);
        LinearLayout.LayoutParams card1Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card1Lp.bottomMargin = Math.round(16 * density);
        card1.setLayoutParams(card1Lp);
        scrollContent.addView(card1);
        
        CardView card2 = new CardView(activity);
        card2.setRadius(16 * density);
        card2.setCardElevation(0f);
        LinearLayout c2 = new LinearLayout(activity);
        c2.setOrientation(LinearLayout.VERTICAL);
        allSwitches.add(buildSwitchRow(activity, c2, activity.getString(R.string.pref_direct_chat_messages), activity.getString(R.string.desc_direct_chat_messages), prefs, "PREF_CHAT_MESSAGES"));
        buildDivider(activity, c2);
        allSwitches.add(buildSwitchRow(activity, c2, activity.getString(R.string.pref_enquiries_chat_requests), activity.getString(R.string.desc_enquiries_chat_requests), prefs, "PREF_ENQUIRIES"));
        card2.addView(c2);
        LinearLayout.LayoutParams card2Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card2Lp.bottomMargin = Math.round(16 * density);
        card2.setLayoutParams(card2Lp);
        scrollContent.addView(card2);
        
        CardView card3 = new CardView(activity);
        card3.setRadius(16 * density);
        card3.setCardElevation(0f);
        LinearLayout c3 = new LinearLayout(activity);
        c3.setOrientation(LinearLayout.VERTICAL);
        allSwitches.add(buildSwitchRow(activity, c3, activity.getString(R.string.pref_rental_applications), activity.getString(R.string.desc_rental_applications), prefs, "PREF_APPLICATIONS"));
        buildDivider(activity, c3);
        allSwitches.add(buildSwitchRow(activity, c3, activity.getString(R.string.pref_property_viewings), activity.getString(R.string.desc_property_viewings), prefs, "PREF_VIEWINGS"));
        buildDivider(activity, c3);
        allSwitches.add(buildSwitchRow(activity, c3, activity.getString(R.string.pref_monthly_rent_reminders), activity.getString(R.string.desc_monthly_rent_reminders), prefs, "PREF_RENT_REMINDERS"));
        card3.addView(c3);
        LinearLayout.LayoutParams card3Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card3Lp.bottomMargin = Math.round(16 * density);
        card3.setLayoutParams(card3Lp);
        scrollContent.addView(card3);
        
        CardView card4 = new CardView(activity);
        card4.setRadius(16 * density);
        card4.setCardElevation(0f);
        LinearLayout c4 = new LinearLayout(activity);
        c4.setOrientation(LinearLayout.VERTICAL);
        allSwitches.add(buildSwitchRow(activity, c4, activity.getString(R.string.pref_listings_id_verification), activity.getString(R.string.desc_listings_id_verification), prefs, "PREF_LISTING_AND_VERIFY"));
        card4.addView(c4);
        LinearLayout.LayoutParams card4Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card4Lp.bottomMargin = Math.round(24 * density);
        card4.setLayoutParams(card4Lp);
        scrollContent.addView(card4);

        swMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("PREF_MASTER_ENABLED", isChecked).apply();
            for (SwitchCompat s : allSwitches) {
                s.setEnabled(isChecked);
                s.setAlpha(isChecked ? 1.0f : 0.5f);
            }
        });
        boolean isMaster = prefs.getBoolean("PREF_MASTER_ENABLED", true);
        for (SwitchCompat s : allSwitches) {
            s.setEnabled(isMaster);
            s.setAlpha(isMaster ? 1.0f : 0.5f);
        }

        Button btnTest = new Button(activity);
        btnTest.setText(R.string.btn_send_test_notification);
        btnTest.setAllCaps(false);
        btnTest.setTextColor(Color.parseColor("#B85032"));
        btnTest.setTypeface(null, Typeface.BOLD);
        btnTest.setBackgroundResource(R.drawable.bg_outline_brand);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(48 * density));
        btnTest.setLayoutParams(btnLp);
        btnTest.setOnClickListener(v -> {
            if (isCategoryEnabled(activity, "PREF_MASTER_ENABLED")) {
                showPhoneNotification(activity, "iKhaya Notifications Active", "Your device is set up to receive important accommodation, chat, and lease updates.", NotificationsActivity.class, 9999);
                Toast.makeText(activity, "Test notification sent.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Notifications are currently muted.", Toast.LENGTH_SHORT).show();
            }
        });
        scrollContent.addView(btnTest);
        
        scrollView.addView(scrollContent);
        root.addView(scrollView);
        dialog.setContentView(root);
        
        dialog.setOnDismissListener(d -> {
            if (onUpdated != null) onUpdated.run();
        });
        dialog.show();
    }

    private static androidx.appcompat.widget.SwitchCompat buildSwitchRow(Context context, android.widget.LinearLayout parent, String titleText, String subText, SharedPreferences prefs, String prefKey) {
        float density = context.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = Math.round(16 * density);
        row.setPadding(pad, pad, pad, pad);
        
        android.widget.LinearLayout textCol = new android.widget.LinearLayout(context);
        textCol.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams textLp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.rightMargin = Math.round(12 * density);
        textCol.setLayoutParams(textLp);
        
        android.widget.TextView title = new android.widget.TextView(context);
        title.setText(titleText);
        title.setTextColor(android.graphics.Color.parseColor("#1F1F1F"));
        title.setTextSize(14f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(title);
        
        android.widget.TextView sub = new android.widget.TextView(context);
        sub.setText(subText);
        sub.setTextColor(android.graphics.Color.parseColor("#786F6B"));
        sub.setTextSize(12f);
        textCol.addView(sub);
        
        row.addView(textCol);
        
        androidx.appcompat.widget.SwitchCompat sw = new androidx.appcompat.widget.SwitchCompat(context);
        sw.setChecked(prefs.getBoolean(prefKey, true));
        android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
            },
            new int[]{
                android.graphics.Color.parseColor("#B85032"),
                android.graphics.Color.parseColor("#E0E0E0")
            }
        );
        androidx.core.graphics.drawable.DrawableCompat.setTintList(sw.getThumbDrawable(), colorStateList);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(prefKey, isChecked).apply());
        row.addView(sw);
        
        parent.addView(row);
        return sw;
    }

    private static void buildDivider(Context context, android.widget.LinearLayout parent) {
        float density = context.getResources().getDisplayMetrics().density;
        android.view.View div = new android.view.View(context);
        android.widget.LinearLayout.LayoutParams divLp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * density));
        divLp.leftMargin = Math.round(16 * density);
        divLp.rightMargin = Math.round(16 * density);
        div.setLayoutParams(divLp);
        div.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"));
        parent.addView(div);
    }

    public static void checkAndTriggerNotifications(Context context) {
        String userId = SupabaseHelper.getUserId(context);
        String token = SupabaseHelper.getAccessToken(context);

        executor.execute(() -> {
            try {
                // A. Pre-Login Guest Support Replies
                String guestId = context.getSharedPreferences("iKhayaSupport", Context.MODE_PRIVATE).getString("GUEST_SUPPORT_ID", "");
                if (!guestId.isEmpty() && isCategoryEnabled(context, "PREF_CHAT_MESSAGES")) {
                    HttpURLConnection guestConn = SupabaseHelper.createConnection(
                            "/rest/v1/guest_support_messages?select=id,sender_id,sender_name,message_text&guest_id=eq." + SupabaseHelper.encode(guestId) + "&order=created_at.desc&limit=1",
                            "GET", null);
                    guestConn.setRequestProperty("x-guest-id", guestId);
                    
                    if (guestConn.getResponseCode() >= 200 && guestConn.getResponseCode() < 300) {
                        JSONArray gArr = new JSONArray(SupabaseHelper.readResponse(guestConn));
                        if (gArr.length() > 0) {
                            JSONObject gMsg = gArr.getJSONObject(0);
                            String gMsgId = gMsg.optString("id", "");
                            String gSenderId = gMsg.optString("sender_id", "");
                            String gMsgText = gMsg.optString("message_text", "Sent a message");
                            
                            SharedPreferences guestPrefs = context.getSharedPreferences("iKhayaSupport", Context.MODE_PRIVATE);
                            String lastSeen = guestPrefs.getString("LAST_GUEST_ADMIN_MSG_ID", "");
                            
                            if ("ADMIN_SUPPORT".equals(gSenderId) && !gMsgId.isEmpty() && !gMsgId.equals(lastSeen)) {
                                guestPrefs.edit().putString("LAST_GUEST_ADMIN_MSG_ID", gMsgId).apply();
                                showPhoneNotification(context, "iKhaya Support Replied", formatCleanMessageBody(gMsgText, "iKhaya Admin Support"), LoginActivity.class, ("guest_" + gMsgId).hashCode());
                            }
                        }
                    }
                    guestConn.disconnect();
                }

                if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) return;

                SharedPreferences statePrefs = context.getSharedPreferences(PREFS_NAME + "_" + userId, Context.MODE_PRIVATE);
                boolean anyUnread = false;

                // B. Unread Notifications Table
                HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/notifications?select=id,title,message,is_read&user_uid=eq." + userId + "&is_read=eq.false&order=created_at.desc",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    java.util.Set<String> shownIds = new java.util.HashSet<>(statePrefs.getStringSet("SHOWN_NOTIF_IDS", new java.util.HashSet<>()));
                    
                    for (int i = 0; i < Math.min(arr.length(), 5); i++) {
                        JSONObject notif = arr.getJSONObject(i);
                        String notifId = notif.optString("id", "");
                        
                        if (!notifId.isEmpty() && !shownIds.contains(notifId)) {
                            anyUnread = true;
                            shownIds.add(notifId);
                            statePrefs.edit().putStringSet("SHOWN_NOTIF_IDS", shownIds).apply();
                            
                            String title = notif.optString("title", "iKhaya Alert");
                            String body = notif.optString("message", "You have a new notification.");
                            showPhoneNotification(context, title, cleanPreview(body), NotificationsActivity.class, notifId.hashCode());
                            
                            // Mark as read so it stops polling
                            HttpURLConnection patchConn = SupabaseHelper.createConnection("/rest/v1/notifications?id=eq." + notifId, "PATCH", token);
                            JSONObject pBody = new JSONObject();
                            pBody.put("is_read", true);
                            SupabaseHelper.writeJson(patchConn, pBody);
                            patchConn.getResponseCode();
                            patchConn.disconnect();
                        }
                    }
                }
                conn.disconnect();

                // C. Incoming Direct Chat Messages
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/messages?select=id,sender_name,sender_role,message_text,created_at&receiver_id=eq." + userId + "&order=created_at.desc&limit=1",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray msgs = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (msgs.length() > 0) {
                        JSONObject msg = msgs.getJSONObject(0);
                        String msgId = msg.optString("id", "");
                        String lastMsgId = statePrefs.getString("LAST_MSG_ID", "");
                        boolean isInitDone = statePrefs.getBoolean("INIT_DONE_MSG", false);
                        
                        if (!msgId.isEmpty() && !msgId.equals(lastMsgId)) {
                            statePrefs.edit().putString("LAST_MSG_ID", msgId).apply();
                            if (!isInitDone) {
                                statePrefs.edit().putBoolean("INIT_DONE_MSG", true).apply();
                            } else {
                                anyUnread = true;
                                String sender = msg.optString("sender_name", "iKhaya User");
                                String roleLabel = msg.optString("sender_role", "Member").toUpperCase(java.util.Locale.ROOT);
                                String text = msg.optString("message_text", "Sent a message");
                                showPhoneNotification(context, "💬 New Message from " + sender + " (" + roleLabel + ")", formatCleanMessageBody(text, sender), UserSelectionActivity.class, ("msg_" + msgId).hashCode());
                            }
                        }
                    }
                }
                conn.disconnect();

                // D. Pending Chat Requests / Listing Enquiries
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/chat_requests?select=id,sender_name&receiver_id=eq." + userId + "&status=eq.pending",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray reqs = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (reqs.length() > 0) {
                        String latestReqId = reqs.getJSONObject(0).optString("id", "");
                        String savedReqId = statePrefs.getString("LAST_CHAT_REQ_ID", "");
                        
                        if (!latestReqId.isEmpty() && !latestReqId.equals(savedReqId)) {
                            anyUnread = true;
                            statePrefs.edit().putString("LAST_CHAT_REQ_ID", latestReqId).apply();
                            String sender = reqs.getJSONObject(0).optString("sender_name", "A user");
                            showPhoneNotification(context, "New Listing Enquiry", sender + " sent you a chat request.", UserSelectionActivity.class, ("req_" + latestReqId).hashCode());
                        }
                    }
                }
                conn.disconnect();

                // E. Tenant & Landlord Rental Applications & Viewing Requests
                String role = context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE).getString("USER_ROLE", "tenant");
                if ("tenant".equalsIgnoreCase(role)) {
                    conn = SupabaseHelper.createConnection(
                            "/rest/v1/rental_applications?select=id,status,applicant_name,listing_id,move_in_date,lease_duration&tenant_uid=eq." + userId,
                            "GET", token);
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                        JSONArray apps = new JSONArray(SupabaseHelper.readResponse(conn));
                        for (int i = 0; i < apps.length(); i++) {
                            JSONObject app = apps.getJSONObject(i);
                            String appId = app.optString("id", "");
                            String status = app.optString("status", "PENDING").toUpperCase(java.util.Locale.ROOT);
                            String savedStatus = statePrefs.getString("APP_STATUS_" + appId, "");
                            String propName = "your accommodation";
                            String moveIn = app.optString("move_in_date", "TBD");
                            
                            if (!savedStatus.isEmpty() && !savedStatus.equals(status) && isCategoryEnabled(context, "PREF_APPLICATIONS")) {
                                anyUnread = true;
                                String notifTitle = "APPROVED".equals(status) ? "🎉 Rental Application Approved!" : ("REJECTED".equals(status) || "DECLINED".equals(status) ? "Rental Application Update" : "Application Status Updated");
                                String notifBody = "APPROVED".equals(status) ? "Great news! Your rental application for " + propName + " (Move in: " + moveIn + ") has been approved." : ("REJECTED".equals(status) || "DECLINED".equals(status) ? "Your rental application for " + propName + " was not approved. Tap to view details." : "Your rental application for " + propName + " status is now " + status + ".");
                                showPhoneNotification(context, notifTitle, notifBody, ApplicationsActivity.class, ("app_" + appId).hashCode());
                            }
                            statePrefs.edit().putString("APP_STATUS_" + appId, status).apply();

                            // Rent Reminder for Approved / Active Leases
                            if (("APPROVED".equals(status) || "ACTIVE".equals(status)) && !statePrefs.getBoolean("RENT_REMINDER_SENT_" + appId, false) && isCategoryEnabled(context, "PREF_RENT_REMINDERS")) {
                                statePrefs.edit().putBoolean("RENT_REMINDER_SENT_" + appId, true).apply();
                                anyUnread = true;
                                showPhoneNotification(context, "📅 Upcoming Rent Reminder", "Friendly reminder: Monthly rent for your active lease at " + propName + " is due on the 1st of the month.", NotificationsActivity.class, ("rent_" + appId).hashCode());
                            }
                        }
                    }
                    conn.disconnect();
                    
                    if (isCategoryEnabled(context, "PREF_VIEWINGS")) {
                        HttpURLConnection vConn = SupabaseHelper.createConnection("/rest/v1/viewings?select=id,property_name,viewing_date,status,location&tenant_uid=eq." + userId, "GET", token);
                        if (vConn.getResponseCode() >= 200 && vConn.getResponseCode() < 300) {
                            JSONArray vArr = new JSONArray(SupabaseHelper.readResponse(vConn));
                            for (int i = 0; i < vArr.length(); i++) {
                                JSONObject vObj = vArr.getJSONObject(i);
                                String vId = vObj.optString("id", "");
                                String vStat = vObj.optString("status", "").toUpperCase(java.util.Locale.ROOT);
                                String savedVStat = statePrefs.getString("VIEWING_STATE_" + vId, "");
                                if (!savedVStat.isEmpty() && !savedVStat.equals(vStat)) {
                                    anyUnread = true;
                                    String propName = vObj.optString("property_name", "a property");
                                    String loc = vObj.optString("location", "").trim();
                                    String body = "Your viewing for " + propName + " (" + vObj.optString("viewing_date", "") + ") is now " + vStat + ".";
                                    if (!loc.isEmpty() && !loc.equalsIgnoreCase("null")) body += " Location: " + loc;
                                    showPhoneNotification(context, "📅 Viewing " + vStat, body, ViewingsActivity.class, ("v_" + vId).hashCode());
                                }
                                statePrefs.edit().putString("VIEWING_STATE_" + vId, vStat).apply();
                            }
                        }
                        vConn.disconnect();
                    }
                    
                } else if ("landlord".equalsIgnoreCase(role)) {
                    // Check Landlord Rental Applications
                    conn = SupabaseHelper.createConnection(
                            "/rest/v1/rental_applications?select=id,applicant_name,occupation,move_in_date,lease_duration,occupants,status&landlord_uid=eq." + userId + "&order=created_at.desc&limit=1",
                            "GET", token);
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                        JSONArray lApps = new JSONArray(SupabaseHelper.readResponse(conn));
                        if (lApps.length() > 0) {
                            JSONObject aObj = lApps.getJSONObject(0);
                            String lAppId = aObj.optString("id", "");
                            String savedLAppId = statePrefs.getString("LAST_LANDLORD_APP_ID", "");
                            if (!lAppId.isEmpty() && !lAppId.equals(savedLAppId) && isCategoryEnabled(context, "PREF_APPLICATIONS")) {
                                anyUnread = true;
                                statePrefs.edit().putString("LAST_LANDLORD_APP_ID", lAppId).apply();
                                String duration = aObj.optString("lease_duration", "12");
                                showPhoneNotification(context, "📋 New Rental Application", aObj.optString("applicant_name", "A tenant") + " submitted a new " + duration + "-month rental application. Tap to review.", ApplicationsActivity.class, ("lapp_" + lAppId).hashCode());
                            }
                        }
                    }
                    conn.disconnect();
                    
                    if (isCategoryEnabled(context, "PREF_VIEWINGS")) {
                        HttpURLConnection vConn = SupabaseHelper.createConnection("/rest/v1/viewings?select=id,property_name,viewing_date,status&landlord_uid=eq." + userId + "&order=created_at.desc&limit=1", "GET", token);
                        if (vConn.getResponseCode() >= 200 && vConn.getResponseCode() < 300) {
                            JSONArray vArr = new JSONArray(SupabaseHelper.readResponse(vConn));
                            if (vArr.length() > 0) {
                                JSONObject vObj = vArr.getJSONObject(0);
                                String vId = vObj.optString("id", "");
                                String savedVId = statePrefs.getString("LAST_LANDLORD_VIEWING_ID", "");
                                if (!vId.isEmpty() && !vId.equals(savedVId)) {
                                    anyUnread = true;
                                    statePrefs.edit().putString("LAST_LANDLORD_VIEWING_ID", vId).apply();
                                    showPhoneNotification(context, "📅 New Viewing Scheduled", "A tenant scheduled a viewing for " + vObj.optString("property_name", "your listing") + " on " + vObj.optString("viewing_date", "an upcoming date") + ".", UserSelectionActivity.class, ("lv_" + vId).hashCode());
                                }
                            }
                        }
                        vConn.disconnect();
                    }
                    
                    // G. Landlord Verifications and Listings
                    if (isCategoryEnabled(context, "PREF_LISTING_AND_VERIFY")) {
                        HttpURLConnection profConn = SupabaseHelper.createConnection("/rest/v1/profiles?select=identity_status,is_identity_verified&or=(id.eq." + userId + ",firebase_uid.eq." + userId + ")", "GET", token);
                        if (profConn.getResponseCode() >= 200 && profConn.getResponseCode() < 300) {
                            JSONArray profArr = new JSONArray(SupabaseHelper.readResponse(profConn));
                            if (profArr.length() > 0) {
                                String vStatus = profArr.getJSONObject(0).optString("identity_status", "UNVERIFIED").toUpperCase();
                                String savedVStatus = statePrefs.getString("LAST_VERIF_STATE", vStatus);
                                if (!vStatus.equals(savedVStatus)) {
                                    statePrefs.edit().putString("LAST_VERIF_STATE", vStatus).apply();
                                    if ("VERIFIED".equals(vStatus) || "APPROVED".equals(vStatus)) {
                                        showPhoneNotification(context, "✅ Identity Verification Approved", "Your identity documents have been verified! Your profile and listings now display the Verified badge.", LandlordProfileActivity.class, "verif_upd".hashCode());
                                    } else {
                                        showPhoneNotification(context, "⚠️ Identity Verification Update", "Your identity verification status was updated to " + vStatus + ". Tap to check your profile.", LandlordProfileActivity.class, "verif_upd".hashCode());
                                    }
                                }
                            }
                        }
                        profConn.disconnect();
                        
                        HttpURLConnection lstConn = SupabaseHelper.createConnection("/rest/v1/listings?select=id,property_name,status&landlord_uid=eq." + userId + "&limit=20", "GET", token);
                        if (lstConn.getResponseCode() >= 200 && lstConn.getResponseCode() < 300) {
                            JSONArray lstArr = new JSONArray(SupabaseHelper.readResponse(lstConn));
                            for (int i = 0; i < lstArr.length(); i++) {
                                JSONObject lst = lstArr.getJSONObject(i);
                                String lstId = lst.optString("id", "");
                                String lstStat = lst.optString("status", "").toUpperCase();
                                String lstName = lst.optString("property_name", "your listing");
                                String savedLstStat = statePrefs.getString("LISTING_STATE_" + lstId, lstStat);
                                if (!lstStat.equals(savedLstStat)) {
                                    statePrefs.edit().putString("LISTING_STATE_" + lstId, lstStat).apply();
                                    if ("AVAILABLE".equals(lstStat) || "APPROVED".equals(lstStat) || "ACTIVE".equals(lstStat)) {
                                        showPhoneNotification(context, "🏠 Property Listing Approved!", "Your listing '" + lstName + "' is now live and visible to tenants.", MyListingsActivity.class, ("lst_" + lstId).hashCode());
                                    } else {
                                        showPhoneNotification(context, "🏠 Listing Status Updated", "Your listing '" + lstName + "' status is now " + lstStat + ".", MyListingsActivity.class, ("lst_" + lstId).hashCode());
                                    }
                                }
                            }
                        }
                        lstConn.disconnect();
                    }
                }
                
                // F. Admin Important Alerts
                if ("admin".equalsIgnoreCase(role)) {
                    HttpURLConnection admConn = SupabaseHelper.createConnection(
                            "/rest/v1/guest_support_messages?select=id,guest_hint,sender_id,message_text&order=created_at.desc&limit=1",
                            "GET", token);
                    admConn.setRequestProperty("x-admin-panel", "true");
                    if (admConn.getResponseCode() >= 200 && admConn.getResponseCode() < 300) {
                        JSONArray admArr = new JSONArray(SupabaseHelper.readResponse(admConn));
                        if (admArr.length() > 0) {
                            JSONObject admMsg = admArr.getJSONObject(0);
                            String aMsgId = admMsg.optString("id", "");
                            String aSenderId = admMsg.optString("sender_id", "");
                            if (aSenderId.startsWith("guest_") && !aMsgId.isEmpty()) {
                                String savedAMsgId = statePrefs.getString("LAST_ADMIN_GUEST_MSG_ID", "");
                                if (!aMsgId.equals(savedAMsgId)) {
                                    statePrefs.edit().putString("LAST_ADMIN_GUEST_MSG_ID", aMsgId).apply();
                                    showPhoneNotification(context, "New Guest Support Message", 
                                            admMsg.optString("guest_hint", "Guest") + ": " + cleanPreview(admMsg.optString("message_text", "")), 
                                            AdminReportsActivity.class, ("adm_g_" + aMsgId).hashCode());
                                }
                            }
                        }
                    }
                    admConn.disconnect();
                    
                    HttpURLConnection rptConn = SupabaseHelper.createConnection(
                            "/rest/v1/reports?select=id,issue_type,reason,reporter_name,reporter_role&status=in.(OPEN,open,PENDING,pending)&order=created_at.desc&limit=1",
                            "GET", token);
                    if (rptConn.getResponseCode() >= 200 && rptConn.getResponseCode() < 300) {
                        JSONArray rptArr = new JSONArray(SupabaseHelper.readResponse(rptConn));
                        if (rptArr.length() > 0) {
                            JSONObject rpt = rptArr.getJSONObject(0);
                            String rptId = rpt.optString("id", "");
                            String savedRptId = statePrefs.getString("LAST_ADMIN_RPT_ID", "");
                            if (!rptId.isEmpty() && !rptId.equals(savedRptId)) {
                                statePrefs.edit().putString("LAST_ADMIN_RPT_ID", rptId).apply();
                                String issueType = rpt.optString("issue_type", "").trim();
                                if (issueType.equalsIgnoreCase("null")) issueType = "";
                                String title = !issueType.isEmpty() ? "🛠️ Ticket: " + issueType : "🛠️ New Support Ticket";
                                String repName = rpt.optString("reporter_name", "User");
                                String repRole = rpt.optString("reporter_role", "Member").toUpperCase(java.util.Locale.ROOT);
                                String body = cleanPreview(rpt.optString("reason", "A new issue was reported."));
                                showPhoneNotification(context, title, repName + " (" + repRole + "): " + body, AdminReportsActivity.class, ("adm_r_" + rptId).hashCode());
                            }
                        }
                    }
                    rptConn.disconnect();
                    
                    HttpURLConnection admVerifConn = SupabaseHelper.createConnection(
                            "/rest/v1/profiles?select=id,full_name,city,identity_status&identity_status=eq.PENDING_VERIFICATION&limit=1",
                            "GET", token);
                    if (admVerifConn.getResponseCode() >= 200 && admVerifConn.getResponseCode() < 300) {
                        JSONArray vArr = new JSONArray(SupabaseHelper.readResponse(admVerifConn));
                        if (vArr.length() > 0) {
                            JSONObject vObj = vArr.getJSONObject(0);
                            String vId = vObj.optString("id", "");
                            String savedVId = statePrefs.getString("LAST_ADMIN_VERIF_ID", "");
                            if (!vId.isEmpty() && !vId.equals(savedVId)) {
                                statePrefs.edit().putString("LAST_ADMIN_VERIF_ID", vId).apply();
                                String cityName = vObj.optString("city", "").trim();
                                String cityTag = (!cityName.isEmpty() && !cityName.equalsIgnoreCase("null")) ? " from " + cityName : "";
                                showPhoneNotification(context, "🪪 New ID Verification Request", 
                                        vObj.optString("full_name", "A landlord") + cityTag + " submitted identity documents for Admin verification.", 
                                        AdminDashboardActivity.class, ("adm_v_" + vId).hashCode());
                            }
                        }
                    }
                    admVerifConn.disconnect();
                }

                if (anyUnread) {
                    markUnreadDot(context, true);
                }
            } catch (Exception ignored) {}
        });
    }

    private static String formatCleanMessageBody(String text, String sender) {
        if (text == null) return "Sent a message";
        if (text.startsWith("[IMAGE_BASE64]:") || text.startsWith("[IMAGE")) return "📷 " + sender + " sent you a photo.";
        if (text.startsWith("[PDF_FILE]:")) {
            String[] parts = text.substring("[PDF_FILE]:".length()).split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : "Document.pdf";
            return "📄 " + sender + " sent you a PDF document: " + fileName;
        }
        return "💬 \"" + text + "\"";
    }

    private static String cleanPreview(String text) {
        if (text == null) return "";
        if (text.startsWith("[IMAGE_BASE64]:") || text.startsWith("[IMAGE")) return "📷 Sent a photo";
        if (text.startsWith("[PDF_FILE]:")) return "📄 Sent a PDF document";
        return text;
    }
}
