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
    private boolean signupCalled = false;

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
        rgOtpMethod = findViewById(R.id.rgOtpMethod);
        rbEmailOtp = findViewById(R.id.rbEmailOtp);
        rbSmsOtp = findViewById(R.id.rbSmsOtp);

        Intent incomingIntent = getIntent();
        username = incomingIntent.getStringExtra("USER_NAME");
        surname = incomingIntent.getStringExtra("USER_SURNAME");
        phone = incomingIntent.getStringExtra("USER_PHONE");
        email = incomingIntent.getStringExtra("USER_EMAIL");
        role = incomingIntent.getStringExtra("USER_ROLE");
        password = incomingIntent.getStringExtra("USER_PASSWORD");

        rbEmailOtp.setText("Email OTP (" + email + ")");
        rbSmsOtp.setText("SMS OTP (" + phone + ")");

        setupOtpJumps(otp1, otp2);
        setupOtpJumps(otp2, otp3);
        setupOtpJumps(otp3, otp4);
        setupOtpJumps(otp4, otp5);
        setupOtpJumps(otp5, otp6);
        setupOtpJumps(otp6, null);

        btnSendOtp.setOnClickListener(v -> sendOtp());
        verifyBtn.setOnClickListener(v -> verifyOtp());
    }

    private void sendOtp() {
        btnSendOtp.setEnabled(false);
        new Thread(() -> {
            try {
                HttpURLConnection connection;
                JSONObject body = new JSONObject();
                
                if (rbEmailOtp.isChecked()) {
                    if (!signupCalled) {
                        connection = SupabaseHelper.createConnection("/auth/v1/signup", "POST", null);
                        body.put("email", email);
                        body.put("password", password);
                        
                        JSONObject metadata = new JSONObject();
                        metadata.put("first_name", username);
                        metadata.put("surname", surname);
                        metadata.put("phone", phone);
                        metadata.put("role", role);
                        body.put("data", metadata);
                    } else {
                        connection = SupabaseHelper.createConnection("/auth/v1/resend", "POST", null);
                        body.put("type", "signup");
                        body.put("email", email);
                    }
                } else {
                    connection = SupabaseHelper.createConnection("/auth/v1/otp", "POST", null);
                    body.put("create_user", true);
                    String formattedPhone = formatPhone(phone);
                    body.put("phone", formattedPhone);
                }

                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (rbEmailOtp.isChecked()) {
                    if (code >= 200 && code < 300) {
                        signupCalled = true;
                    } else if (code == 400 || code == 422) { // Already registered but possibly unverified
                        signupCalled = true;
                        
                        // Try resend
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
                        Toast.makeText(this, "OTP Sent!", Toast.LENGTH_SHORT).show();
                        btnSendOtp.postDelayed(() -> btnSendOtp.setEnabled(true), 60000);
                    } else {
                        btnSendOtp.setEnabled(true);
                        Toast.makeText(this, "Failed to send OTP: " + finalResponse, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnSendOtp.setEnabled(true);
                    Toast.makeText(this, "Error sending OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void verifyOtp() {
        String enteredCode = otp1.getText().toString() +
                otp2.getText().toString() +
                otp3.getText().toString() +
                otp4.getText().toString() +
                otp5.getText().toString() +
                otp6.getText().toString();

        if (enteredCode.length() < 6) {
            Toast.makeText(this, "Please enter all 6 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        verifyBtn.setEnabled(false);
        new Thread(() -> {
            try {
                HttpURLConnection connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                JSONObject body = new JSONObject();
                body.put("token", enteredCode);
                
                if (rbEmailOtp.isChecked()) {
                    body.put("type", "signup");
                    body.put("email", email);
                } else {
                    body.put("type", "sms");
                    body.put("phone", formatPhone(phone));
                }

                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (code >= 400 && rbEmailOtp.isChecked()) {
                    // Fallback to email type
                    connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                    body.put("type", "email");
                    SupabaseHelper.writeJson(connection, body);
                    code = connection.getResponseCode();
                    response = SupabaseHelper.readResponse(connection);
                    connection.disconnect();
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

                            SupabaseHelper.saveSession(this, accessToken, refreshToken, userId, (username + " " + surname).trim(), role);

                            Toast.makeText(this, "Verified successfully!", Toast.LENGTH_SHORT).show();
                            
                            if (rbSmsOtp.isChecked() && !accessToken.isEmpty() && password != null && !password.isEmpty()) {
                                new Thread(() -> {
                                    try {
                                        HttpURLConnection putConn = SupabaseHelper.createConnection("/auth/v1/user", "PUT", accessToken);
                                        JSONObject putBody = new JSONObject();
                                        putBody.put("password", password);
                                        SupabaseHelper.writeJson(putConn, putBody);
                                        putConn.getResponseCode();
                                        putConn.disconnect();
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
                            Toast.makeText(this, "Failed to parse OTP response.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Invalid OTP!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    verifyBtn.setEnabled(true);
                    Toast.makeText(this, "Error verifying OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(currentBox.getWindowToken(), 0);
                        }
                    }
                }
            }
        });
    }
}