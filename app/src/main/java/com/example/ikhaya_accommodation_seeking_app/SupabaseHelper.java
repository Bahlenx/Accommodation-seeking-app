package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    public static String getUserId(Context context) {
        return context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .getString("user_id", null);
    }

    public static void clearSession(Context context) {
        context
                .getSharedPreferences("iKhayaSession", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    /**
     * Executes a true HTTP PATCH request using OkHttp.
     * Safely handles developer bypass by omitting invalid Bearer headers when no user session exists.
     */
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

        // Only send Authorization header if we have a real JWT session token (starts with 'ey')
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
}