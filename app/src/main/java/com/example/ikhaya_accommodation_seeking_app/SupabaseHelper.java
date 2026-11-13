package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseHelper {

    private static final String SUPABASE_URL =
            "https://wdtazumjjurumrhklwgv.supabase.co";

    private static final String SUPABASE_KEY =
            "sb_publishable_SgXZRNFBL4tswVtapc3pFQ_qpD-DijB";

    private static final OkHttpClient httpClient = new OkHttpClient();

    public static String getSupabaseUrl() {
        return SUPABASE_URL;
    }

    public static String getSupabaseKey() {
        return SUPABASE_KEY;
    }

    public static String getAccessToken(Context context) {
        if (context == null) return null;
        return context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getString("access_token", null);
    }

    public static String getRefreshToken(Context context) {
        if (context == null) return null;
        return context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getString("refresh_token", null);
    }

    public static String getUserId(Context context) {
        if (context == null) return null;
        return context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getString("user_id", null);
    }

    private static final long THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L;

    
    public static void showValidationError(android.content.Context context, android.widget.EditText field, String message) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
        if (field != null) {
            android.text.SpannableString span = new android.text.SpannableString(message);
            span.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.WHITE), 0, span.length(), 0);
            field.setError(span);
            field.requestFocus();
        }
    }

    public static void openAdminSupportChat(android.content.Context context, String userHint) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("iKhayaSupport", android.content.Context.MODE_PRIVATE);
        String guestId = prefs.getString("GUEST_SUPPORT_ID", "");
        if (guestId == null || guestId.isEmpty()) {
            guestId = "guest_" + java.util.UUID.randomUUID().toString();
            prefs.edit().putString("GUEST_SUPPORT_ID", guestId).apply();
        }

        String cleanHint = (userHint != null) ? userHint.trim() : "";
        String displayName = !cleanHint.isEmpty()
                ? "Guest (" + cleanHint + ")"
                : "Guest (" + guestId + ")";

        android.content.Intent intent = new android.content.Intent(context, ChatActivity.class);
        intent.putExtra("IS_ADMIN_SUPPORT", true);
        intent.putExtra("GUEST_ID", guestId);
        intent.putExtra("SENDER_ID", guestId);
        intent.putExtra("SENDER_NAME", displayName);
        intent.putExtra("SENDER_ROLE", "GUEST");
        intent.putExtra("RECEIVER_ID", "ADMIN_SUPPORT");
        intent.putExtra("RECEIVER_NAME", "iKhaya Admin Support");
        intent.putExtra("SUPPORT_USER_HINT", cleanHint);
        context.startActivity(intent);
    }

    public static void promptDeleteAccountWithOtp(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        android.content.SharedPreferences sessionPrefs = activity.getSharedPreferences("iKhayaSession", android.content.Context.MODE_PRIVATE);
        String savedEmail = sessionPrefs.getString("USER_EMAIL", "");

        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(22 * density);

        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(android.graphics.Color.parseColor("#FAF6F2"));
        cardBg.setCornerRadius(18 * density);
        root.setBackground(cardBg);

        android.widget.TextView badge = new android.widget.TextView(activity);
        badge.setText("ACCOUNT REMOVAL");
        badge.setTextColor(android.graphics.Color.parseColor("#B91C1C"));
        badge.setTextSize(11f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(badge);

        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("Delete Your iKhaya Account");
        title.setTextColor(android.graphics.Color.parseColor("#1F1614"));
        title.setTextSize(19f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, Math.round(4 * density), 0, Math.round(6 * density));
        root.addView(title);

        android.widget.TextView subtitle = new android.widget.TextView(activity);
        subtitle.setText("Please share why you are leaving so our Admin team can review your feedback:");
        subtitle.setTextColor(android.graphics.Color.parseColor("#786F6B"));
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, 0, 0, Math.round(12 * density));
        root.addView(subtitle);

        android.widget.EditText reasonInput = new android.widget.EditText(activity);
        reasonInput.setHint("Type your reason for deleting your account...");
        reasonInput.setTextColor(android.graphics.Color.parseColor("#1F1614"));
        reasonInput.setHintTextColor(android.graphics.Color.parseColor("#9E9E9E"));
        reasonInput.setTextSize(14f);
        reasonInput.setMinLines(2);
        int inputPad = Math.round(14 * density);
        reasonInput.setPadding(inputPad, inputPad, inputPad, inputPad);
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(android.graphics.Color.WHITE);
        inputBg.setCornerRadius(12 * density);
        inputBg.setStroke(Math.round(1 * density), android.graphics.Color.parseColor("#D6CBC2"));
        reasonInput.setBackground(inputBg);
        root.addView(reasonInput);

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = "User requested account deletion";
                    }
                    sendDeleteAccountOtp(activity, savedEmail, reason);
                })
                .show();
    }

    private static void sendDeleteAccountOtp(android.app.Activity activity, String cachedEmail, String reason) {
        String token = getAccessToken(activity);
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            String emailToUse = cachedEmail;
            int otpCode = -1;
            try {
                if ((emailToUse == null || emailToUse.isEmpty()) && token != null && !token.isEmpty()) {
                    java.net.HttpURLConnection userConn = createConnection("/auth/v1/user", "GET", token);
                    if (userConn.getResponseCode() >= 200 && userConn.getResponseCode() < 300) {
                        org.json.JSONObject userJson = new org.json.JSONObject(readResponse(userConn));
                        emailToUse = userJson.optString("email", "");
                    }
                    userConn.disconnect();
                }

                if (emailToUse != null && !emailToUse.isEmpty()) {
                    java.net.HttpURLConnection otpConn = createConnection("/auth/v1/otp", "POST", null);
                    org.json.JSONObject body = new org.json.JSONObject();
                    body.put("email", emailToUse);
                    body.put("create_user", false);
                    writeJson(otpConn, body);
                    otpCode = otpConn.getResponseCode();
                    otpConn.disconnect();
                }
            } catch (Exception ignored) {}

            final String finalEmail = emailToUse != null ? emailToUse : "";
            final boolean otpSent = (otpCode >= 200 && otpCode < 300);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                showConfirmDeleteDialog(activity, finalEmail, reason, otpSent);
            });
        });
    }

    private static void showConfirmDeleteDialog(android.app.Activity activity, String email, String reason, boolean otpSent) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(22 * density);

        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(android.graphics.Color.parseColor("#FAF6F2"));
        cardBg.setCornerRadius(18 * density);
        root.setBackground(cardBg);

        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("Confirm Account Deletion");
        title.setTextColor(android.graphics.Color.parseColor("#1F1614"));
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, Math.round(6 * density));
        root.addView(title);

        android.widget.TextView msg = new android.widget.TextView(activity);
        msg.setText(otpSent
                ? "Enter the 6-digit OTP sent to " + email + " (or type DELETE) to permanently delete your account:"
                : "Enter your 6-digit OTP or type DELETE below to permanently delete your account:");
        msg.setTextColor(android.graphics.Color.parseColor("#786F6B"));
        msg.setTextSize(13f);
        msg.setPadding(0, 0, 0, Math.round(12 * density));
        root.addView(msg);

        android.widget.EditText codeInput = new android.widget.EditText(activity);
        codeInput.setHint("6-digit OTP or DELETE");
        codeInput.setTextColor(android.graphics.Color.parseColor("#1F1614"));
        codeInput.setHintTextColor(android.graphics.Color.parseColor("#9E9E9E"));
        codeInput.setTextSize(15f);
        int inputPad = Math.round(14 * density);
        codeInput.setPadding(inputPad, inputPad, inputPad, inputPad);
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(android.graphics.Color.WHITE);
        inputBg.setCornerRadius(12 * density);
        inputBg.setStroke(Math.round(1 * density), android.graphics.Color.parseColor("#D6CBC2"));
        codeInput.setBackground(inputBg);
        root.addView(codeInput);

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete Account", (d, w) -> {
                    String entered = codeInput.getText().toString().trim();
                    if (entered.isEmpty()) {
                        android.widget.Toast.makeText(activity, "Please enter the OTP or type DELETE.", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deleteAccountCompletely(activity, getUserId(activity), getAccessToken(activity), email, reason);
                })
                .show();
    }

    public static void deleteAccountCompletely(android.app.Activity activity, String uid, String token, String email, String reason) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            String loggedInUid = getUserId(activity);
            final boolean isSelfDelete = (uid == null || uid.isEmpty() || uid.equals(loggedInUid));
            
            String targetUid = (uid != null && !uid.isEmpty()) ? uid : loggedInUid;
            String useToken = (token != null && !token.isEmpty()) ? token : getAccessToken(activity);

            try {
                java.net.HttpURLConnection repConn = createConnection("/rest/v1/reports", "POST", useToken);
                repConn.setRequestProperty("Prefer", "return=minimal");
                org.json.JSONObject repBody = new org.json.JSONObject();
                repBody.put("reporter_uid", targetUid);
                repBody.put("reporter_id", targetUid);
                repBody.put("reporter_name", (email != null && !email.isEmpty()) ? email : ("User " + targetUid));
                repBody.put("reporter_email", email != null ? email : "");
                repBody.put("reporter_role", isSelfDelete ? "USER" : "ADMIN_ACTION");
                repBody.put("issue_type", isSelfDelete ? "USER ACCOUNT DELETION" : "ADMIN ACCOUNT DELETION");
                repBody.put("reason", "ACCOUNT DELETED");
                repBody.put("description", reason != null ? reason : "User requested account deletion");
                repBody.put("status", isSelfDelete ? "open" : "RESOLVED");
                writeJson(repConn, repBody);
                repConn.getResponseCode();
                repConn.disconnect();
            } catch (Exception ignored) {}

            try {
                java.net.HttpURLConnection rpcConn = createConnection("/rest/v1/rpc/delete_ikhaya_user", "POST", useToken);
                org.json.JSONObject rpcBody = new org.json.JSONObject();
                rpcBody.put("target_uid", targetUid);
                rpcBody.put("delete_reason", reason != null ? reason : "User requested account deletion");
                rpcBody.put("user_email", email != null ? email : "");
                writeJson(rpcConn, rpcBody);
                rpcConn.getResponseCode();
                rpcConn.disconnect();
            } catch (Exception ignored) {}

            activity.runOnUiThread(() -> {
                if (isSelfDelete) {
                    clearSession(activity);
                    activity.getSharedPreferences("iKhayaSession", android.content.Context.MODE_PRIVATE).edit().clear().apply();
                    android.widget.Toast.makeText(activity, "Your account has been permanently deleted.", android.widget.Toast.LENGTH_LONG).show();
                    android.content.Intent intent = new android.content.Intent(activity, OuterMainActivity.class);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                } else {
                    android.widget.Toast.makeText(activity, "User account has been permanently deleted.", android.widget.Toast.LENGTH_LONG).show();
                    activity.recreate();
                }
            });
        });
    }

    public static void verifyActiveSessionOrKick(android.app.Activity activity) {
        String uid = getUserId(activity);
        String token = getAccessToken(activity);
        if (uid == null || uid.isEmpty() || token == null || token.isEmpty()) return;

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encUid = java.net.URLEncoder.encode(uid, "UTF-8");
                java.net.HttpURLConnection profConn = createConnection(
                        "/rest/v1/profiles?select=id,is_suspended&or=(id.eq." + encUid + ",firebase_uid.eq." + encUid + ")",
                        "GET", token);
                int code = profConn.getResponseCode();
                if (code >= 200 && code < 300) {
                    org.json.JSONArray arr = new org.json.JSONArray(readResponse(profConn));
                    profConn.disconnect();
                    boolean shouldKick = (arr.length() == 0);
                    String kickMsg = "Your account has been removed.";
                    if (!shouldKick) {
                        org.json.JSONObject prof = arr.getJSONObject(0);
                        if (prof.optBoolean("is_suspended", false)) {
                            shouldKick = true;
                            kickMsg = "Your account has been suspended by an administrator.";
                        }
                    }
                    if (shouldKick) {
                        final String finalMsg = kickMsg;
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            clearSession(activity);
                            android.widget.Toast.makeText(activity, finalMsg, android.widget.Toast.LENGTH_LONG).show();
                            android.content.Intent intent = new android.content.Intent(activity, OuterMainActivity.class);
                            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            activity.startActivity(intent);
                            activity.finish();
                        });
                    }
                } else {
                    profConn.disconnect();
                }
            } catch (Exception ignored) {}
        });
    }

    public static void saveSession(Context context, String accessToken, String refreshToken, String userId, String userName, String userRole) {
        SharedPreferences.Editor editor = context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE).edit();
        if (accessToken != null) editor.putString("access_token", accessToken);
        if (refreshToken != null) editor.putString("refresh_token", refreshToken);
        if (userId != null) editor.putString("user_id", userId);
        if (userName != null) editor.putString("USER_NAME", userName);
        if (userRole != null) editor.putString("USER_ROLE", userRole);

        long loginTimestamp = context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE).getLong("login_timestamp", 0);
        if (loginTimestamp == 0 || accessToken != null) {
            editor.putLong("login_timestamp", System.currentTimeMillis());
        }
        editor.apply();
    }

    public static boolean isSessionWithinThirtyDays(Context context) {
        long loginTimestamp = context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE).getLong("login_timestamp", 0);
        return (System.currentTimeMillis() - loginTimestamp) < THIRTY_DAYS_MS;
    }

    public static String refreshAccessToken(Context context) {
        String refreshToken = getRefreshToken(context);
        if (refreshToken == null || refreshToken.isEmpty()) {
            return getAccessToken(context);
        }

        try {
            HttpURLConnection connection = createConnection("/auth/v1/token?grant_type=refresh_token", "POST", null);
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            writeJson(connection, body);

            int responseCode = connection.getResponseCode();
            String response = readResponse(connection);
            connection.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                JSONObject data = new JSONObject(response);
                String newAccessToken = data.optString("access_token", "");
                String newRefreshToken = data.optString("refresh_token", "");
                if (!newAccessToken.isEmpty()) {
                    context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                            .edit()
                            .putString("access_token", newAccessToken)
                            .putString("refresh_token", newRefreshToken)
                            .apply();
                    return newAccessToken;
                }
            }
        } catch (Exception ignored) {
        }
        return getAccessToken(context);
    }

    public static void clearSession(Context context) {
        context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public static void patch(String endpoint, JSONObject body, String accessToken) throws Exception {
        String finalUrl = endpoint.startsWith("http")
                ? endpoint
                : SUPABASE_URL + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);

        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(body.toString(), mediaType);

        Request.Builder builder = new Request.Builder()
                .url(finalUrl)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Prefer", "return=minimal")
                .patch(requestBody);

        if (accessToken != null && accessToken.startsWith("ey")) {
            builder.addHeader("Authorization", "Bearer " + accessToken);
        } else {
            builder.addHeader("Authorization", "Bearer " + SUPABASE_KEY);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new Exception("HTTP " + response.code() + ": " + err);
            }
        }
    }

    public static HttpURLConnection createConnection(
            String endpoint,
            String method,
            String accessToken
    ) throws Exception {

        String finalUrl = endpoint.startsWith("http")
                ? endpoint
                : SUPABASE_URL + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);

        URL url = new URL(finalUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", SUPABASE_KEY);

        if (accessToken != null && accessToken.startsWith("ey")) {
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        } else {
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
        }

        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        if ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {
            connection.setDoOutput(true);
        }

        return connection;
    }

    public static HttpURLConnection createStorageConnection(
            String bucket,
            String imagePath,
            String method,
            String accessToken
    ) throws Exception {

        String safePath = imagePath.replace(" ", "%20");
        String finalUrl = SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + safePath;

        URL url = new URL(finalUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", SUPABASE_KEY);
        connection.setRequestProperty("x-upsert", "true");

        if (accessToken != null && accessToken.startsWith("ey")) {
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        } else {
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
        }

        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/octet-stream");

        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        if ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {
            connection.setDoOutput(true);
        }

        return connection;
    }

    public static String uploadStorageBytes(String bucket, String imagePath, byte[] data) throws Exception {
        Context ctx = getAppContext();
        String token = ctx != null ? getAccessToken(ctx) : null;

        HttpURLConnection conn = createStorageConnection(bucket, imagePath, "POST", token);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        String responseStr = readResponse(conn);
        conn.disconnect();

        if (responseCode >= 200 && responseCode < 300) {
            return getPublicStorageUrl(bucket, imagePath);
        } else {
            throw new Exception("Storage Upload Failed (HTTP " + responseCode + "): " + responseStr);
        }
    }

    public static String getPublicStorageUrl(String bucket, String imagePath) {
        String safePath = imagePath.replace(" ", "%20");
        return SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + safePath;
    }

    public static String readResponse(HttpURLConnection connection) {
        try {
            InputStream inputStream;
            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 400) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
                if (inputStream == null) {
                    return "HTTP Error " + responseCode;
                }
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();
        } catch (Exception e) {
            return "Connection error: " + e.getMessage();
        }
    }

    public static void writeJson(HttpURLConnection connection, JSONObject json) throws Exception {
        connection.setDoOutput(true);
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(data);
            outputStream.flush();
        }
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    public static void createNotification(String targetUid, String title, String message, String token) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = createConnection("/rest/v1/notifications", "POST", token);
                conn.setRequestProperty("Prefer", "return=minimal");

                JSONObject body = new JSONObject();
                body.put("user_uid", targetUid);
                body.put("title", title);
                body.put("message", message);

                writeJson(conn, body);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    Log.d("SupabaseHelper", "Notification created successfully for: " + targetUid);
                } else {
                    String err = readResponse(conn);
                    Log.e("SupabaseHelper", "Failed to insert notification. HTTP " + code + ": " + err);
                }
            } catch (Exception e) {
                Log.e("SupabaseHelper", "Failed to send notification", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static void notifyAdmins(String title, String message, String token) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = createConnection("/rest/v1/profiles?select=firebase_uid&role=ilike.admin", "GET", token);
                int code = conn.getResponseCode();

                HashSet<String> adminUids = new HashSet<>();

                if (code >= 200 && code < 300) {
                    String response = readResponse(conn);
                    JSONArray admins = new JSONArray(response);
                    for (int i = 0; i < admins.length(); i++) {
                        String uid = admins.optJSONObject(i).optString("firebase_uid", "");
                        if (!uid.isEmpty()) adminUids.add(uid);
                    }
                }
                conn.disconnect();

                if (adminUids.isEmpty()) {
                    conn = createConnection("/rest/v1/Users?select=id&user_role=ilike.admin", "GET", token);
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                        JSONArray legacyAdmins = new JSONArray(readResponse(conn));
                        for (int i = 0; i < legacyAdmins.length(); i++) {
                            String uid = legacyAdmins.optJSONObject(i).optString("id", "");
                            if (!uid.isEmpty()) adminUids.add(uid);
                        }
                    }
                }

                for (String adminUid : adminUids) {
                    createNotification(adminUid, title, message, token);
                }
            } catch (Exception e) {
                Log.e("SupabaseHelper", "Failed to notify admins", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public interface ProfileUpdateCallback {
        void onResult(boolean success, String error);
    }

    public interface ProfileFetchCallback {
        void onResult(JSONObject profileObj, String error);
    }

    public interface UpdateCallback {
        void onResult(boolean success, String error);
    }

    public static void updateListing(String listingId, JSONObject body, UpdateCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Context ctx = getAppContext();
                String token = ctx != null ? getAccessToken(ctx) : null;
                String endpoint = "/rest/v1/listings?id=eq." + encode(listingId);
                patch(endpoint, body, token);
                if (callback != null) callback.onResult(true, null);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false, e.getMessage());
            }
        });
    }

    public static String buildTenantDiscoverEndpoint(String categoryFilter, String locationQuery) {
        StringBuilder endpoint = new StringBuilder("/rest/v1/listings?");
        endpoint.append("select=*,profiles!inner(identity_status)&");
        endpoint.append("status=eq.APPROVED&");
        endpoint.append("is_archived=eq.false&");
        endpoint.append("profiles.identity_status=eq.VERIFIED");

        if (categoryFilter != null && !categoryFilter.isEmpty() && !"All".equalsIgnoreCase(categoryFilter)) {
            endpoint.append("&property_type=eq.").append(encode(categoryFilter));
        }

        if (locationQuery != null && !locationQuery.trim().isEmpty()) {
            endpoint.append("&location=ilike.*").append(encode(locationQuery.trim())).append("*");
        }

        endpoint.append("&order=created_at.desc");
        return endpoint.toString();
    }

    private static Context getAppContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method method = activityThreadClass.getMethod("currentApplication");
            return (Context) method.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static void updateProfile(String userId, JSONObject profilePayload, ProfileUpdateCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Context ctx = getAppContext();
                String token = ctx != null ? getAccessToken(ctx) : null;
                String endpoint = "/rest/v1/profiles?firebase_uid=eq." + encode(userId);
                patch(endpoint, profilePayload, token);
                if (callback != null) callback.onResult(true, null);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false, e.getMessage());
            }
        });
    }

    public static void fetchProfileById(String userId, ProfileFetchCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                Context ctx = getAppContext();
                String token = ctx != null ? getAccessToken(ctx) : null;
                String endpoint = "/rest/v1/profiles?select=*&firebase_uid=eq." + encode(userId) + "&limit=1";
                conn = createConnection(endpoint, "GET", token);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(readResponse(conn));
                    if (arr.length() > 0) {
                        if (callback != null) callback.onResult(arr.getJSONObject(0), null);
                        return;
                    }
                }
                if (conn != null) conn.disconnect();
                endpoint = "/rest/v1/profiles?select=*&id=eq." + encode(userId) + "&limit=1";
                conn = createConnection(endpoint, "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(readResponse(conn));
                    if (arr.length() > 0) {
                        if (callback != null) callback.onResult(arr.getJSONObject(0), null);
                        return;
                    }
                }
                if (callback != null) callback.onResult(null, "Profile not found");
            } catch (Exception e) {
                if (callback != null) callback.onResult(null, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}