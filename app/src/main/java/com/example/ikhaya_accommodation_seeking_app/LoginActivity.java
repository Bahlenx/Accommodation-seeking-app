package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ImageButton backButton = findViewById(R.id.back_button);

        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                Intent intent = new Intent(
                        LoginActivity.this,
                        OuterMainActivity.class
                );

                intent.setFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                startActivity(intent);
                finish();
            });
        }

        emailInput = findViewById(R.id.editTextText);
        passwordInput = findViewById(R.id.editTextText2);
        loginBtn = findViewById(R.id.button2);

        if (loginBtn != null) {
            loginBtn.setOnClickListener(v -> {

                String email = emailInput.getText()
                        .toString()
                        .trim();

                String password = passwordInput.getText()
                        .toString();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(
                            this,
                            "Please enter both Email and Password",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                loginBtn.setEnabled(false);

                Toast.makeText(
                        this,
                        "Logging in...",
                        Toast.LENGTH_SHORT
                ).show();

                signInWithSupabase(email, password);
            });
        }
    }

    private void signInWithSupabase(
            String email,
            String password
    ) {
        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/auth/v1/token?grant_type=password";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "POST",
                        null
                );

                connection.setDoOutput(true);

                JSONObject body = new JSONObject();

                body.put("email", email);
                body.put("password", password);

                SupabaseHelper.writeJson(
                        connection,
                        body
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode >= 200
                        && responseCode < 300) {

                    JSONObject data =
                            new JSONObject(response);

                    String accessToken =
                            data.optString(
                                    "access_token",
                                    ""
                            );

                    String refreshToken =
                            data.optString(
                                    "refresh_token",
                                    ""
                            );

                    JSONObject user =
                            data.optJSONObject("user");

                    if (accessToken.isEmpty()
                            || user == null) {

                        runOnUiThread(() -> {
                            loginBtn.setEnabled(true);

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Login succeeded but no Supabase session was returned.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });

                        return;
                    }

                    String userId =
                            user.optString(
                                    "id",
                                    ""
                            );

                    if (userId.isEmpty()) {

                        runOnUiThread(() -> {
                            loginBtn.setEnabled(true);

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Login failed because no user ID was returned.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });

                        return;
                    }

                    getSharedPreferences(
                            "iKhayaSession",
                            MODE_PRIVATE
                    )
                            .edit()
                            .putString(
                                    "access_token",
                                    accessToken
                            )
                            .putString(
                                    "refresh_token",
                                    refreshToken
                            )
                            .putString(
                                    "user_id",
                                    userId
                            )
                            .apply();

                    getUserRole(
                            accessToken,
                            userId
                    );

                } else {

                    runOnUiThread(() -> {

                        loginBtn.setEnabled(true);

                        Toast.makeText(
                                LoginActivity.this,
                                "Login failed: "
                                        + extractErrorMessage(response),
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

            } catch (Exception e) {

                runOnUiThread(() -> {

                    loginBtn.setEnabled(true);

                    Toast.makeText(
                            LoginActivity.this,
                            "Could not connect to Supabase: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void getUserRole(
            String accessToken,
            String userId
    ) {

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                String encodedUserId =
                        URLEncoder.encode(
                                userId,
                                "UTF-8"
                        );

                String endpoint =
                        "/rest/v1/profiles"
                                + "?select=role"
                                + "&firebase_uid=eq."
                                + encodedUserId
                                + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode >= 200
                        && responseCode < 300) {

                    JSONArray data =
                            new JSONArray(response);

                    if (data.length() > 0) {

                        JSONObject profile =
                                data.getJSONObject(0);

                        String role =
                                profile.optString(
                                        "role",
                                        "tenant"
                                );
                                
                        String fullName =
                                profile.optString(
                                        "full_name",
                                        "User"
                                );

                        getSharedPreferences("iKhayaSession", MODE_PRIVATE)
                                .edit()
                                .putString("USER_NAME", fullName)
                                .putString("USER_ROLE", role)
                                .apply();

                        routeUser(role);

                    } else {

                        runOnUiThread(() -> {

                            loginBtn.setEnabled(true);

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Your account profile was not found.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                } else {

                    runOnUiThread(() -> {

                        loginBtn.setEnabled(true);

                        Toast.makeText(
                                LoginActivity.this,
                                "Could not load your account profile: "
                                        + extractErrorMessage(response),
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

            } catch (Exception e) {

                runOnUiThread(() -> {

                    loginBtn.setEnabled(true);

                    Toast.makeText(
                            LoginActivity.this,
                            "Could not load your profile: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void routeUser(String role) {

        Intent intent;

        if ("landlord".equalsIgnoreCase(role)) {

            intent = new Intent(
                    LoginActivity.this,
                    LandlordDashboardActivity.class
            );

        } else if ("admin".equalsIgnoreCase(role)) {

            /*
             * Admin dashboard has not been supplied yet.
             * For now, send admin to HomeActivity.
             */
            intent = new Intent(
                    LoginActivity.this,
                    HomeActivity.class
            );

        } else {

            intent = new Intent(
                    LoginActivity.this,
                    HomeActivity.class
            );
        }

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private String extractErrorMessage(String response) {

        try {

            JSONObject error =
                    new JSONObject(response);

            String message =
                    error.optString(
                            "msg",
                            ""
                    );

            if (message.isEmpty()) {
                message =
                        error.optString(
                                "message",
                                ""
                        );
            }

            if (message.isEmpty()) {
                message =
                        error.optString(
                                "error_description",
                                ""
                        );
            }

            if (!message.isEmpty()) {
                return message;
            }

        } catch (Exception ignored) {
        }

        return response.isEmpty()
                ? "Unknown error"
                : response;
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executor.shutdown();
    }
}