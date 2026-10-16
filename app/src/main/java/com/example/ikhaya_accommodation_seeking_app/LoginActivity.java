package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
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
    private TextView forgotPasswordText;
    private ImageButton togglePasswordButton;

    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Connect the views from activity_login.xml
        ImageButton backButton = findViewById(R.id.back_button);

        emailInput = findViewById(R.id.editTextText);
        passwordInput = findViewById(R.id.editTextText2);
        loginBtn = findViewById(R.id.button2);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);

        // Password visibility button.
        // This ID must also exist in activity_login.xml.
        togglePasswordButton = findViewById(R.id.btnTogglePassword);

        // Back button
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                Intent intent = new Intent(
                        LoginActivity.this,
                        OuterMainActivity.class
                );

                intent.setFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                startActivity(intent);
                finish();
            });
        }

        // Show or hide password
        if (togglePasswordButton != null && passwordInput != null) {
            togglePasswordButton.setContentDescription("Show password");

            togglePasswordButton.setOnClickListener(v -> {
                int cursorPosition = passwordInput.getSelectionStart();

                if (isPasswordVisible) {
                    passwordInput.setTransformationMethod(
                            PasswordTransformationMethod.getInstance()
                    );

                    isPasswordVisible = false;
                    togglePasswordButton.setContentDescription("Show password");
                } else {
                    passwordInput.setTransformationMethod(
                            HideReturnsTransformationMethod.getInstance()
                    );

                    isPasswordVisible = true;
                    togglePasswordButton.setContentDescription("Hide password");
                }

                int textLength = passwordInput.getText().length();
                int safeCursorPosition = Math.max(
                        0,
                        Math.min(cursorPosition, textLength)
                );

                passwordInput.setSelection(safeCursorPosition);
            });
        }

        // Login button
        if (loginBtn != null) {
            loginBtn.setOnClickListener(v -> {
                String email = emailInput.getText()
                        .toString()
                        .trim();

                String password = passwordInput.getText()
                        .toString();

                if (email.isEmpty()) {
                    emailInput.setError("Enter your email address.");
                    emailInput.requestFocus();
                    return;
                }

                if (password.isEmpty()) {
                    passwordInput.setError("Enter your password.");
                    passwordInput.requestFocus();
                    return;
                }

                loginBtn.setEnabled(false);
                signInWithSupabase(email, password);
            });
        }

        // Forgot password
        if (forgotPasswordText != null) {
            forgotPasswordText.setOnClickListener(v ->
                    sendPasswordReset()
            );
        }
    }

    /**
     * Signs in using Supabase Auth.
     */
    private void signInWithSupabase(String email, String password) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = SupabaseHelper.createConnection(
                        "/auth/v1/token?grant_type=password",
                        "POST",
                        null
                );

                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                SupabaseHelper.writeJson(connection, body);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONObject data = new JSONObject(response);

                    String accessToken =
                            data.optString("access_token", "");

                    String refreshToken =
                            data.optString("refresh_token", "");

                    JSONObject user = data.optJSONObject("user");

                    if (accessToken.isEmpty() || user == null) {
                        showLoginError(
                                "No Supabase session was returned."
                        );
                        return;
                    }

                    String userId = user.optString("id", "");

                    if (userId.isEmpty()) {
                        showLoginError(
                                "No user ID was returned."
                        );
                        return;
                    }

                    // Save the login session.
                    getSharedPreferences(
                            "iKhayaSession",
                            MODE_PRIVATE
                    ).edit()
                            .putString("access_token", accessToken)
                            .putString("refresh_token", refreshToken)
                            .putString("user_id", userId)
                            .apply();

                    // Load the user's role and open the correct dashboard.
                    getUserRole(accessToken, userId);

                } else {
                    showLoginError(
                            "Login failed: "
                                    + extractErrorMessage(response)
                    );
                }

            } catch (Exception e) {
                showLoginError(
                        "Could not connect to Supabase. Check your connection."
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Requests a password-reset email from Supabase.
     */
    private void sendPasswordReset() {
        String email = emailInput.getText()
                .toString()
                .trim();

        if (email.isEmpty()) {
            emailInput.setError("Enter your email address first.");
            emailInput.requestFocus();
            return;
        }

        if (loginBtn != null) {
            loginBtn.setEnabled(false);
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = SupabaseHelper.createConnection(
                        "/auth/v1/recover",
                        "POST",
                        null
                );

                JSONObject body = new JSONObject();
                body.put("email", email);

                SupabaseHelper.writeJson(connection, body);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                runOnUiThread(() -> {
                    if (loginBtn != null) {
                        loginBtn.setEnabled(true);
                    }

                    if (responseCode >= 200 && responseCode < 300) {
                        new androidx.appcompat.app.AlertDialog.Builder(
                                LoginActivity.this
                        )
                                .setTitle("Check your email")
                                .setMessage(
                                        "If an account exists for this email, "
                                                + "a password-reset email will be sent. "
                                                + "Check your inbox and spam folder."
                                )
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        Toast.makeText(
                                LoginActivity.this,
                                "Could not request a password reset: "
                                        + extractErrorMessage(response),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (loginBtn != null) {
                        loginBtn.setEnabled(true);
                    }

                    Toast.makeText(
                            LoginActivity.this,
                            "Could not connect to request a password reset.",
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

    /**
     * Loads the user's role from the profiles table.
     */
    private void getUserRole(String accessToken, String userId) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/profiles?select=role"
                                + "&firebase_uid=eq."
                                + URLEncoder.encode(userId, "UTF-8")
                                + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONArray data = new JSONArray(response);

                    if (data.length() > 0) {
                        JSONObject profile = data.getJSONObject(0);

                        String role = profile.optString(
                                "role",
                                "tenant"
                        );

                        routeUser(role);

                    } else {
                        showLoginError(
                                "Your account profile was not found."
                        );
                    }

                } else {
                    showLoginError(
                            "Could not load your account profile: "
                                    + extractErrorMessage(response)
                    );
                }

            } catch (Exception e) {
                showLoginError(
                        "Could not load your account profile."
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Sends landlords and tenants to their respective screens.
     */
    private void routeUser(String role) {
        runOnUiThread(() -> {
            Intent intent;

            if ("landlord".equalsIgnoreCase(role)) {
                intent = new Intent(
                        LoginActivity.this,
                        LandlordDashboardActivity.class
                );
            } else {
                intent = new Intent(
                        LoginActivity.this,
                        HomeActivity.class
                );
            }

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
        });
    }

    /**
     * Displays an error and allows the user to try again.
     */
    private void showLoginError(String message) {
        runOnUiThread(() -> {
            if (loginBtn != null) {
                loginBtn.setEnabled(true);
            }

            Toast.makeText(
                    LoginActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    /**
     * Extracts a readable message from a Supabase error response.
     */
    private String extractErrorMessage(String response) {
        try {
            JSONObject error = new JSONObject(response);

            String message = error.optString("msg", "");

            if (message.isEmpty()) {
                message = error.optString("message", "");
            }

            if (message.isEmpty()) {
                message = error.optString("error_description", "");
            }

            if (message.isEmpty()) {
                message = error.optString("error", "");
            }

            if (!message.isEmpty()) {
                return message;
            }

        } catch (Exception ignored) {
            // The response was not JSON.
        }

        return response == null || response.trim().isEmpty()
                ? "Unknown error"
                : response;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}