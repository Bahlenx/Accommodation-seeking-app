
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UserProfileLoader {

    public interface Callback {
        void onLoaded(String name, String email, String phone);
    }

    private static final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private UserProfileLoader() {
    }

    public static void load(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();

        SharedPreferences prefs = appContext.getSharedPreferences(
                "iKhayaSession",
                Context.MODE_PRIVATE
        );

        FirebaseUser firebaseUser =
                FirebaseAuth.getInstance().getCurrentUser();

        String savedName = first(
                prefs.getString("USER_NAME", ""),
                prefs.getString("user_name", ""),
                prefs.getString("full_name", ""),
                prefs.getString("name", "")
        );

        String savedEmail = first(
                prefs.getString("USER_EMAIL", ""),
                prefs.getString("user_email", ""),
                prefs.getString("email", ""),
                prefs.getString("EMAIL", "")
        );

        String savedPhone = first(
                prefs.getString("USER_PHONE", ""),
                prefs.getString("user_phone", ""),
                prefs.getString("phone", ""),
                prefs.getString("phone_number", "")
        );

        String firebaseName = firebaseUser == null
                ? ""
                : clean(firebaseUser.getDisplayName());

        String firebaseEmail = firebaseUser == null
                ? ""
                : clean(firebaseUser.getEmail());

        String firebasePhone = firebaseUser == null
                ? ""
                : clean(firebaseUser.getPhoneNumber());

        String fallbackName = first(savedName, firebaseName);
        String fallbackEmail = first(savedEmail, firebaseEmail);
        String fallbackPhone = first(savedPhone, firebasePhone);

        String token = SupabaseHelper.getAccessToken(appContext);
        String userId = SupabaseHelper.getUserId(appContext);

        if (token == null || token.trim().isEmpty()) {
            deliver(
                    callback,
                    fallbackName,
                    fallbackEmail,
                    fallbackPhone
            );
            return;
        }

        final String lookupId = clean(userId);

        executor.execute(() -> {
            String name = fallbackName;
            String email = fallbackEmail;
            String phone = fallbackPhone;
            String resolvedId = lookupId;

            JSONObject authUser = getObject(
                    "/auth/v1/user",
                    token
            );

            if (authUser != null) {
                resolvedId = first(
                        authUser.optString("id", ""),
                        resolvedId
                );

                email = first(
                        authUser.optString("email", ""),
                        email
                );

                phone = first(
                        authUser.optString("phone", ""),
                        phone
                );

                JSONObject metadata =
                        authUser.optJSONObject("user_metadata");

                if (metadata != null) {
                    name = first(
                            metadata.optString("full_name", ""),
                            metadata.optString("name", ""),
                            name
                    );

                    phone = first(
                            metadata.optString("phone_number", ""),
                            metadata.optString("phone", ""),
                            phone
                    );
                }
            }

            JSONObject profile = null;

            if (!resolvedId.isEmpty()) {
                profile = getSingleRow(
                        "/rest/v1/profiles?select=*&firebase_uid=eq."
                                + SupabaseHelper.encode(resolvedId)
                                + "&limit=1",
                        token
                );

                if (profile == null) {
                    profile = getSingleRow(
                            "/rest/v1/profiles?select=*&id=eq."
                                    + SupabaseHelper.encode(resolvedId)
                                    + "&limit=1",
                            token
                    );
                }

                if (profile == null) {
                    profile = getSingleRow(
                            "/rest/v1/Users?select=*&id=eq."
                                    + SupabaseHelper.encode(resolvedId)
                                    + "&limit=1",
                            token
                    );
                }
            }

            if (profile == null && !email.isEmpty()) {
                profile = getSingleRow(
                        "/rest/v1/profiles?select=*&email=eq."
                                + SupabaseHelper.encode(email)
                                + "&limit=1",
                        token
                );
            }

            if (profile == null && !email.isEmpty()) {
                profile = getSingleRow(
                        "/rest/v1/Users?select=*&email=eq."
                                + SupabaseHelper.encode(email)
                                + "&limit=1",
                        token
                );
            }

            if (profile != null) {
                String fullName = first(
                        profile.optString("full_name", ""),
                        profile.optString("fullName", ""),
                        profile.optString("name", "")
                );

                if (fullName.isEmpty()) {
                    String firstName = first(
                            profile.optString("firstName", ""),
                            profile.optString("first_name", "")
                    );

                    String surname = first(
                            profile.optString("surname", ""),
                            profile.optString("last_name", "")
                    );

                    fullName = (firstName + " " + surname).trim();
                }

                name = first(fullName, name);

                email = first(
                        profile.optString("email", ""),
                        email
                );

                phone = first(
                        profile.optString("phone_number", ""),
                        profile.optString("phoneNumber", ""),
                        profile.optString("phone", ""),
                        phone
                );
            }

            if (!email.isEmpty()) {
                prefs.edit()
                        .putString("USER_EMAIL", email)
                        .apply();
            }

            if (!name.isEmpty()) {
                prefs.edit()
                        .putString("USER_NAME", name)
                        .apply();
            }

            if (!phone.isEmpty()) {
                prefs.edit()
                        .putString("USER_PHONE", phone)
                        .apply();
            }

            deliver(callback, name, email, phone);
        });
    }

    private static JSONObject getObject(
            String endpoint,
            String token
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    endpoint,
                    "GET",
                    token
            );

            int code = connection.getResponseCode();
            String response =
                    SupabaseHelper.readResponse(connection);

            if (code >= 200 && code < 300) {
                return new JSONObject(response);
            }

        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    private static JSONObject getSingleRow(
            String endpoint,
            String token
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    endpoint,
                    "GET",
                    token
            );

            int code = connection.getResponseCode();
            String response =
                    SupabaseHelper.readResponse(connection);

            if (code >= 200 && code < 300) {
                JSONArray rows = new JSONArray(response);

                if (rows.length() > 0) {
                    return rows.optJSONObject(0);
                }
            }

        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    private static void deliver(
            Callback callback,
            String name,
            String email,
            String phone
    ) {
        mainHandler.post(() ->
                callback.onLoaded(
                        clean(name),
                        clean(email),
                        clean(phone)
                )
        );
    }

    private static String first(String... values) {
        for (String value : values) {
            String cleaned = clean(value);

            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }

        return "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        if ("null".equalsIgnoreCase(cleaned)) {
            return "";
        }

        return cleaned;
    }
}
