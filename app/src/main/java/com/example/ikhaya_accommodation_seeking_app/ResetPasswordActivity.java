package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText edtNewPassword, edtConfirmPassword;
    private Button btnReset;
    private View btnBack;
    private android.widget.ImageButton btnToggleNewPassword, btnToggleConfirmPassword;
    
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private boolean isResetting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        btnBack = findViewById(R.id.btnBack);
        edtNewPassword = findViewById(R.id.edtNewPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnReset = findViewById(R.id.btnChangePassword);
        
        btnToggleNewPassword = findViewById(R.id.btnToggleNewPassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);

        btnBack.setOnClickListener(v -> finish());
        
        setupPasswordToggle(btnToggleNewPassword, edtNewPassword, 1);
        setupPasswordToggle(btnToggleConfirmPassword, edtConfirmPassword, 2);

        btnReset.setOnClickListener(v -> {
            if (isResetting) return;
            
            String newPassword = edtNewPassword.getText().toString().trim();
            String confirmPassword = edtConfirmPassword.getText().toString().trim();

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
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

            isResetting = true;
            btnReset.setEnabled(false);

            String accessToken = getIntent().getStringExtra("ACCESS_TOKEN");
            if (accessToken == null || accessToken.isEmpty()) {
                accessToken = SupabaseHelper.getAccessToken(this);
            }

            if (accessToken == null || accessToken.isEmpty()) {
                isResetting = false;
                btnReset.setEnabled(true);
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
                return;
            }

            performPasswordReset(accessToken, newPassword);
        });
    }

    private void performPasswordReset(String accessToken, String newPassword) {
        executor.execute(() -> {
            HttpURLConnection updateConnection = null;
            try {
                updateConnection = SupabaseHelper.createConnection(
                        "/auth/v1/user",
                        "PUT",
                        accessToken
                );

                JSONObject updateBody = new JSONObject();
                updateBody.put("password", newPassword);

                SupabaseHelper.writeJson(updateConnection, updateBody);

                int code = updateConnection.getResponseCode();
                
                mainHandler.post(() -> {
                    isResetting = false;
                    btnReset.setEnabled(true);

                    if (code >= 200 && code < 300) {
                        Toast.makeText(this, R.string.password_changed_successfully, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to update password. Code: " + code, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    isResetting = false;
                    btnReset.setEnabled(true);
                    Toast.makeText(this, "Network error updating password.", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (updateConnection != null) {
                    updateConnection.disconnect();
                }
            }
        });
    }

    private void showMessage(int resId) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show();
    }
    
    private void setupPasswordToggle(android.widget.ImageButton toggleBtn, EditText editText, int type) {
        if (toggleBtn == null || editText == null) return;
        toggleBtn.setOnClickListener(v -> {
            int cursorPosition = editText.getSelectionStart();
            boolean isVisible = false;
            
            if (type == 1) {
                isNewPasswordVisible = !isNewPasswordVisible;
                isVisible = isNewPasswordVisible;
            } else if (type == 2) {
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
}