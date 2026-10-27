package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChangePasswordActivity extends AppCompatActivity {

    private static final String TAG = "ChangePassword";

    private EditText edtCurrentPassword;
    private EditText edtNewPassword;
    private EditText edtConfirmPassword;

    private View btnBack;
    private Button btnChangePassword;
    private android.widget.ImageButton btnToggleCurrentPassword, btnToggleNewPassword, btnToggleConfirmPassword;
    
    private boolean isCurrentPasswordVisible = false;
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private boolean changingPassword = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        edtCurrentPassword = findViewById(R.id.edtCurrentPassword);
        edtNewPassword = findViewById(R.id.edtNewPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnBack = findViewById(R.id.btnBack);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        
        btnToggleCurrentPassword = findViewById(R.id.btnToggleCurrentPassword);
        btnToggleNewPassword = findViewById(R.id.btnToggleNewPassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);

        if (edtCurrentPassword == null
                || edtNewPassword == null
                || edtConfirmPassword == null
                || btnBack == null
                || btnChangePassword == null) {

            android.widget.Toast.makeText(this, R.string.password_screen_error, android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnBack.setOnClickListener(v -> finish());
        btnChangePassword.setOnClickListener(v -> changePassword());
        
        setupPasswordToggle(btnToggleCurrentPassword, edtCurrentPassword, 1);
        setupPasswordToggle(btnToggleNewPassword, edtNewPassword, 2);
        setupPasswordToggle(btnToggleConfirmPassword, edtConfirmPassword, 3);
    }
    
    private void setupPasswordToggle(android.widget.ImageButton toggleBtn, EditText editText, int type) {
        if (toggleBtn == null || editText == null) return;
        toggleBtn.setOnClickListener(v -> {
            int cursorPosition = editText.getSelectionStart();
            boolean isVisible = false;
            
            if (type == 1) {
                isCurrentPasswordVisible = !isCurrentPasswordVisible;
                isVisible = isCurrentPasswordVisible;
            } else if (type == 2) {
                isNewPasswordVisible = !isNewPasswordVisible;
                isVisible = isNewPasswordVisible;
            } else if (type == 3) {
                isConfirmPasswordVisible = !isConfirmPasswordVisible;
                isVisible = isConfirmPasswordVisible;
            }

            if (isVisible) {
                editText.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
            } else {
                editText.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
            }

            int textLength = editText.getText().length();
            int safeCursorPosition = Math.max(0, Math.min(cursorPosition, textLength));
            editText.setSelection(safeCursorPosition);
        });
    }

    private void changePassword() {
        if (changingPassword) {
            return;
        }

        String currentPassword = edtCurrentPassword.getText().toString();
        String newPassword = edtNewPassword.getText().toString();
        String confirmPassword = edtConfirmPassword.getText().toString();

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

            performPasswordChange(email.trim(), currentPassword, newPassword);
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
                verifyConnection = SupabaseHelper.createConnection(
                        "/auth/v1/token?grant_type=password",
                        "POST",
                        null
                );

                JSONObject verifyBody = new JSONObject();
                verifyBody.put("email", email);
                verifyBody.put("password", currentPassword);

                SupabaseHelper.writeJson(verifyConnection, verifyBody);

                int verifyCode = verifyConnection.getResponseCode();
                String verifyResponse = SupabaseHelper.readResponse(verifyConnection);

                if (verifyCode < 200 || verifyCode >= 300) {
                    showMessage(R.string.password_current_incorrect);
                    return;
                }

                JSONObject session = new JSONObject(verifyResponse);
                String accessToken = session.optString("access_token", "");

                if (accessToken.isEmpty()) {
                    showMessage(R.string.password_session_missing);
                    return;
                }

                updateConnection = SupabaseHelper.createConnection(
                        "/auth/v1/user",
                        "PUT",
                        accessToken
                );

                JSONObject updateBody = new JSONObject();
                updateBody.put("password", newPassword);

                SupabaseHelper.writeJson(updateConnection, updateBody);

                int updateCode = updateConnection.getResponseCode();
                String updateResponse = SupabaseHelper.readResponse(updateConnection);

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
                    Log.e(TAG, "Supabase password update failed: " + updateCode + " " + updateResponse);
                    showMessage(R.string.password_update_failed);
                }

            } catch (Exception e) {
                Log.e(TAG, "Password update error", e);
                showMessage(R.string.password_connection_error);

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
                Toast.makeText(this, messageResource, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}