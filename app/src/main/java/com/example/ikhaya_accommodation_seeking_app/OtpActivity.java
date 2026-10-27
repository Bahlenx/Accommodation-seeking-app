package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.net.HttpURLConnection;

public class OtpActivity extends AppCompatActivity {

    private EditText otp1, otp2, otp3, otp4, otp5, otp6;
    private Button verifyBtn, btnSendOtp;
    private RadioGroup rgOtpMethod;
    private RadioButton rbEmailOtp, rbSmsOtp;

    private String username, surname, phone, email, role, password;
    private android.os.CountDownTimer resendTimer;
    private boolean isPasswordReset = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

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
        btnSendOtp.setText("Send / Resend OTP Code");
        
        rgOtpMethod = findViewById(R.id.rgOtpMethod);
        rbEmailOtp = findViewById(R.id.rbEmailOtp);
        rbSmsOtp = findViewById(R.id.rbSmsOtp);

        Intent incomingIntent = getIntent();
        username = incomingIntent.getStringExtra("USER_NAME");
        surname = incomingIntent.getStringExtra("USER_SURNAME");
        phone = incomingIntent.getStringExtra("USER_PHONE");
        email = incomingIntent.getStringExtra("USER_EMAIL");
        role = "landlord".equalsIgnoreCase(incomingIntent.getStringExtra("USER_ROLE")) ? "landlord" : "tenant";
        password = incomingIntent.getStringExtra("USER_PASSWORD");
        isPasswordReset = incomingIntent.getBooleanExtra("IS_PASSWORD_RESET", false);

        if (rbSmsOtp != null) {
            rbSmsOtp.setVisibility(android.view.View.GONE);
        }
        if (rbEmailOtp != null) {
            rbEmailOtp.setChecked(true);
            rbEmailOtp.setText("Email OTP (" + email + ")");
        }

        setupOtpJumps(otp1, otp2);
        setupOtpJumps(otp2, otp3);
        setupOtpJumps(otp3, otp4);
        setupOtpJumps(otp4, otp5);
        setupOtpJumps(otp5, otp6);
        setupOtpJumps(otp6, null);

        btnSendOtp.setOnClickListener(v -> sendOtp());
        verifyBtn.setOnClickListener(v -> verifyOtp());
        
        if (incomingIntent.getBooleanExtra("INITIAL_OTP_SENT", false)) {
            Toast.makeText(this, getString(R.string.msg_otp_sent_email_spam, email), Toast.LENGTH_LONG).show();
            startResendCooldown();
        } else {
            sendOtp();
        }
    }
    
    private void startResendCooldown() {
        btnSendOtp.setEnabled(false);
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendTimer = new android.os.CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnSendOtp.setText("Resend OTP in " + (millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                btnSendOtp.setEnabled(true);
                btnSendOtp.setText("Send / Resend OTP Code");
            }
        }.start();
    }

    private void sendOtp() {
        startResendCooldown();
        new Thread(() -> {
            try {
                HttpURLConnection connection;
                JSONObject body = new JSONObject();
                
                connection = SupabaseHelper.createConnection("/auth/v1/otp", "POST", null);
                body.put("email", email);
                if (isPasswordReset) {
                    body.put("create_user", false);
                } else {
                    body.put("create_user", true);
                    JSONObject metadata = new JSONObject();
                    metadata.put("first_name", username);
                    metadata.put("surname", surname);
                    metadata.put("phone", phone);
                    metadata.put("role", role);
                    body.put("data", metadata);
                }
                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (code >= 400) {
                    if (isPasswordReset) {
                        connection = SupabaseHelper.createConnection("/auth/v1/recover", "POST", null);
                        JSONObject resendBody = new JSONObject();
                        resendBody.put("email", email);
                        SupabaseHelper.writeJson(connection, resendBody);
                        code = connection.getResponseCode();
                        response = SupabaseHelper.readResponse(connection);
                        connection.disconnect();
                    } else {
                        connection = SupabaseHelper.createConnection("/auth/v1/resend", "POST", null);
                        JSONObject resendBody = new JSONObject();
                        resendBody.put("type", "signup");
                        resendBody.put("email", email);
                        SupabaseHelper.writeJson(connection, resendBody);
                        code = connection.getResponseCode();
                        response = SupabaseHelper.readResponse(connection);
                        connection.disconnect();
                    }
                }

                final int finalCode = code;
                final String finalResponse = response;

                runOnUiThread(() -> {
                    if (finalCode >= 200 && finalCode < 300) {
                        Toast.makeText(this, R.string.msg_otp_sent, Toast.LENGTH_SHORT).show();
                    } else {
                        if (resendTimer != null) resendTimer.cancel();
                        btnSendOtp.setEnabled(true);
                        btnSendOtp.setText(R.string.btn_send_otp_code);
                        Toast.makeText(this, getString(R.string.error_failed_send_otp_prefix) + finalResponse, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (resendTimer != null) resendTimer.cancel();
                    btnSendOtp.setEnabled(true);
                    btnSendOtp.setText(R.string.btn_send_otp_code);
                    Toast.makeText(this, getString(R.string.error_sending_otp_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void verifyOtp() {
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
                
                body.put("type", "email");
                body.put("email", email);

                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (code >= 400) {
                    // Fallback to magiclink
                    connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                    body.put("type", "magiclink");
                    SupabaseHelper.writeJson(connection, body);
                    code = connection.getResponseCode();
                    response = SupabaseHelper.readResponse(connection);
                    connection.disconnect();
                    
                    if (code >= 400) {
                        // Fallback to signup type
                        connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                        body.put("type", "signup");
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

                            Toast.makeText(this, R.string.msg_verified_successfully, Toast.LENGTH_SHORT).show();
                            
                            if (isPasswordReset) {
                                Intent intent = new Intent(OtpActivity.this, ResetPasswordActivity.class);
                                intent.putExtra("USER_EMAIL", email);
                                intent.putExtra("ACCESS_TOKEN", accessToken);
                                startActivity(intent);
                                finish();
                                return;
                            }
                            
                            if (password != null && !password.isEmpty() && !accessToken.isEmpty()) {
                                new Thread(() -> {
                                    try {
                                        HttpURLConnection userConn = SupabaseHelper.createConnection("/auth/v1/user", "PUT", accessToken);
                                        JSONObject updateBody = new JSONObject();
                                        updateBody.put("password", password);
                                        JSONObject meta = new JSONObject();
                                        meta.put("first_name", username);
                                        meta.put("surname", surname);
                                        meta.put("phone", phone);
                                        meta.put("role", role);
                                        updateBody.put("data", meta);
                                        SupabaseHelper.writeJson(userConn, updateBody);
                                        userConn.getResponseCode();
                                        userConn.disconnect();
                                    } catch (Exception ignored) {}
                                }).start();
                            }
                            
                            Intent intent = new Intent(OtpActivity.this, PersonalActivity.class);
                            intent.putExtra("USER_NAME", username);
                            intent.putExtra("USER_SURNAME", surname);
                            intent.putExtra("USER_PHONE", phone);
                            intent.putExtra("USER_EMAIL", email);
                            intent.putExtra("USER_ROLE", role);
                            intent.putExtra("USER_PASSWORD", password);
                            intent.putExtra("OTP_VERIFIED", true);
                            startActivity(intent);
                            
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

    private String formatPhone(String rawPhone) {
        if (rawPhone.startsWith("0")) {
            return "+27" + rawPhone.substring(1);
        }
        return rawPhone;
    }

    private void setupOtpJumps(EditText currentBox, EditText nextBox) {
        currentBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 1) {
                    if (nextBox != null) {
                        nextBox.requestFocus();
                    } else {
                        currentBox.clearFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(currentBox.getWindowToken(), 0);
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
}