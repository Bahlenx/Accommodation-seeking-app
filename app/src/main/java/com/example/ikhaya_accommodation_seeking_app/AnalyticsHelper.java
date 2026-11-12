package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsHelper {

    private static final String TAG = "iKhayaAnalytics";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void init(Context context) {
        Log.d(TAG, "Analytics initialized with Supabase backend.");
    }

    public static void logLogin(Context context, String role) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("role", role);
            logEvent(context, "user_login", null, meta);
        } catch (Exception ignored) {}
    }

    public static void logEvent(Context context, String eventName, String propertyId, JSONObject metadata) {
        String token = SupabaseHelper.getAccessToken(context);
        String userId = SupabaseHelper.getUserId(context);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/analytics_events", "POST", token);
                conn.setRequestProperty("Prefer", "return=minimal");

                JSONObject body = new JSONObject();
                body.put("event_name", eventName);
                if (userId != null && !userId.isEmpty()) body.put("user_uid", userId);
                if (propertyId != null && !propertyId.isEmpty()) body.put("property_id", propertyId);
                if (metadata != null) body.put("metadata", metadata);

                SupabaseHelper.writeJson(conn, body);
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, "Failed to log event: " + eventName, e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static void logListingView(Context context, String listingId, String title) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("title", title);
            logEvent(context, "property_view", listingId, meta);
        } catch (Exception ignored) {}
    }

    public static void logSearch(Context context, String query, String propertyType) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("query", query);
            meta.put("type", propertyType != null ? propertyType : "all");
            logEvent(context, "search_performed", null, meta);
        } catch (Exception ignored) {}
    }

    public static void logViewingBooked(Context context, String listingId) {
        logEvent(context, "viewing_scheduled", listingId, null);
    }

    public static void logAdminAction(Context context, String actionType, String propertyTitle) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("action", actionType);
            meta.put("property_title", propertyTitle);
            logEvent(context, "admin_moderation", null, meta);
        } catch (Exception ignored) {}
    }
}
