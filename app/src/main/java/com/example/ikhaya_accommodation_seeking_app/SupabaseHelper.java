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

public class SupabaseHelper {

    private static final String SUPABASE_URL =
            "https://wdtazumjjurumrhklwgv.supabase.co";

    /*
     * IMPORTANT:
     * Put your existing Supabase PUBLISHABLE key here.
     *
     * Do NOT use a service_role key inside the Android app.
     */
    private static final String SUPABASE_KEY =
            "sb_publishable_SgXZRNFBL4tswVtapc3pFQ_qpD-DijB";


    // =========================================================
    // BASIC SUPABASE INFORMATION
    // =========================================================

    public static String getSupabaseUrl() {
        return SUPABASE_URL;
    }

    public static String getSupabaseKey() {
        return SUPABASE_KEY;
    }


    // =========================================================
    // SESSION
    // =========================================================

    public static String getAccessToken(Context context) {

        return context
                .getSharedPreferences(
                        "iKhayaSession",
                        Context.MODE_PRIVATE
                )
                .getString(
                        "access_token",
                        null
                );
    }


    public static String getUserId(Context context) {

        return context
                .getSharedPreferences(
                        "iKhayaSession",
                        Context.MODE_PRIVATE
                )
                .getString(
                        "user_id",
                        null
                );
    }


    public static void clearSession(Context context) {

        context
                .getSharedPreferences(
                        "iKhayaSession",
                        Context.MODE_PRIVATE
                )
                .edit()
                .clear()
                .apply();
    }


    // =========================================================
    // NORMAL SUPABASE REST CONNECTION
    // =========================================================

    public static HttpURLConnection createConnection(
            String endpoint,
            String method,
            String accessToken
    ) throws Exception {

        String finalUrl;

        if (endpoint.startsWith("http://")
                || endpoint.startsWith("https://")) {

            finalUrl = endpoint;

        } else {

            if (endpoint.startsWith("/")) {

                finalUrl =
                        SUPABASE_URL
                                + endpoint;

            } else {

                finalUrl =
                        SUPABASE_URL
                                + "/"
                                + endpoint;
            }
        }

        URL url =
                new URL(finalUrl);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);

        connection.setRequestProperty(
                "apikey",
                SUPABASE_KEY
        );

        if (accessToken != null
                && !accessToken.isEmpty()) {

            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + accessToken
            );
        }

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                20000
        );

        if ("POST".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {

            connection.setDoOutput(true);
        }

        return connection;
    }


    // =========================================================
    // SUPABASE STORAGE CONNECTION
    // =========================================================

    public static HttpURLConnection createStorageConnection(
            String bucket,
            String imagePath,
            String method,
            String accessToken
    ) throws Exception {

        String safePath =
                imagePath.replace(
                        " ",
                        "%20"
                );

        String finalUrl =
                SUPABASE_URL
                        + "/storage/v1/object/"
                        + bucket
                        + "/"
                        + safePath;

        URL url =
                new URL(finalUrl);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(
                method
        );

        connection.setRequestProperty(
                "apikey",
                SUPABASE_KEY
        );

        if (accessToken != null
                && !accessToken.isEmpty()) {

            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + accessToken
            );
        }

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setRequestProperty(
                "Content-Type",
                "application/octet-stream"
        );

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                30000
        );

        if ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {

            connection.setDoOutput(true);
        }

        return connection;
    }


    // =========================================================
    // PUBLIC STORAGE URL
    // =========================================================

    public static String getPublicStorageUrl(
            String bucket,
            String imagePath
    ) {

        String safePath =
                imagePath.replace(
                        " ",
                        "%20"
                );

        return SUPABASE_URL
                + "/storage/v1/object/public/"
                + bucket
                + "/"
                + safePath;
    }


    // =========================================================
    // READ RESPONSE
    // =========================================================

    public static String readResponse(
            HttpURLConnection connection
    ) {

        try {

            InputStream inputStream;

            int responseCode =
                    connection.getResponseCode();

            if (responseCode >= 200
                    && responseCode < 400) {

                inputStream =
                        connection.getInputStream();

            } else {

                inputStream =
                        connection.getErrorStream();

                if (inputStream == null) {

                    return "HTTP Error "
                            + responseCode;
                }
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    inputStream,
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder response =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {

                response.append(line);
            }

            reader.close();

            return response.toString();

        } catch (Exception e) {

            return "Connection error: "
                    + e.getMessage();
        }
    }


    // =========================================================
    // WRITE JSON
    // =========================================================

    public static void writeJson(
            HttpURLConnection connection,
            JSONObject json
    ) throws Exception {

        connection.setDoOutput(true);

        byte[] data =
                json.toString()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        try (OutputStream outputStream =
                     connection.getOutputStream()) {

            outputStream.write(data);
            outputStream.flush();
        }
    }


    // =========================================================
    // URL ENCODING
    // =========================================================

    public static String encode(
            String value
    ) {

        try {

            return URLEncoder.encode(
                    value,
                    "UTF-8"
            ).replace(
                    "+",
                    "%20"
            );

        } catch (Exception e) {

            return value;
        }
    }

    // =========================================================
    // ROLE COLOR CODING
    // =========================================================

    public static int getRoleTextColor(String role) {
        if (role == null) return android.graphics.Color.parseColor("#495057");
        switch (role.toLowerCase()) {
            case "landlord": return android.graphics.Color.parseColor("#2E7D32");
            case "tenant":
            case "resident": return android.graphics.Color.parseColor("#1565C0");
            case "admin": return android.graphics.Color.parseColor("#C62828");
            default: return android.graphics.Color.parseColor("#495057");
        }
    }

    public static int getRoleBgColor(String role) {
        if (role == null) return android.graphics.Color.parseColor("#E9ECEF");
        switch (role.toLowerCase()) {
            case "landlord": return android.graphics.Color.parseColor("#E8F5E9");
            case "tenant":
            case "resident": return android.graphics.Color.parseColor("#E3F2FD");
            case "admin": return android.graphics.Color.parseColor("#FFEBEE");
            default: return android.graphics.Color.parseColor("#E9ECEF");
        }
    }
}