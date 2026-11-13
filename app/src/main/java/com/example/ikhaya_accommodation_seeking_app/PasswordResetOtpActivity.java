package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.HttpURLConnection;

public class PasswordResetOtpActivity extends AppCompatActivity {

    private static final String TAG = "PasswordResetOtp";

    private EditText otp1, otp2, otp3, otp4, otp5, otp6;
    private Button verifyBtn, btnSendOtp;
    private RadioGroup rgOtpMethod;
    private RadioButton rbEmailOtp, rbSmsOtp;

    private String email;
    private android.os.CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_reset_otp);

        ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        otp1 = findViewById(R.id.otp_num_1);
        otp2 = findViewById(R.id.otp_num_2);
        otp3 = findViewById(R.id.otp_num_3);
        otp4 = findViewById(R.id.otp_num_4);
        otp5 = findViewById(R.id.otp_num_5);
        otp6 = findViewById(R.id.otp_num_6);

        verifyBtn = findViewById(R.id.button);
        btnSendOtp = findViewById(R.id.btnSendOtp);

        rgOtpMethod = findViewById(R.id.rgOtpMethod);
        rbEmailOtp = findViewById(R.id.rbEmailOtp);
        rbSmsOtp = findViewById(R.id.rbSmsOtp);

        email = getIntent().getStringExtra("USER_EMAIL");
        
        android.view.View btnSupportMail = findViewById(R.id.btnSupportMail);
        if (btnSupportMail != null) {
            btnSupportMail.setOnClickListener(v -> {
                String userHint = email != null ? email : "";
                SupabaseHelper.openAdminSupportChat(this, userHint);
            });
        }

        if (rbSmsOtp != null) {
            rbSmsOtp.setVisibility(View.GONE);
        }
        if (rbEmailOtp != null) {
            rbEmailOtp.setChecked(true);
            rbEmailOtp.setText("Recovery Email (" + email + ")");
        }

        setupOtpJumps(otp1, otp2);
        setupOtpJumps(otp2, otp3);
        setupOtpJumps(otp3, otp4);
        setupOtpJumps(otp4, otp5);
        setupOtpJumps(otp5, otp6);
        setupOtpJumps(otp6, null);

        btnSendOtp.setOnClickListener(v -> sendRecoveryOtp());
        verifyBtn.setOnClickListener(v -> verifyRecoveryOtp());

        sendRecoveryOtp();
    }

    private void setupOtpJumps(EditText current, EditText next) {
        current.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 1 && next != null) {
                    next.requestFocus();
                } else if (s.length() == 0 && current != otp1) {
                    // move back logic can be handled via key listener, keep simple here
                }
            }
        });
        current.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (current.getText().toString().isEmpty()) {
                    if (current == otp6) otp5.requestFocus();
                    else if (current == otp5) otp4.requestFocus();
                    else if (current == otp4) otp3.requestFocus();
                    else if (current == otp3) otp2.requestFocus();
                    else if (current == otp2) otp1.requestFocus();
                    return true;
                }
            }
            return false;
        });
    }

    private void startResendCooldown() {
        btnSendOtp.setEnabled(false);
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendTimer = new android.os.CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnSendOtp.setText("Resend Code in " + (millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                btnSendOtp.setEnabled(true);
                btnSendOtp.setText("Resend Recovery Code");
            }
        }.start();
    }

    private void sendRecoveryOtp() {
        startResendCooldown();
        new Thread(() -> {
            try {
                HttpURLConnection connection = SupabaseHelper.createConnection("/auth/v1/recover", "POST", null);
                JSONObject body = new JSONObject();
                body.put("email", email);
                SupabaseHelper.writeJson(connection, body);
                
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (code >= 400) {
                    connection = SupabaseHelper.createConnection("/auth/v1/otp", "POST", null);
                    JSONObject fallbackBody = new JSONObject();
                    fallbackBody.put("email", email);
                    fallbackBody.put("create_user", false);
                    SupabaseHelper.writeJson(connection, fallbackBody);
                    code = connection.getResponseCode();
                    response = SupabaseHelper.readResponse(connection);
                    connection.disconnect();
                }

                final int finalCode = code;
                final String finalResponse = response;

                runOnUiThread(() -> {
                    if (finalCode >= 200 && finalCode < 300) {
                        Toast.makeText(this, "Recovery code sent!", Toast.LENGTH_SHORT).show();
                    } else {
                        if (resendTimer != null) resendTimer.cancel();
                        btnSendOtp.setEnabled(true);
                        btnSendOtp.setText("Send Recovery Code");
                        android.util.Log.e("PasswordResetOtp", "Failed to send code: " + finalResponse);
                        Toast.makeText(this, "Could not send verification code right now. Please wait a moment and try again.", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (resendTimer != null) resendTimer.cancel();
                    btnSendOtp.setEnabled(true);
                    btnSendOtp.setText("Send Recovery Code");
                    android.util.Log.e("PasswordResetOtp", "Error sending code", e);
                    Toast.makeText(this, "Could not send verification code right now. Please wait a moment and try again.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void verifyRecoveryOtp() {
        String enteredCode = otp1.getText().toString().trim() +
                otp2.getText().toString().trim() +
                otp3.getText().toString().trim() +
                otp4.getText().toString().trim() +
                otp5.getText().toString().trim() +
                otp6.getText().toString().trim();

        if (enteredCode.length() < 6) {
            Toast.makeText(this, R.string.msg_enter_all_digits, Toast.LENGTH_SHORT).show();
            return;
        }

        verifyBtn.setEnabled(false);
        new Thread(() -> {
            try {
                HttpURLConnection connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                JSONObject body = new JSONObject();
                body.put("token", enteredCode);
                String otpType = getIntent().getBooleanExtra("IS_DELETE_ACCOUNT", false) ? "email" : "recovery";
                body.put("type", otpType);
                body.put("email", email);
                SupabaseHelper.writeJson(connection, body);
                
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (code >= 400) {
                    connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                    body.put("type", "recovery");
                    SupabaseHelper.writeJson(connection, body);
                    code = connection.getResponseCode();
                    response = SupabaseHelper.readResponse(connection);
                    connection.disconnect();
                    
                    if (code >= 400) {
                        connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                        body.put("type", "magiclink");
                        SupabaseHelper.writeJson(connection, body);
                        code = connection.getResponseCode();
                        response = SupabaseHelper.readResponse(connection);
                        connection.disconnect();
                    }
                }

                final int finalCode = code;
                final String finalResponse = response;

                runOnUiThread(() -> {
                    verifyBtn.setEnabled(true);
                    if (finalCode >= 200 && finalCode < 300) {
                        try {
                            JSONObject data = new JSONObject(finalResponse);
                            String accessToken = data.optString("access_token", "");
                            String refreshToken = data.optString("refresh_token", "");
                            JSONObject user = data.optJSONObject("user");
                            String userId = user != null ? user.optString("id", "") : "";

                            SupabaseHelper.saveSession(this, accessToken, refreshToken, userId, null, null);

                            boolean isDeleteAccount = getIntent().getBooleanExtra("IS_DELETE_ACCOUNT", false);
                            if (isDeleteAccount) {
                                String deleteReason = getIntent().getStringExtra("DELETE_REASON");
                                performPermanentAccountDeletion(accessToken, userId, email, deleteReason);
                                return;
                            }

                            Toast.makeText(this, R.string.msg_verified_successfully, Toast.LENGTH_SHORT).show();
                            
                            Intent intent = new Intent(PasswordResetOtpActivity.this, ResetPasswordActivity.class);
                            intent.putExtra("USER_EMAIL", email);
                            intent.putExtra("ACCESS_TOKEN", accessToken);
                            startActivity(intent);
                            finish();
                            
                        } catch (Exception e) {
                            Toast.makeText(this, R.string.error_parse_otp_response, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, R.string.msg_invalid_otp, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    verifyBtn.setEnabled(true);
                    Toast.makeText(this, getString(R.string.error_verifying_otp_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void performPermanentAccountDeletion(String token, String verifiedUserId, String userEmail, String reason) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            String uid = (verifiedUserId != null && !verifiedUserId.isEmpty())
                    ? verifiedUserId
                    : SupabaseHelper.getUserId(this);
            String useToken = (token != null && !token.isEmpty())
                    ? token
                    : SupabaseHelper.getAccessToken(this);

            try {
                // 1. Log the deletion reason to Admin Reports so Admins can review it
                HttpURLConnection reportConn = SupabaseHelper.createConnection("/rest/v1/reports", "POST", useToken);
                JSONObject reportBody = new JSONObject();
                reportBody.put("reporter_id", uid);
                reportBody.put("reporter_email", userEmail != null ? userEmail : "");
                reportBody.put("issue_type", "ACCOUNT DELETED");
                reportBody.put("description", "User deleted account. Reason: " + (reason != null ? reason : "No reason provided"));
                reportBody.put("status", "RESOLVED");
                SupabaseHelper.writeJson(reportConn, reportBody);
                reportConn.getResponseCode();
                reportConn.disconnect();
            } catch (Exception ignored) {}

            try {
                // 2. Delete user profile rows from Supabase
                if (uid != null && !uid.isEmpty()) {
                    HttpURLConnection delProfile = SupabaseHelper.createConnection(
                            "/rest/v1/profiles?firebase_uid=eq." + java.net.URLEncoder.encode(uid, "UTF-8"),
                            "DELETE", useToken);
                    delProfile.getResponseCode();
                    delProfile.disconnect();

                    HttpURLConnection delUser = SupabaseHelper.createConnection(
                            "/rest/v1/Users?user_id=eq." + java.net.URLEncoder.encode(uid, "UTF-8"),
                            "DELETE", useToken);
                    delUser.getResponseCode();
                    delUser.disconnect();
                }
            } catch (Exception ignored) {}

            runOnUiThread(() -> {
                SupabaseHelper.clearSession(PasswordResetOtpActivity.this);
                getSharedPreferences("iKhayaSession", MODE_PRIVATE).edit().clear().apply();
                Toast.makeText(PasswordResetOtpActivity.this, "Your account has been permanently deleted.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(PasswordResetOtpActivity.this, OuterMainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }
}