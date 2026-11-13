package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "ikhaya_alerts_channel";
    private static final String PREFS_NAME = "iKhayaNotificationState";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean pollerStarted = false;

    private NotificationHelper() {}

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "iKhaya Alerts & Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for chat messages, listings, viewings, applications, and rent reminders");
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

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
        
        android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Light_NoTitleBar);
        float density = activity.getResources().getDisplayMetrics().density;
        
        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.parseColor("#F9F6F3"));
        
        android.widget.LinearLayout topBar = new android.widget.LinearLayout(activity);
        topBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = Math.round(16 * density);
        topBar.setPadding(pad, pad, pad, pad);
        
        android.widget.ImageButton btnBack = new android.widget.ImageButton(activity);
        android.graphics.drawable.GradientDrawable backBg = new android.graphics.drawable.GradientDrawable();
        backBg.setColor(android.graphics.Color.WHITE);
        backBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        btnBack.setBackground(backBg);
        btnBack.setImageResource(R.drawable.chevron_left);
        btnBack.setPadding(Math.round(8 * density), Math.round(8 * density), Math.round(8 * density), Math.round(8 * density));
        android.widget.LinearLayout.LayoutParams backLp = new android.widget.LinearLayout.LayoutParams(Math.round(38 * density), Math.round(38 * density));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            if (onUpdated != null) onUpdated.run();
        });
        topBar.addView(btnBack);
        
        android.widget.TextView header = new android.widget.TextView(activity);
        header.setText("NOTIFICATION SETTINGS");
        header.setTextColor(android.graphics.Color.parseColor("#786F6B"));
        header.setTextSize(12f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams headLp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headLp.rightMargin = Math.round(38 * density);
        header.setLayoutParams(headLp);
        topBar.addView(header);
        root.addView(topBar);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        android.widget.LinearLayout scrollContent = new android.widget.LinearLayout(activity);
        scrollContent.setOrientation(android.widget.LinearLayout.VERTICAL);
        scrollContent.setPadding(Math.round(20 * density), Math.round(8 * density), Math.round(20 * density), Math.round(32 * density));
        
        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("Notification Preferences");
        title.setTextColor(android.graphics.Color.parseColor("#1F1F1F"));
        title.setTextSize(26f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        scrollContent.addView(title);
        
        android.widget.TextView badge = new android.widget.TextView(activity);
        badge.setText("CUSTOMIZE DEVICE ALERTS");
        badge.setTextColor(android.graphics.Color.parseColor("#B85032"));
        badge.setTextSize(10f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setColor(android.graphics.Color.parseColor("#FDE8E1"));
        badgeBg.setCornerRadius(Math.round(6 * density));
        badge.setBackground(badgeBg);
        badge.setPadding(Math.round(8 * density), Math.round(4 * density), Math.round(8 * density), Math.round(4 * density));
        android.widget.LinearLayout.LayoutParams badgeLp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = Math.round(8 * density);
        badgeLp.bottomMargin = Math.round(24 * density);
        badge.setLayoutParams(badgeLp);
        scrollContent.addView(badge);

        java.util.List<androidx.appcompat.widget.SwitchCompat> allSwitches = new java.util.ArrayList<>();
        
        androidx.cardview.widget.CardView card1 = new androidx.cardview.widget.CardView(activity);
        card1.setRadius(16 * density);
        card1.setCardElevation(0f);
        android.widget.LinearLayout c1 = new android.widget.LinearLayout(activity);
        c1.setOrientation(android.widget.LinearLayout.VERTICAL);
        androidx.appcompat.widget.SwitchCompat swMaster = buildSwitchRow(activity, c1, "Allow Push Notifications", "Enable or mute all iKhaya status-bar alerts on this device", prefs, "PREF_MASTER_ENABLED");
        card1.addView(c1);
        android.widget.LinearLayout.LayoutParams card1Lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        card1Lp.bottomMargin = Math.round(16 * density);
        card1.setLayoutParams(card1Lp);
        scrollContent.addView(card1);
        
        androidx.cardview.widget.CardView card2 = new androidx.cardview.widget.CardView(activity);
        card2.setRadius(16 * density);
        card2.setCardElevation(0f);
        android.widget.LinearLayout c2 = new android.widget.LinearLayout(activity);
        c2.setOrientation(android.widget.LinearLayout.VERTICAL);
        allSwitches.add(buildSwitchRow(activity, c2, "Direct Chat Messages", "Instant alerts for new chat messages, photos, and PDF documents", prefs, "PREF_CHAT_MESSAGES"));
        buildDivider(activity, c2);
        allSwitches.add(buildSwitchRow(activity, c2, "Listing Enquiries & Chat Requests", "Alerts when a tenant or host sends a connection or listing request", prefs, "PREF_ENQUIRIES"));
        card2.addView(c2);
        android.widget.LinearLayout.LayoutParams card2Lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        card2Lp.bottomMargin = Math.round(16 * density);
        card2.setLayoutParams(card2Lp);
        scrollContent.addView(card2);
        
        androidx.cardview.widget.CardView card3 = new androidx.cardview.widget.CardView(activity);
        card3.setRadius(16 * density);
        card3.setCardElevation(0f);
        android.widget.LinearLayout c3 = new android.widget.LinearLayout(activity);
        c3.setOrientation(android.widget.LinearLayout.VERTICAL);
        allSwitches.add(buildSwitchRow(activity, c3, "Rental Applications", "Status updates when applications are submitted, approved, or declined", prefs, "PREF_APPLICATIONS"));
        buildDivider(activity, c3);
        allSwitches.add(buildSwitchRow(activity, c3, "Property Viewings", "Alerts for new viewing requests and viewing schedule confirmations", prefs, "PREF_VIEWINGS"));
        buildDivider(activity, c3);
        allSwitches.add(buildSwitchRow(activity, c3, "Monthly Rent Reminders", "Helpful reminders before monthly rent is due on active leases", prefs, "PREF_RENT_REMINDERS"));
        card3.addView(c3);
        android.widget.LinearLayout.LayoutParams card3Lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        card3Lp.bottomMargin = Math.round(16 * density);
        card3.setLayoutParams(card3Lp);
        scrollContent.addView(card3);
        
        androidx.cardview.widget.CardView card4 = new androidx.cardview.widget.CardView(activity);
        card4.setRadius(16 * density);
        card4.setCardElevation(0f);
        android.widget.LinearLayout c4 = new android.widget.LinearLayout(activity);
        c4.setOrientation(android.widget.LinearLayout.VERTICAL);
        allSwitches.add(buildSwitchRow(activity, c4, "Listings & ID Verification", "Alerts for listing approvals, price updates, and ID verification results", prefs, "PREF_LISTING_AND_VERIFY"));
        card4.addView(c4);
        android.widget.LinearLayout.LayoutParams card4Lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        card4Lp.bottomMargin = Math.round(24 * density);
        card4.setLayoutParams(card4Lp);
        scrollContent.addView(card4);

        swMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("PREF_MASTER_ENABLED", isChecked).apply();
            for (androidx.appcompat.widget.SwitchCompat s : allSwitches) {
                s.setEnabled(isChecked);
                s.setAlpha(isChecked ? 1.0f : 0.5f);
            }
        });
        boolean isMaster = prefs.getBoolean("PREF_MASTER_ENABLED", true);
        for (androidx.appcompat.widget.SwitchCompat s : allSwitches) {
            s.setEnabled(isMaster);
            s.setAlpha(isMaster ? 1.0f : 0.5f);
        }

        android.widget.Button btnTest = new android.widget.Button(activity);
        btnTest.setText("Send Test Notification");
        btnTest.setAllCaps(false);
        btnTest.setTextColor(android.graphics.Color.parseColor("#B85032"));
        btnTest.setTypeface(null, android.graphics.Typeface.BOLD);
        btnTest.setBackgroundResource(R.drawable.bg_outline_brand);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(48 * density));
        btnTest.setLayoutParams(btnLp);
        btnTest.setOnClickListener(v -> {
            if (isCategoryEnabled(activity, "PREF_MASTER_ENABLED")) {
                showPhoneNotification(activity, "iKhaya Notifications Active", "Your device is set up to receive important accommodation, chat, and lease updates.", NotificationsActivity.class, 9999);
                android.widget.Toast.makeText(activity, "Test notification sent.", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(activity, "Notifications are currently muted.", android.widget.Toast.LENGTH_SHORT).show();
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
                        "/rest/v1/messages?select=id,sender_name,message_text&receiver_id=eq." + userId + "&order=created_at.desc&limit=1",
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
                                String text = msg.optString("message_text", "Sent a message");
                                showPhoneNotification(context, "New Message from " + sender, formatCleanMessageBody(text, sender), UserSelectionActivity.class, ("msg_" + msgId).hashCode());
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
                            "/rest/v1/rental_applications?select=id,status,property_name&tenant_uid=eq." + userId,
                            "GET", token);
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                        JSONArray apps = new JSONArray(SupabaseHelper.readResponse(conn));
                        for (int i = 0; i < apps.length(); i++) {
                            JSONObject app = apps.getJSONObject(i);
                            String appId = app.optString("id", "");
                            String status = app.optString("status", "PENDING").toUpperCase();
                            String propName = app.optString("property_name", "your accommodation");
                            String savedStatus = statePrefs.getString("APP_STATUS_" + appId, "");
                            
                            if (!savedStatus.isEmpty() && !savedStatus.equals(status) && isCategoryEnabled(context, "PREF_APPLICATIONS")) {
                                anyUnread = true;
                                String notifTitle = "APPROVED".equals(status) ? "🎉 Rental Application Approved!" : ("REJECTED".equals(status) || "DECLINED".equals(status) ? "Rental Application Update" : "Application Status Updated");
                                String notifBody = "APPROVED".equals(status) ? "Great news! Your application for " + propName + " has been approved." : ("REJECTED".equals(status) || "DECLINED".equals(status) ? "Your application for " + propName + " was not approved. Tap to view details." : "Your application for " + propName + " is now " + status + ".");
                                showPhoneNotification(context, notifTitle, notifBody, ApplicationsActivity.class, ("app_" + appId).hashCode());
                            }
                            statePrefs.edit().putString("APP_STATUS_" + appId, status).apply();

                            // Rent Reminder for Approved / Active Leases
                            if (("APPROVED".equals(status) || "ACTIVE".equals(status)) && !statePrefs.getBoolean("RENT_REMINDER_SENT_" + appId, false) && isCategoryEnabled(context, "PREF_RENT_REMINDERS")) {
                                statePrefs.edit().putBoolean("RENT_REMINDER_SENT_" + appId, true).apply();
                                anyUnread = true;
                                showPhoneNotification(context, "📅 Upcoming Rent Reminder", "Friendly reminder: Monthly rent for " + propName + " is due on the 1st of the month.", NotificationsActivity.class, ("rent_" + appId).hashCode());
                            }
                        }
                    }
                    conn.disconnect();
                } else if ("landlord".equalsIgnoreCase(role)) {
                    // Check Landlord Rental Applications
                    conn = SupabaseHelper.createConnection(
                            "/rest/v1/rental_applications?select=id,property_name,status&landlord_uid=eq." + userId + "&order=created_at.desc&limit=1",
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
                                showPhoneNotification(context, "📋 New Rental Application", "A tenant submitted a rental application for " + aObj.optString("property_name", "your listing") + ". Tap to review.", ApplicationsActivity.class, ("lapp_" + lAppId).hashCode());
                            }
                        }
                    }
                    conn.disconnect();
                    
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
                            "/rest/v1/reports?select=id,issue_type,reason&status=in.(OPEN,open,PENDING,pending)&order=created_at.desc&limit=1",
                            "GET", token);
                    if (rptConn.getResponseCode() >= 200 && rptConn.getResponseCode() < 300) {
                        JSONArray rptArr = new JSONArray(SupabaseHelper.readResponse(rptConn));
                        if (rptArr.length() > 0) {
                            JSONObject rpt = rptArr.getJSONObject(0);
                            String rptId = rpt.optString("id", "");
                            String savedRptId = statePrefs.getString("LAST_ADMIN_RPT_ID", "");
                            if (!rptId.isEmpty() && !rptId.equals(savedRptId)) {
                                statePrefs.edit().putString("LAST_ADMIN_RPT_ID", rptId).apply();
                                showPhoneNotification(context, "New Support Ticket", 
                                        cleanPreview(rpt.optString("reason", "A new issue was reported.")), 
                                        AdminReportsActivity.class, ("adm_r_" + rptId).hashCode());
                            }
                        }
                    }
                    rptConn.disconnect();
                    
                    HttpURLConnection admVerifConn = SupabaseHelper.createConnection(
                            "/rest/v1/profiles?select=id,full_name,identity_status&identity_status=eq.PENDING_VERIFICATION&limit=1",
                            "GET", token);
                    if (admVerifConn.getResponseCode() >= 200 && admVerifConn.getResponseCode() < 300) {
                        JSONArray vArr = new JSONArray(SupabaseHelper.readResponse(admVerifConn));
                        if (vArr.length() > 0) {
                            JSONObject vObj = vArr.getJSONObject(0);
                            String vId = vObj.optString("id", "");
                            String savedVId = statePrefs.getString("LAST_ADMIN_VERIF_ID", "");
                            if (!vId.isEmpty() && !vId.equals(savedVId)) {
                                statePrefs.edit().putString("LAST_ADMIN_VERIF_ID", vId).apply();
                                showPhoneNotification(context, "🪪 New ID Verification Request", 
                                        vObj.optString("full_name", "A landlord") + " submitted identity documents for Admin verification.", 
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
