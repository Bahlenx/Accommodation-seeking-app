
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public final class LandlordNotificationState {

    private LandlordNotificationState() {
    }

    private static String prefsName(String kind) {
        return "ikhaya_seen_" + kind;
    }

    private static String key(String kind, String userId) {
        return "seen_ids_" + userId;
    }

    public static synchronized Set<String> seen(
            Context context,
            String kind,
            String userId
    ) {
        SharedPreferences prefs = context.getSharedPreferences(
                prefsName(kind),
                Context.MODE_PRIVATE
        );

        String raw = prefs.getString(key(kind, userId), "");
        Set<String> ids = new HashSet<>();

        if (raw != null && !raw.isEmpty()) {
            for (String id : raw.split("\\|")) {
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }

        return ids;
    }

    public static synchronized boolean hasUnseen(
            Context context,
            String kind,
            String userId,
            JSONArray rows
    ) {
        if (rows == null || userId == null || userId.isEmpty()) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                prefsName(kind),
                Context.MODE_PRIVATE
        );

        String storageKey = key(kind, userId);

        if (!prefs.contains(storageKey)) {
            markSeen(context, kind, userId, rows);
            return false;
        }

        Set<String> seenIds = seen(context, kind, userId);

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);

            if (row == null) {
                continue;
            }

            String id = row.optString("id", "");

            if (!id.isEmpty() && !seenIds.contains(id)) {
                return true;
            }
        }

        return false;
    }

    public static synchronized void markSeen(
            Context context,
            String kind,
            String userId,
            JSONArray rows
    ) {
        if (rows == null || userId == null || userId.isEmpty()) {
            return;
        }

        Set<String> ids = seen(context, kind, userId);

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);

            if (row == null) {
                continue;
            }

            String id = row.optString("id", "");

            if (!id.isEmpty()) {
                ids.add(id);
            }
        }

        context.getSharedPreferences(
                prefsName(kind),
                Context.MODE_PRIVATE
        ).edit().putString(
                key(kind, userId),
                TextUtils.join("|", ids)
        ).commit();
    }
}
