
package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText edtCurrentPassword;
    private EditText edtNewPassword;
    private EditText edtConfirmPassword;

    private android.view.View btnBack;
    private Button btnChangePassword;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private boolean changingPassword = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        edtCurrentPassword =
                findViewById(R.id.edtCurrentPassword);

        edtNewPassword =
                findViewById(R.id.edtNewPassword);

        edtConfirmPassword =
                findViewById(R.id.edtConfirmPassword);

        btnBack = findViewById(R.id.btnBack);

        btnChangePassword =
                findViewById(R.id.btnChangePassword);

        if (edtCurrentPassword == null
                || edtNewPassword == null
                || edtConfirmPassword == null
                || btnBack == null
                || btnChangePassword == null) {

            Toast.makeText(
                    this,
                    R.string.password_screen_error,
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        btnBack.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(
                v -> changePassword()
        );
    }

    private void changePassword() {
        if (changingPassword) {
            return;
        }

        String currentPassword =
                edtCurrentPassword.getText().toString();

        String newPassword =
                edtNewPassword.getText().toString();

        String confirmPassword =
                edtConfirmPassword.getText().toString();

        if (currentPassword.trim().isEmpty()
                || newPassword.trim().isEmpty()
                || confirmPassword.trim().isEmpty()) {

            showMessage(R.string.password_fill_all_fields);
            return;
        }

        if (newPassword.length() < 8) {
            showMessage(R.string.password_minimum_length);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showMessage(R.string.passwords_do_not_match);
            return;
        }

        if (currentPassword.equals(newPassword)) {
            showMessage(R.string.password_must_be_different);
            return;
        }

        changingPassword = true;
        btnChangePassword.setEnabled(false);

        UserProfileLoader.load(this, (name, email, phone) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (email == null || email.trim().isEmpty()) {
                changingPassword = false;
                btnChangePassword.setEnabled(true);

                showMessage(R.string.password_email_missing);
                return;
            }

            performPasswordChange(
                    email.trim(),
                    currentPassword,
                    newPassword
            );
        });
    }

    private void performPasswordChange(
            String email,
            String currentPassword,
            String newPassword
    ) {
        executor.execute(() -> {
            HttpURLConnection verifyConnection = null;
            HttpURLConnection updateConnection = null;

            try {
                verifyConnection =
                        SupabaseHelper.createConnection(
                                "/auth/v1/token?grant_type=password",
                                "POST",
                                null
                        );

                JSONObject verifyBody = new JSONObject();

                verifyBody.put("email", email);
                verifyBody.put("password", currentPassword);

                SupabaseHelper.writeJson(
                        verifyConnection,
                        verifyBody
                );

                int verifyCode =
                        verifyConnection.getResponseCode();

                String verifyResponse =
                        SupabaseHelper.readResponse(
                                verifyConnection
                        );

                if (verifyCode < 200 || verifyCode >= 300) {
                    showMessage(
                            R.string.password_current_incorrect
                    );
                    return;
                }

                JSONObject session =
                        new JSONObject(verifyResponse);

                String accessToken =
                        session.optString("access_token", "");

                if (accessToken.isEmpty()) {
                    showMessage(
                            R.string.password_session_missing
                    );
                    return;
                }

                updateConnection =
                        SupabaseHelper.createConnection(
                                "/auth/v1/user",
                                "PUT",
                                accessToken
                        );

                JSONObject updateBody = new JSONObject();

                updateBody.put("password", newPassword);

                SupabaseHelper.writeJson(
                        updateConnection,
                        updateBody
                );

                int updateCode =
                        updateConnection.getResponseCode();

                String updateResponse =
                        SupabaseHelper.readResponse(
                                updateConnection
                        );

                if (updateCode >= 200 && updateCode < 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        edtCurrentPassword.setText("");
                        edtNewPassword.setText("");
                        edtConfirmPassword.setText("");

                        Toast.makeText(
                                this,
                                R.string.password_changed_successfully,
                                Toast.LENGTH_LONG
                        ).show();

                        finish();
                    });

                } else {
                    android.util.Log.e(
                            "ChangePassword",
                            "Supabase password update failed: "
                                    + updateCode
                                    + " "
                                    + updateResponse
                    );

                    showMessage(
                            R.string.password_update_failed
                    );
                }

            } catch (Exception e) {
                android.util.Log.e(
                        "ChangePassword",
                        "Password update error",
                        e
                );

                showMessage(
                        R.string.password_connection_error
                );

            } finally {
                if (verifyConnection != null) {
                    verifyConnection.disconnect();
                }

                if (updateConnection != null) {
                    updateConnection.disconnect();
                }

                runOnUiThread(() -> {
                    changingPassword = false;

                    if (!isFinishing() && !isDestroyed()) {
                        btnChangePassword.setEnabled(true);
                    }
                });
            }
        });
    }

    private void showMessage(int messageResource) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(
                        this,
                        messageResource,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
