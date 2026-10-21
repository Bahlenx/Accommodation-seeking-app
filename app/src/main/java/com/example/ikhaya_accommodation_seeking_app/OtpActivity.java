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

    private String username, surname, phone, email, role;

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
                HttpURLConnection connection = SupabaseHelper.createConnection("/auth/v1/otp", "POST", null);
                JSONObject body = new JSONObject();
                body.put("create_user", true);
                
                if (rbEmailOtp.isChecked()) {
                    body.put("email", email);
                } else {
                    String formattedPhone = formatPhone(phone);
                    body.put("phone", formattedPhone);
                }

                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        Toast.makeText(this, "OTP Sent!", Toast.LENGTH_SHORT).show();
                        btnSendOtp.postDelayed(() -> btnSendOtp.setEnabled(true), 60000);
                    } else {
                        btnSendOtp.setEnabled(true);
                        Toast.makeText(this, "Failed to send OTP: " + response, Toast.LENGTH_LONG).show();
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
                    body.put("type", "email");
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
                    // Fallback to signup type for newly created users via OTP
                    connection = SupabaseHelper.createConnection("/auth/v1/verify", "POST", null);
                    body.put("type", "signup");
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

                            SupabaseHelper.saveSession(this, accessToken, refreshToken, userId, null, null);

                            Toast.makeText(this, "Verified successfully!", Toast.LENGTH_SHORT).show();
                            
                            Intent intent = new Intent(OtpActivity.this, CreatePasswordActivity.class);
                            intent.putExtra("USER_NAME", username);
                            intent.putExtra("USER_SURNAME", surname);
                            intent.putExtra("USER_PHONE", phone);
                            intent.putExtra("USER_EMAIL", email);
                            intent.putExtra("USER_ROLE", role);
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