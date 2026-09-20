package com.example.ikhaya_accomadationapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class OtpActivity extends AppCompatActivity {

    private EditText otp1, otp2, otp3, otp4, otp5;
    private Button verifyBtn;

    // Our mock demo code
    private static final String MOCK_OTP = "12345";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        // 1. Back Button Logic
        ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // 2. Link XML to Java
        otp1 = findViewById(R.id.otp_num_1);
        otp2 = findViewById(R.id.otp_num_2);
        otp3 = findViewById(R.id.otp_num_3);
        otp4 = findViewById(R.id.otp_num_4);
        otp5 = findViewById(R.id.otp_num_5);
        verifyBtn = findViewById(R.id.button);

        // Retrieve data passed from RegistrationActivity
        Intent incomingIntent = getIntent();
        String username = incomingIntent.getStringExtra("USER_NAME");
        String surname = incomingIntent.getStringExtra("USER_SURNAME");
        String phone = incomingIntent.getStringExtra("USER_PHONE");
        String email = incomingIntent.getStringExtra("USER_EMAIL");
        String role = incomingIntent.getStringExtra("USER_ROLE");

        // 3. Set up the auto-jumping logic
        setupOtpJumps(otp1, otp2);
        setupOtpJumps(otp2, otp3);
        setupOtpJumps(otp3, otp4);
        setupOtpJumps(otp4, otp5);
        setupOtpJumps(otp5, null); // null means it's the last box

        // 4. Button Click Logic
        verifyBtn.setOnClickListener(v -> {
            // Combine all 5 boxes into one string
            String enteredCode = otp1.getText().toString() +
                    otp2.getText().toString() +
                    otp3.getText().toString() +
                    otp4.getText().toString() +
                    otp5.getText().toString();

            if (enteredCode.equals(MOCK_OTP)) {
                Toast.makeText(this, "Verified successfully!", Toast.LENGTH_SHORT).show();

                // Move to CreatePasswordActivity and forward user registration info
                Intent intent = new Intent(OtpActivity.this, CreatePasswordActivity.class);
                intent.putExtra("USER_NAME", username);
                intent.putExtra("USER_SURNAME", surname);
                intent.putExtra("USER_PHONE", phone);
                intent.putExtra("USER_EMAIL", email);
                intent.putExtra("USER_ROLE", role);
                startActivity(intent);

            } else {
                Toast.makeText(this, "Invalid code! Use 12345", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Helper method to automatically jump to the next box
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