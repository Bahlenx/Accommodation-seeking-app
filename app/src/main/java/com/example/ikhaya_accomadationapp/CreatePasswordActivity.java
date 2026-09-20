package com.example.ikhaya_accomadationapp;

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
import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Pattern;

public class CreatePasswordActivity extends AppCompatActivity {

    // 1. Regular Expressions to check each requirement
    private final Pattern uppercasePattern = Pattern.compile("[A-Z]");
    private final Pattern lowercasePattern = Pattern.compile("[a-z]");
    private final Pattern numberPattern = Pattern.compile("[0-9]");
    private final Pattern specialCharPattern = Pattern.compile("[^a-zA-Z0-9]");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passwordpage); // Make sure this matches your XML file name

        // Back button logic
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // Connect UI Elements
        EditText editPassword = findViewById(R.id.editPassword);
        ProgressBar passwordStrengthProgressbar = findViewById(R.id.passwordStrengthProgressbar);
        Button registerBtn = findViewById(R.id.registerBtn);

        ImageView uppercaseIcon = findViewById(R.id.uppercaseCheckerIcon);
        ImageView lowercaseIcon = findViewById(R.id.lowercaseCheckerIcon);
        ImageView numberIcon = findViewById(R.id.numberCheckerIcon);
        ImageView specialIcon = findViewById(R.id.specialCharacterIcon);

        TextView uppercaseText = findViewById(R.id.uppercaseChecker);
        TextView lowercaseText = findViewById(R.id.lowercaseChecker);
        TextView numberText = findViewById(R.id.numberChecker);
        TextView specialText = findViewById(R.id.specialCharacter);

        // Initial Setup: Button disabled, max progress set to 4 (for 4 criteria)
        registerBtn.setEnabled(false);
        passwordStrengthProgressbar.setMax(4);

        // 2. Watch text as the user types
        editPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();

                // Evaluate the current password against our patterns
                boolean hasUppercase = uppercasePattern.matcher(password).find();
                boolean hasLowercase = lowercasePattern.matcher(password).find();
                boolean hasNumber = numberPattern.matcher(password).find();
                boolean hasSpecial = specialCharPattern.matcher(password).find();

                // Update the checkmarks and text colors
                updateRequirementUI(hasUppercase, uppercaseIcon, uppercaseText);
                updateRequirementUI(hasLowercase, lowercaseIcon, lowercaseText);
                updateRequirementUI(hasNumber, numberIcon, numberText);
                updateRequirementUI(hasSpecial, specialIcon, specialText);

                // Tally up the score
                int progress = 0;
                if (hasUppercase) progress++;
                if (hasLowercase) progress++;
                if (hasNumber) progress++;
                if (hasSpecial) progress++;

                // Update the progress bar
                passwordStrengthProgressbar.setProgress(progress);

                // Optional: Change progress bar color based on strength
                if (progress <= 1) {
                    passwordStrengthProgressbar.getProgressDrawable().setTint(Color.parseColor("#F44336")); // Red (Weak)
                } else if (progress <= 3) {
                    passwordStrengthProgressbar.getProgressDrawable().setTint(Color.parseColor("#FFEB3B")); // Yellow (Medium)
                } else {
                    passwordStrengthProgressbar.getProgressDrawable().setTint(Color.parseColor("#4CAF50")); // Green (Strong)
                }

                // 3. Enable the Register button only if all rules are met and password is long enough (e.g., 8 characters)
                registerBtn.setEnabled(progress == 4 && password.length() >= 8);
            }
        });

        // 4. Final submission listener
        registerBtn.setOnClickListener(v -> {
            // Forward any data received from the previous screens (Registration -> OTP -> Password)
            Intent currentIntent = getIntent();
            String username = currentIntent.getStringExtra("USER_NAME");
            String surname = currentIntent.getStringExtra("USER_SURNAME");
            String phone = currentIntent.getStringExtra("USER_PHONE");
            String email = currentIntent.getStringExtra("USER_EMAIL");
            String role = currentIntent.getStringExtra("USER_ROLE");

            // Create Intent to go to PersonalActivity (activity_userverification.xml)
            Intent nextIntent = new Intent(CreatePasswordActivity.this, PersonalActivity.class);
            
            // Pass all the collected data forward to the final screen
            nextIntent.putExtra("USER_NAME", username);
            nextIntent.putExtra("USER_SURNAME", surname);
            nextIntent.putExtra("USER_PHONE", phone);
            nextIntent.putExtra("USER_EMAIL", email);
            nextIntent.putExtra("USER_ROLE", role);
            
            // Also pass the password securely (or hash it before passing depending on your security needs)
            nextIntent.putExtra("USER_PASSWORD", editPassword.getText().toString().trim());

            startActivity(nextIntent);
        });
    }

    // Helper method to cleanly toggle colors when requirements are met
    private void updateRequirementUI(boolean isMet, ImageView icon, TextView text) {
        if (isMet) {
            // Turns Green when satisfied
            icon.setColorFilter(Color.parseColor("#4CAF50"));
            text.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            // Reverts to Default Gray when incomplete
            icon.setColorFilter(Color.parseColor("#808080"));
            text.setTextColor(Color.parseColor("#808080"));
        }
    }
}