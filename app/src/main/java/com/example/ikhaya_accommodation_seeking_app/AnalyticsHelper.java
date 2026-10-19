package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;

public class AnalyticsHelper {

    private static FirebaseAnalytics mFirebaseAnalytics;

    public static void init(Context context) {
        if (mFirebaseAnalytics == null) {
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        }
    }

    public static void logLogin(Context context, String role) {
        init(context);
        Bundle bundle = new Bundle();
        bundle.putString("user_role", role);
        bundle.putString(FirebaseAnalytics.Param.METHOD, "Supabase");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);
    }

    public static void logAdminAction(Context context, String actionType, String propertyTitle) {
        init(context);
        Bundle bundle = new Bundle();
        bundle.putString("action_type", actionType); // e.g., "approved", "rejected", "revoked"
        bundle.putString("property", propertyTitle);
        mFirebaseAnalytics.logEvent("admin_moderation", bundle);
    }
}