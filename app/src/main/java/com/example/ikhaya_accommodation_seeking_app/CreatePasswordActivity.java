package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.regex.Pattern;

public class CreatePasswordActivity extends AppCompatActivity {

    private final Pattern uppercasePattern = Pattern.compile("[A-Z]");
    private final Pattern lowercasePattern = Pattern.compile("[a-z]");
    private final Pattern numberPattern = Pattern.compile("[0-9]");
    private final Pattern specialCharPattern = Pattern.compile("[^a-zA-Z0-9]");

    private EditText editPassword;
    private ProgressBar passwordStrengthProgressbar;
    private Button registerBtn;

    private ImageView uppercaseIcon;
    private ImageView lowercaseIcon;
    private ImageView numberIcon;
    private ImageView specialIcon;

    private TextView uppercaseText;
    private TextView lowercaseText;
    private TextView numberText;
    private TextView specialText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passwordpage);

        ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        editPassword = findViewById(R.id.editPassword);
        passwordStrengthProgressbar =
                findViewById(R.id.passwordStrengthProgressbar);
        registerBtn = findViewById(R.id.registerBtn);

        uppercaseIcon = findViewById(R.id.uppercaseCheckerIcon);
        lowercaseIcon = findViewById(R.id.lowercaseCheckerIcon);
        numberIcon = findViewById(R.id.numberCheckerIcon);
        specialIcon = findViewById(R.id.specialCharacterIcon);

        uppercaseText = findViewById(R.id.uppercaseChecker);
        lowercaseText = findViewById(R.id.lowercaseChecker);
        numberText = findViewById(R.id.numberChecker);
        specialText = findViewById(R.id.specialCharacter);

        if (editPassword == null
                || passwordStrengthProgressbar == null
                || registerBtn == null
                || uppercaseIcon == null
                || lowercaseIcon == null
                || numberIcon == null
                || specialIcon == null
                || uppercaseText == null
                || lowercaseText == null
                || numberText == null
                || specialText == null) {

            Toast.makeText(
                    this,
                    "Password screen layout is missing a required view.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        registerBtn.setText("Proceed to OTP Verification");
        registerBtn.setEnabled(false);
        passwordStrengthProgressbar.setMax(4);

        editPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();

                boolean hasUppercase =
                        uppercasePattern.matcher(password).find();
                boolean hasLowercase =
                        lowercasePattern.matcher(password).find();
                boolean hasNumber =
                        numberPattern.matcher(password).find();
                boolean hasSpecial =
                        specialCharPattern.matcher(password).find();

                updateRequirementUI(
                        hasUppercase, uppercaseIcon, uppercaseText);
                updateRequirementUI(
                        hasLowercase, lowercaseIcon, lowercaseText);
                updateRequirementUI(
                        hasNumber, numberIcon, numberText);
                updateRequirementUI(
                        hasSpecial, specialIcon, specialText);

                int progress = 0;
                if (hasUppercase) progress++;
                if (hasLowercase) progress++;
                if (hasNumber) progress++;
                if (hasSpecial) progress++;

                passwordStrengthProgressbar.setProgress(progress);

                if (progress <= 1) {
                    passwordStrengthProgressbar.getProgressDrawable()
                            .setTint(Color.parseColor("#F44336"));
                } else if (progress <= 3) {
                    passwordStrengthProgressbar.getProgressDrawable()
                            .setTint(Color.parseColor("#FFEB3B"));
                } else {
                    passwordStrengthProgressbar.getProgressDrawable()
                            .setTint(Color.parseColor("#4CAF50"));
                }

                registerBtn.setEnabled(
                        progress == 4 && password.length() >= 8);
            }
        });

        registerBtn.setOnClickListener(v -> continueRegistration());
    }

    private void continueRegistration() {
        String username = getIntent().getStringExtra("USER_NAME");
        String surname = getIntent().getStringExtra("USER_SURNAME");
        String phone = getIntent().getStringExtra("USER_PHONE");
        String email = getIntent().getStringExtra("USER_EMAIL");
        String role = getIntent().getStringExtra("USER_ROLE");
        String password = editPassword.getText().toString();

        if (email == null || email.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Your email is missing. Please restart registration.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (password.length() < 8
                || !uppercasePattern.matcher(password).find()
                || !lowercasePattern.matcher(password).find()
                || !numberPattern.matcher(password).find()
                || !specialCharPattern.matcher(password).find()) {

            Toast.makeText(
                    this,
                    "Please meet all password requirements.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String normalizedRole = "landlord".equalsIgnoreCase(role) ? "landlord" : "tenant";
        
        registerBtn.setEnabled(false);
        registerBtn.setText("Creating account & sending OTP...");

        new Thread(() -> {
            try {
                HttpURLConnection connection = SupabaseHelper.createConnection("/auth/v1/signup", "POST", null);
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                
                JSONObject metadata = new JSONObject();
                metadata.put("first_name", username);
                metadata.put("surname", surname);
                metadata.put("phone", phone);
                metadata.put("role", normalizedRole);
                body.put("data", metadata);
                
                SupabaseHelper.writeJson(connection, body);
                int code = connection.getResponseCode();
                connection.disconnect();

                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        Intent nextIntent = new Intent(CreatePasswordActivity.this, OtpActivity.class);
                        nextIntent.putExtra("USER_NAME", username);
                        nextIntent.putExtra("USER_SURNAME", surname);
                        nextIntent.putExtra("USER_PHONE", phone);
                        nextIntent.putExtra("USER_EMAIL", email);
                        nextIntent.putExtra("USER_ROLE", normalizedRole);
                        nextIntent.putExtra("USER_PASSWORD", password);
                        nextIntent.putExtra("INITIAL_OTP_SENT", true);
                        startActivity(nextIntent);
                    });
                } else if (code == 400 || code == 422) {
                    // Unverified user already exists, trigger OTP sending
                    connection = SupabaseHelper.createConnection("/auth/v1/otp", "POST", null);
                    JSONObject resendBody = new JSONObject();
                    resendBody.put("email", email);
                    resendBody.put("create_user", true);
                    resendBody.put("data", metadata);
                    SupabaseHelper.writeJson(connection, resendBody);
                    connection.getResponseCode();
                    connection.disconnect();
                    
                    runOnUiThread(() -> {
                        Intent nextIntent = new Intent(CreatePasswordActivity.this, OtpActivity.class);
                        nextIntent.putExtra("USER_NAME", username);
                        nextIntent.putExtra("USER_SURNAME", surname);
                        nextIntent.putExtra("USER_PHONE", phone);
                        nextIntent.putExtra("USER_EMAIL", email);
                        nextIntent.putExtra("USER_ROLE", normalizedRole);
                        nextIntent.putExtra("USER_PASSWORD", password);
                        nextIntent.putExtra("INITIAL_OTP_SENT", true);
                        startActivity(nextIntent);
                    });
                } else {
                    runOnUiThread(() -> {
                        registerBtn.setEnabled(true);
                        registerBtn.setText("Proceed to OTP Verification");
                        Toast.makeText(this, "Failed to create account (HTTP " + code + ")", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    registerBtn.setEnabled(true);
                    registerBtn.setText("Proceed to OTP Verification");
                    Toast.makeText(this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void updateRequirementUI(
            boolean isMet,
            ImageView icon,
            TextView text) {

        int color = isMet
                ? Color.parseColor("#4CAF50")
                : Color.parseColor("#808080");

        icon.setColorFilter(color);
        text.setTextColor(color);
    }
}

