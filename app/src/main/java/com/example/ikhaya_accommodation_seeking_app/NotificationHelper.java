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

        Runnable pollTask = new Runnable() {
            @Override
            public void run() {
                checkAndTriggerNotifications(appContext);
                handler.postDelayed(this, 20000L); // Poll every 20 seconds even if user is on home screen
            }
        };
        handler.postDelayed(pollTask, 3000L);
    }

    public static void checkAndTriggerNotifications(Context context) {
        String userId = SupabaseHelper.getUserId(context);
        String token = SupabaseHelper.getAccessToken(context);
        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) return;

        executor.execute(() -> {
            SharedPreferences statePrefs = context.getSharedPreferences(PREFS_NAME + "_" + userId, Context.MODE_PRIVATE);
            boolean anyUnread = false;

            try {
                // 1. Check Unread Notifications Table (Listings, System Alerts, Viewings)
                HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/notifications?select=id,title,message,is_read&user_id=eq." + userId + "&is_read=eq.false&order=created_at.desc",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (arr.length() > 0) {
                        anyUnread = true;
                        JSONObject latest = arr.getJSONObject(0);
                        String notifId = latest.optString("id", "");
                        String lastSeenNotifId = statePrefs.getString("LAST_NOTIF_ID", "");
                        if (!notifId.isEmpty() && !notifId.equals(lastSeenNotifId)) {
                            statePrefs.edit().putString("LAST_NOTIF_ID", notifId).apply();
                            String title = latest.optString("title", "iKhaya Alert");
                            String body = latest.optString("message", "You have a new notification.");
                            showPhoneNotification(context, title, body, NotificationsActivity.class, 1001);
                        }
                    }
                }
                conn.disconnect();

                // 2. Check New Incoming Chat Messages
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/messages?select=id,sender_name,message_text&receiver_id=eq." + userId + "&order=created_at.desc&limit=1",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray msgs = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (msgs.length() > 0) {
                        JSONObject msg = msgs.getJSONObject(0);
                        String msgId = msg.optString("id", "");
                        String lastMsgId = statePrefs.getString("LAST_MSG_ID", "");
                        if (!msgId.isEmpty() && !msgId.equals(lastMsgId)) {
                            if (!lastMsgId.isEmpty()) {
                                anyUnread = true;
                                String sender = msg.optString("sender_name", "iKhaya User");
                                String text = msg.optString("message_text", "Sent an attachment");
                                if (text.startsWith("[IMAGE_BASE64]:")) text = "📷 Sent a photo";
                                else if (text.startsWith("[PDF_FILE]:")) text = "📄 Sent a PDF document";
                                showPhoneNotification(context, "New Message from " + sender, text, UserSelectionActivity.class, 1002);
                            }
                            statePrefs.edit().putString("LAST_MSG_ID", msgId).apply();
                        }
                    }
                }
                conn.disconnect();

                // 3. Check Pending Chat Requests / Listing Enquiries
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/chat_requests?select=id,sender_name&receiver_id=eq." + userId + "&status=eq.pending",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray reqs = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (reqs.length() > 0) {
                        anyUnread = true;
                        int prevCount = statePrefs.getInt("LAST_CHAT_REQ_COUNT", 0);
                        if (reqs.length() > prevCount) {
                            String sender = reqs.getJSONObject(0).optString("sender_name", "A user");
                            showPhoneNotification(context, "New Listing Enquiry", sender + " sent you a chat request.", UserSelectionActivity.class, 1003);
                        }
                        statePrefs.edit().putInt("LAST_CHAT_REQ_COUNT", reqs.length()).apply();
                    }
                }
                conn.disconnect();

                // 4. Check Tenant Rental Applications & Rent Reminder
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
                            if (!savedStatus.isEmpty() && !savedStatus.equals(status)) {
                                anyUnread = true;
                                showPhoneNotification(context, "Application " + status,
                                        "Your application for " + propName + " is now " + status + ".", ApplicationsActivity.class, 1004);
                            }
                            statePrefs.edit().putString("APP_STATUS_" + appId, status).apply();

                            // 5. Rent Reminder for Approved / Active Leases (Once per session cycle)
                            if (("APPROVED".equals(status) || "ACTIVE".equals(status)) && !statePrefs.getBoolean("RENT_REMINDER_SENT_" + appId, false)) {
                                statePrefs.edit().putBoolean("RENT_REMINDER_SENT_" + appId, true).apply();
                                anyUnread = true;
                                showPhoneNotification(context, "Upcoming Rent & Lease Reminder",
                                        "Reminder: Your monthly rent for " + propName + " is due on the 1st of the month.", NotificationsActivity.class, 1005);
                            }
                        }
                    }
                    conn.disconnect();
                } else if ("landlord".equalsIgnoreCase(role)) {
                    // Check Landlord Viewing Requests / Enquiries
                    conn = SupabaseHelper.createConnection(
                            "/rest/v1/enquiries?select=id,tenant_name,property_name&landlord_uid=eq." + userId,
                            "GET", token);
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                        JSONArray enqs = new JSONArray(SupabaseHelper.readResponse(conn));
                        if (enqs.length() > 0) {
                            anyUnread = true;
                            int prevEnq = statePrefs.getInt("LAST_ENQ_COUNT", 0);
                            if (enqs.length() > prevEnq) {
                                JSONObject eObj = enqs.getJSONObject(0);
                                showPhoneNotification(context, "New Viewing / Listing Enquiry",
                                        eObj.optString("tenant_name", "A tenant") + " enquired about " + eObj.optString("property_name", "your listing") + ".",
                                        UserSelectionActivity.class, 1006);
                            }
                            statePrefs.edit().putInt("LAST_ENQ_COUNT", enqs.length()).apply();
                        }
                    }
                    conn.disconnect();
                }

                if (anyUnread) {
                    markUnreadDot(context, true);
                }
            } catch (Exception ignored) {}
        });
    }
}
