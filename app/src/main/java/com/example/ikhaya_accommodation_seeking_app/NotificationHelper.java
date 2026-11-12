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

    public static void requestPermissionAndStartPoller(android.app.Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        startBackgroundPoller(activity);
        checkAndTriggerNotifications(activity.getApplicationContext());
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

    public static void checkAndTriggerNotifications(Context context) {
        String userId = SupabaseHelper.getUserId(context);
        String token = SupabaseHelper.getAccessToken(context);

        executor.execute(() -> {
            try {
                // A. Pre-Login Guest Support Replies
                String guestId = context.getSharedPreferences("iKhayaSupport", Context.MODE_PRIVATE).getString("GUEST_SUPPORT_ID", "");
                if (!guestId.isEmpty()) {
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
                                showPhoneNotification(context, "New Reply from iKhaya Support", cleanPreview(gMsgText), LoginActivity.class, ("guest_" + gMsgId).hashCode());
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
                                showPhoneNotification(context, "New Message from " + sender, cleanPreview(text), UserSelectionActivity.class, ("msg_" + msgId).hashCode());
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
                            
                            if (!savedStatus.isEmpty() && !savedStatus.equals(status)) {
                                anyUnread = true;
                                showPhoneNotification(context, "Application " + status,
                                        "Your application for " + propName + " is now " + status + ".", ApplicationsActivity.class, ("app_" + appId).hashCode());
                            }
                            statePrefs.edit().putString("APP_STATUS_" + appId, status).apply();

                            // Rent Reminder for Approved / Active Leases
                            if (("APPROVED".equals(status) || "ACTIVE".equals(status)) && !statePrefs.getBoolean("RENT_REMINDER_SENT_" + appId, false)) {
                                statePrefs.edit().putBoolean("RENT_REMINDER_SENT_" + appId, true).apply();
                                anyUnread = true;
                                showPhoneNotification(context, "Upcoming Rent & Lease Reminder",
                                        "Reminder: Your monthly rent for " + propName + " is due on the 1st of the month.", NotificationsActivity.class, ("rent_" + appId).hashCode());
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
                            if (!lAppId.isEmpty() && !lAppId.equals(savedLAppId)) {
                                anyUnread = true;
                                statePrefs.edit().putString("LAST_LANDLORD_APP_ID", lAppId).apply();
                                showPhoneNotification(context, "New Rental Application",
                                        "A tenant applied for " + aObj.optString("property_name", "your listing") + ".",
                                        ApplicationsActivity.class, ("lapp_" + lAppId).hashCode());
                            }
                        }
                    }
                    conn.disconnect();
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
                }

                if (anyUnread) {
                    markUnreadDot(context, true);
                }
            } catch (Exception ignored) {}
        });
    }

    private static String cleanPreview(String text) {
        if (text == null) return "";
        if (text.startsWith("[IMAGE_BASE64]:") || text.startsWith("[IMAGE")) return "📷 Sent a photo";
        if (text.startsWith("[PDF_FILE]:")) return "📄 Sent a PDF document";
        return text;
    }
}
