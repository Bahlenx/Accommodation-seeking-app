package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

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
        return context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getString("access_token", null);
    }

    public static String getRefreshToken(Context context) {
        return context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getString("refresh_token", null);
    }

    public static String getUserId(Context context) {
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
        android.content.SharedPreferences sessionPrefs = activity.getSharedPreferences("iKhayaSession", android.content.Context.MODE_PRIVATE);
        String savedEmail = sessionPrefs.getString("USER_EMAIL", "");

        android.widget.EditText reasonInput = new android.widget.EditText(activity);
        reasonInput.setHint("Reason for deleting account (shared with Admin)");
        reasonInput.setPadding(48, 32, 48, 32);

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Delete Account")
                .setMessage("Enter your reason for deleting your account. A 6-digit OTP will be sent to your email to confirm permanent deletion.")
                .setView(reasonInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send OTP", (dialog, which) -> {
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
            try {
                if ((emailToUse == null || emailToUse.isEmpty()) && token != null && !token.isEmpty()) {
                    java.net.HttpURLConnection userConn = createConnection("/auth/v1/user", "GET", token);
                    if (userConn.getResponseCode() >= 200 && userConn.getResponseCode() < 300) {
                        org.json.JSONObject userJson = new org.json.JSONObject(readResponse(userConn));
                        emailToUse = userJson.optString("email", "");
                    }
                    userConn.disconnect();
                }

                if (emailToUse == null || emailToUse.isEmpty()) {
                    activity.runOnUiThread(() ->
                            android.widget.Toast.makeText(activity, "Could not find account email. Please log in again.", android.widget.Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                java.net.HttpURLConnection otpConn = createConnection("/auth/v1/otp", "POST", null);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("email", emailToUse);
                body.put("create_user", false);
                writeJson(otpConn, body);
                int code = otpConn.getResponseCode();
                otpConn.disconnect();

                final String finalEmail = emailToUse;
                activity.runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        android.widget.Toast.makeText(activity, "Verification OTP sent to " + finalEmail, android.widget.Toast.LENGTH_SHORT).show();
                        android.content.Intent intent = new android.content.Intent(activity, PasswordResetOtpActivity.class);
                        intent.putExtra("USER_EMAIL", finalEmail);
                        intent.putExtra("IS_DELETE_ACCOUNT", true);
                        intent.putExtra("DELETE_REASON", reason);
                        activity.startActivity(intent);
                    } else {
                        android.widget.Toast.makeText(activity, "Failed to send OTP (Code " + code + ")", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        android.widget.Toast.makeText(activity, "Network error while requesting OTP.", android.widget.Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    public static void saveSession(Context context, String accessToken, String refreshToken, String userId, String userName, String userRole) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE).edit();
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
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
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
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Case-insensitive check on profiles
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

                // Fallback check on Users table if empty
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
}