package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.util.Log;

public class AnalyticsHelper {

    private static final String TAG = "iKhayaAnalytics";

    public static void init(Context context) {
        // Initialization no longer requires Firebase, but signature is kept intact.
        Log.d(TAG, "Analytics initialized.");
    }

    public static void logLogin(Context context, String role) {
        Log.d(TAG, "Event: LOGIN, role: " + role + ", method: Supabase");
    }

    public static void logAdminAction(Context context, String actionType, String propertyTitle) {
        Log.d(TAG, "Event: admin_moderation, action_type: " + actionType + ", property: " + propertyTitle);
    }
}
