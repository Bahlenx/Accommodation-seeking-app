package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;

public class WorkflowAutomationHelper {
    private static final String TAG = "WorkflowAutomation";

    // Safely PATCH with automatic CHECK-constraint fallback (e.g., "Cancelled" -> "Declined")
    public static boolean patchStatusWithFallback(
            Context context, String endpoint, String primaryStatus, String fallbackStatus, String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", primaryStatus);
            SupabaseHelper.patch(endpoint, body, token);
            return true;
        } catch (Exception primaryErr) {
            if (fallbackStatus != null && !fallbackStatus.isEmpty()
                    && primaryErr.getMessage() != null
                    && (primaryErr.getMessage().contains("23514")
                    || primaryErr.getMessage().contains("check constraint")
                    || primaryErr.getMessage().contains("HTTP 400"))) {
                try {
                    JSONObject fb = new JSONObject();
                    fb.put("status", fallbackStatus);
                    SupabaseHelper.patch(endpoint, fb, token);
                    return true;
                } catch (Exception ignored) {}
            }
            Log.w(TAG, "patchStatusWithFallback failed on " + endpoint, primaryErr);
            return false;
        }
    }

    // Safely POST with automatic PGRST204 retry (strips optional columns if live DB doesn't have them)
    public static int postWithOptionalFallback(
            String endpoint, JSONObject fullBody, String[] optionalKeys, String token, StringBuilder outResponse) throws Exception {
        int code = executePost(endpoint, fullBody, token, outResponse);
        if (code == 400 && outResponse != null && outResponse.toString().contains("PGRST204") && optionalKeys != null) {
            JSONObject stripped = new JSONObject(fullBody.toString());
            for (String key : optionalKeys) stripped.remove(key);
            outResponse.setLength(0);
            code = executePost(endpoint, stripped, token, outResponse);
        }
        return code;
    }

    private static int executePost(String endpoint, JSONObject body, String token, StringBuilder outResponse) throws Exception {
        HttpURLConnection conn = SupabaseHelper.createConnection(endpoint, "POST", token);
        conn.setRequestProperty("Prefer", "return=representation");
        SupabaseHelper.writeJson(conn, body);
        int code = conn.getResponseCode();
        if (outResponse != null) {
            outResponse.append(SupabaseHelper.readResponse(conn));
        }
        conn.disconnect();
        return code;
    }

    public static boolean isViewingDateInPast(String viewingDateStr) {
        if (viewingDateStr == null || viewingDateStr.isEmpty()) return false;
        try {
            String ymd = viewingDateStr.length() > 10 ? viewingDateStr.substring(0, 10) : viewingDateStr;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
            java.util.Date vDate = sdf.parse(ymd);
            if (vDate == null) return false;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return vDate.before(cal.getTime());
        } catch (Exception e) {
            return false;
        }
    }

    public static void syncListingViewingAvailability(Context context, String listingId, String token) {
        if (listingId == null || listingId.isEmpty()) return;
        try {
            // 1. Do not overwrite RENTED, SOLD, DELETED, or archived listings
            HttpURLConnection lConn = SupabaseHelper.createConnection(
                    "/rest/v1/listings?select=status,availability,is_archived&id=eq." + SupabaseHelper.encode(listingId),
                    "GET", token);
            if (lConn.getResponseCode() >= 200 && lConn.getResponseCode() < 300) {
                JSONArray lArr = new JSONArray(SupabaseHelper.readResponse(lConn));
                lConn.disconnect();
                if (lArr.length() == 0) return;
                JSONObject lst = lArr.getJSONObject(0);
                String lstStatus = lst.optString("status", "");
                String lstAvail = lst.optString("availability", "");
                boolean archived = lst.optBoolean("is_archived", false);
                if (archived
                        || "RENTED".equalsIgnoreCase(lstStatus) || "SOLD".equalsIgnoreCase(lstStatus)
                        || "DELETED".equalsIgnoreCase(lstStatus)
                        || "Rented".equalsIgnoreCase(lstAvail) || "Sold".equalsIgnoreCase(lstAvail)) {
                    return;
                }
            } else {
                lConn.disconnect();
            }

            // 2. Recompute from non-expired active viewings only
            HttpURLConnection vConn = SupabaseHelper.createConnection(
                    "/rest/v1/viewings?select=status,viewing_date&listing_id=eq." + SupabaseHelper.encode(listingId),
                    "GET", token);
            if (vConn.getResponseCode() >= 200 && vConn.getResponseCode() < 300) {
                JSONArray arr = new JSONArray(SupabaseHelper.readResponse(vConn));
                boolean hasConfirmed = false;
                boolean hasPending = false;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject v = arr.getJSONObject(i);
                    if (isViewingDateInPast(v.optString("viewing_date", ""))) continue;
                    String st = v.optString("status", "");
                    if ("Confirmed".equalsIgnoreCase(st)) hasConfirmed = true;
                    else if ("Pending".equalsIgnoreCase(st) || "Pending Host".equalsIgnoreCase(st)) hasPending = true;
                }
                String newAvail = hasConfirmed ? "Under Viewing" : (hasPending ? "Pending Viewing" : "Available");
                JSONObject patch = new JSONObject();
                patch.put("availability", newAvail);
                SupabaseHelper.patch("/rest/v1/listings?id=eq." + SupabaseHelper.encode(listingId), patch, token);
            }
            vConn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "syncListingViewingAvailability failed", e);
        }
    }

    public static void openAddToCalendarIntent(
            Context context, String propertyName, String viewingDateStr,
            String location, double lat, double lon) {
        long startMillis = parseViewingStartMillis(viewingDateStr);
        long endMillis = startMillis + (60L * 60L * 1000L); // 1-hour viewing block

        String cleanProp = (propertyName == null || propertyName.trim().isEmpty()) ? "Property" : propertyName.trim();
        StringBuilder desc = new StringBuilder("Scheduled viewing via iKhaya for ").append(cleanProp);
        if (viewingDateStr != null && !viewingDateStr.isEmpty()) {
            desc.append("\nSlot: ").append(viewingDateStr);
        }
        if (lat != 0.0 || lon != 0.0) {
            desc.append("\nMap Pin: https://maps.google.com/?q=").append(lat).append(",").append(lon);
        }

        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_INSERT)
                .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, "Scheduled viewing for " + cleanProp)
                .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, location != null ? location : "")
                .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc.toString())
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                .putExtra(android.provider.CalendarContract.Events.HAS_ALARM, 1);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "No calendar app found on this device.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private static long parseViewingStartMillis(String viewingDateStr) {
        if (viewingDateStr == null || viewingDateStr.isEmpty()) return System.currentTimeMillis();
        try {
            String ymd = viewingDateStr.length() > 10 ? viewingDateStr.substring(0, 10) : viewingDateStr;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
            java.util.Date vDate = sdf.parse(ymd);
            if (vDate == null) return System.currentTimeMillis();
            
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(vDate);
            
            if (viewingDateStr.contains("Morning") || viewingDateStr.contains("09:00")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 9);
            } else if (viewingDateStr.contains("Midday") || viewingDateStr.contains("11:00") || viewingDateStr.contains("12:00")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
            } else if (viewingDateStr.contains("Afternoon") || viewingDateStr.contains("14:00")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 14);
            } else if (viewingDateStr.contains("Late") || viewingDateStr.contains("16:00")) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 16);
            } else {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 10);
            }
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public static void ensureDirectChatUnlocked(Context context, String userA, String userB, String senderName, String token) {
        try {
            String encA = SupabaseHelper.encode(userA);
            String encB = SupabaseHelper.encode(userB);
            String q = "or=(and(sender_id.eq." + encA + ",receiver_id.eq." + encB + ")," +
                    "and(sender_id.eq." + encB + ",receiver_id.eq." + encA + "))";
            HttpURLConnection cConn = SupabaseHelper.createConnection("/rest/v1/chat_requests?select=id,status&" + q, "GET", token);
            if (cConn.getResponseCode() >= 200 && cConn.getResponseCode() < 300) {
                JSONArray arr = new JSONArray(SupabaseHelper.readResponse(cConn));
                if (arr.length() > 0) {
                    JSONObject req = arr.getJSONObject(0);
                    String stat = req.optString("status", "");
                    if (!"accepted".equalsIgnoreCase(stat)) {
                        JSONObject p = new JSONObject();
                        p.put("status", "accepted");
                        SupabaseHelper.patch("/rest/v1/chat_requests?id=eq." + req.optString("id", ""), p, token);
                    }
                } else {
                    JSONObject p = new JSONObject();
                    p.put("sender_id", userA);
                    p.put("receiver_id", userB);
                    p.put("sender_name", senderName);
                    p.put("status", "accepted");
                    postWithOptionalFallback("/rest/v1/chat_requests", p, new String[]{"sender_name"}, token, new StringBuilder());
                }
            }
            cConn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "ensureDirectChatUnlocked failed", e);
        }
    }
}