
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public final class TenantNotificationState {

    private static final String PREFS = "ikhaya_tenant_notification_v2";

    private TenantNotificationState() {
    }

    private static String key(String userId, String kind) {
        return userId + "_" + kind;
    }

    private static Set<String> getIds(JSONArray rows, String kind) {
        Set<String> ids = new HashSet<>();

        if (rows == null) {
            return ids;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);

            if (row == null) {
                continue;
            }

            String id = row.optString("id", "");

            if (id.isEmpty()) {
                continue;
            }

            if ("applications".equals(kind)) {
                String status = row.optString("status", "");
                ids.add(id + ":" + status.toUpperCase());
            } else {
                ids.add(id);
            }
        }

        return ids;
    }

    public static synchronized boolean hasNew(
            Context context,
            String userId,
            String kind,
            JSONArray rows
    ) {
        if (rows == null || userId == null || userId.isEmpty()) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );

        String storageKey = key(userId, kind);
        Set<String> current = getIds(rows, kind);

        if (!prefs.contains(storageKey)) {
            prefs.edit()
                    .putStringSet(storageKey, new HashSet<>(current))
                    .commit();

            return false;
        }

        Set<String> seen = new HashSet<>(
                prefs.getStringSet(
                        storageKey,
                        new HashSet<>()
                )
        );

        for (String id : current) {
            if (!seen.contains(id)) {
                return true;
            }
        }

        return false;
    }

    public static synchronized void markSeen(
            Context context,
            String userId,
            String kind,
            JSONArray rows
    ) {
        if (rows == null || userId == null || userId.isEmpty()) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );

        String storageKey = key(userId, kind);

        Set<String> seen = new HashSet<>(
                prefs.getStringSet(
                        storageKey,
                        new HashSet<>()
                )
        );

        seen.addAll(getIds(rows, kind));

        prefs.edit()
                .putStringSet(storageKey, seen)
                .commit();
    }
}
