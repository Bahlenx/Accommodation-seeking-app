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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class LoginActivity extends AppCompatActivity {
    private static final String SUPABASE_URL =
            "https://wdtazumjjurumrhklwgv.supabase.co";
    private static final String SUPABASE_KEY =
            "sb_publishable_SgXZRNFBL4tswVtapc3pFQ_qpD-DijB";
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Back button
        ImageButton backButton =
                findViewById(R.id.back_button);
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
        // Login fields
        emailInput =
                findViewById(R.id.editTextText);
        passwordInput =
                findViewById(R.id.editTextText2);
        loginBtn =
                findViewById(R.id.button2);
        if (loginBtn != null) {
            loginBtn.setOnClickListener(v -> {
                String email =
                        emailInput.getText()
                                .toString()
                                .trim();
                String password =
                        passwordInput.getText()
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
                signInWithSupabase(
                        email,
                        password
                );
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
                        SUPABASE_URL
                                + "/auth/v1/token?grant_type=password";
                URL url =
                        new URL(endpoint);
                connection =
                        (HttpURLConnection)
                                url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty(
                        "apikey",
                        SUPABASE_KEY
                );
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );
                connection.setDoOutput(true);
                JSONObject body =
                        new JSONObject();
                body.put(
                        "email",
                        email
                );
                body.put(
                        "password",
                        password
                );
                byte[] requestBody =
                        body.toString()
                                .getBytes("UTF-8");
                connection.getOutputStream()
                        .write(requestBody);
                int responseCode =
                        connection.getResponseCode();
                String response =
                        readResponse(
                                connection,
                                responseCode
                        );
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
                    
                    // Call the newly migrated security logging method
                    logDeviceSecurityEvent(userId, accessToken);

                    // Save session locally for later app requests
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

    private void logDeviceSecurityEvent(String userId, String accessToken) {
        try {
            // Fetch IP Address
            String ipAddress = "Unknown";
            try {
                URL ipUrl = new URL("https://api.ipify.org");
                HttpURLConnection ipConn = (HttpURLConnection) ipUrl.openConnection();
                ipConn.setConnectTimeout(5000);
                ipConn.setReadTimeout(5000);
                ipConn.setRequestMethod("GET");
                if (ipConn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(ipConn.getInputStream()));
                    ipAddress = reader.readLine();
                    reader.close();
                }
                ipConn.disconnect();
            } catch (Exception ignored) {
                // Ignore if it fails, IP will remain Unknown
            }

            // Push to Supabase
            String endpoint = SUPABASE_URL + "/rest/v1/security_logs";
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("manufacturer", android.os.Build.MANUFACTURER);
            body.put("model", android.os.Build.MODEL);
            body.put("os_version", android.os.Build.VERSION.RELEASE);
            body.put("time_zone", java.util.TimeZone.getDefault().getID());
            body.put("ip_address", ipAddress);

            connection.getOutputStream().write(body.toString().getBytes("UTF-8"));
            connection.getResponseCode(); // Execute the request
            connection.disconnect();

        } catch (Exception e) {
            android.util.Log.e("SupabaseSecurity", "Failed to log security event", e);
        }
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
                // Corrected endpoint per instructions: using "Users" table and "user_role" select
                String endpoint =
                        SUPABASE_URL
                                + "/rest/v1/Users"
                                + "?select=user_role"
                                + "&id=eq."
                                + encodedUserId
                                + "&limit=1";
                URL url =
                        new URL(endpoint);
                connection =
                        (HttpURLConnection)
                                url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty(
                        "apikey",
                        SUPABASE_KEY
                );
                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + accessToken
                );
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                int responseCode =
                        connection.getResponseCode();
                String response =
                        readResponse(
                                connection,
                                responseCode
                        );
                if (responseCode >= 200
                        && responseCode < 300) {
                    JSONArray data =
                            new JSONArray(response);
                    if (data.length() > 0) {
                        JSONObject profile =
                                data.getJSONObject(0);
                        String role =
                                profile.optString(
                                        "user_role",
                                        "Resident"
                                );
                        runOnUiThread(() ->
                                routeUser(role)
                        );
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
                                "Supabase profile error: "
                                        + responseCode
                                        + "\n"
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
    private String readResponse(
            HttpURLConnection connection,
            int responseCode
    ) {
        try {
            BufferedReader reader;
            if (responseCode >= 200
                    && responseCode < 300) {
                reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream()
                                )
                        );
            } else {
                if (connection.getErrorStream() == null) {
                    return "";
                }
                reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getErrorStream()
                                )
                        );
            }
            StringBuilder result =
                    new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }
    private String extractErrorMessage(
            String response
    ) {
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
    private void routeUser(String role) {
        Intent intent;
        if ("landlord".equalsIgnoreCase(role)) {
            intent =
                    new Intent(
                            LoginActivity.this,
                            LandlordDashboardActivity.class
                    );
        } else if ("admin".equalsIgnoreCase(role)) {
            intent =
                    new Intent(
                            LoginActivity.this,
                            HomeActivity.class
                    );
        } else {
            intent =
                    new Intent(
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
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}