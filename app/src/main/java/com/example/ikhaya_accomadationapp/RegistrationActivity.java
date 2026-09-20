package com.example.ikhaya_accomadationapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.TransitionManager;

public class RegistrationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_regitration);

        // 1. Back Button Logic
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // 2. Slider Toggle Logic (Resident / Landlord with Smooth Animation)
        RadioGroup roleRadioGroup = findViewById(R.id.roleRadioGroup);
        ConstraintLayout toggleContainer = findViewById(R.id.toggleContainer);

        roleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // This line tells ConstraintLayout to smoothly animate any rule changes inside the container
            TransitionManager.beginDelayedTransition(toggleContainer);

            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(toggleContainer);

            if (checkedId == R.id.radioResident) {
                // Snap white indicator to the left side (from start of parent to guideline center)
                constraintSet.connect(R.id.slideIndicator, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.connect(R.id.slideIndicator, ConstraintSet.END, R.id.guidelineCenter, ConstraintSet.END);
            } else if (checkedId == R.id.radioLandlord) {
                // Snap white indicator to the right side (from guideline center to end of parent)
                constraintSet.connect(R.id.slideIndicator, ConstraintSet.START, R.id.guidelineCenter, ConstraintSet.START);
                constraintSet.connect(R.id.slideIndicator, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            }

            constraintSet.applyTo(toggleContainer);
        });

        // 3. Input Validation & OTP Button Logic
        EditText editFirstName = findViewById(R.id.editFirstName);
        EditText editUserSurname = findViewById(R.id.editUserSurname);
        EditText editPhone = findViewById(R.id.editPhone_number);
        EditText editEmail = findViewById(R.id.editEmailAddress);
        Button otpButton = findViewById(R.id.otp_button);

        otpButton.setEnabled(false);

        TextWatcher fieldValidator = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String firstName = editFirstName.getText().toString().trim();
                String surname = editUserSurname.getText().toString().trim();
                String phone = editPhone.getText().toString().trim();
                String email = editEmail.getText().toString().trim();

                // 1. Name and Surname: At least 2 characters, only letters, spaces, or hyphens
                boolean isFirstNameValid = firstName.length() >= 2 && firstName.matches("[a-zA-Z\\s-]+");
                boolean isSurnameValid = surname.length() >= 2 && surname.matches("[a-zA-Z\\s-]+");
                
                // 2. Phone Number: Standard South African mobile format (e.g., 0821234567 or +27821234567)
                boolean isPhoneValid = phone.matches("^(\\+27|0)[6-8][0-9]{8}$");
                
                // 3. Email: Built-in Android Pattern matching
                boolean isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches();

                // Only enable the button if ALL validations pass perfectly
                otpButton.setEnabled(isFirstNameValid && isSurnameValid && isPhoneValid && isEmailValid);
            }
        };

        // Add focus listeners to show helpful error messages when the user leaves a field
        editFirstName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !editFirstName.getText().toString().trim().isEmpty() && !editFirstName.getText().toString().trim().matches("[a-zA-Z\\s-]+")) {
                editFirstName.setError(getString(R.string.error_first_name));
            }
        });

        editUserSurname.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !editUserSurname.getText().toString().trim().isEmpty() && !editUserSurname.getText().toString().trim().matches("[a-zA-Z\\s-]+")) {
                editUserSurname.setError(getString(R.string.error_surname));
            }
        });

        editPhone.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !editPhone.getText().toString().trim().isEmpty() && !editPhone.getText().toString().trim().matches("^(\\+27|0)[6-8][0-9]{8}$")) {
                editPhone.setError(getString(R.string.error_phone));
            }
        });

        editEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !editEmail.getText().toString().trim().isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(editEmail.getText().toString().trim()).matches()) {
                editEmail.setError(getString(R.string.error_email));
            }
        });

        editFirstName.addTextChangedListener(fieldValidator);
        editUserSurname.addTextChangedListener(fieldValidator);
        editPhone.addTextChangedListener(fieldValidator);
        editEmail.addTextChangedListener(fieldValidator);

        // 4. Send Data and Open OtpActivity on Button Click
        otpButton.setOnClickListener(v -> {
            String firstName = editFirstName.getText().toString().trim();
            String surname = editUserSurname.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            String email = editEmail.getText().toString().trim();

            // Determine if they chose Resident or Landlord
            int checkedId = roleRadioGroup.getCheckedRadioButtonId();
            String role = (checkedId == R.id.radioLandlord) ? "Landlord" : "Resident";

            // Create Intent to move to OtpActivity and pass the collected data along
            Intent intent = new Intent(RegistrationActivity.this, OtpActivity.class);
            intent.putExtra("USER_NAME", firstName); // Keep the key as USER_NAME so OtpActivity doesn't break
            intent.putExtra("USER_SURNAME", surname);
            intent.putExtra("USER_PHONE", phone);
            intent.putExtra("USER_EMAIL", email);
            intent.putExtra("USER_ROLE", role);

            startActivity(intent);
        });
    }
}