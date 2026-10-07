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
import androidx.appcompat.app.AppCompatActivity;
import java.util.regex.Pattern;
public class CreatePasswordActivity extends AppCompatActivity {
    private final Pattern uppercasePattern =
            Pattern.compile("[A-Z]");
    private final Pattern lowercasePattern =
            Pattern.compile("[a-z]");
    private final Pattern numberPattern =
            Pattern.compile("[0-9]");
    private final Pattern specialCharPattern =
            Pattern.compile("[^a-zA-Z0-9]");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(
                R.layout.activity_passwordpage
        );
        ImageButton backButton =
                findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(
                    v -> finish()
            );
        }
        EditText editPassword =
                findViewById(R.id.editPassword);
        ProgressBar passwordStrengthProgressbar =
                findViewById(
                        R.id.passwordStrengthProgressbar
                );
        Button registerBtn =
                findViewById(R.id.registerBtn);
        ImageView uppercaseIcon =
                findViewById(
                        R.id.uppercaseCheckerIcon
                );
        ImageView lowercaseIcon =
                findViewById(
                        R.id.lowercaseCheckerIcon
                );
        ImageView numberIcon =
                findViewById(
                        R.id.numberCheckerIcon
                );
        ImageView specialIcon =
                findViewById(
                        R.id.specialCharacterIcon
                );
        TextView uppercaseText =
                findViewById(
                        R.id.uppercaseChecker
                );
        TextView lowercaseText =
                findViewById(
                        R.id.lowercaseChecker
                );
        TextView numberText =
                findViewById(
                        R.id.numberChecker
                );
        TextView specialText =
                findViewById(
                        R.id.specialCharacter
                );
        registerBtn.setEnabled(false);
        passwordStrengthProgressbar.setMax(4);
        editPassword.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }
                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {
                    }
                    @Override
                    public void afterTextChanged(
                            Editable s
                    ) {
                        String password =
                                s.toString();
                        boolean hasUppercase =
                                uppercasePattern
                                        .matcher(password)
                                        .find();
                        boolean hasLowercase =
                                lowercasePattern
                                        .matcher(password)
                                        .find();
                        boolean hasNumber =
                                numberPattern
                                        .matcher(password)
                                        .find();
                        boolean hasSpecial =
                                specialCharPattern
                                        .matcher(password)
                                        .find();
                        updateRequirementUI(
                                hasUppercase,
                                uppercaseIcon,
                                uppercaseText
                        );
                        updateRequirementUI(
                                hasLowercase,
                                lowercaseIcon,
                                lowercaseText
                        );
                        updateRequirementUI(
                                hasNumber,
                                numberIcon,
                                numberText
                        );
                        updateRequirementUI(
                                hasSpecial,
                                specialIcon,
                                specialText
                        );
                        int progress = 0;
                        if (hasUppercase) {
                            progress++;
                        }
                        if (hasLowercase) {
                            progress++;
                        }
                        if (hasNumber) {
                            progress++;
                        }
                        if (hasSpecial) {
                            progress++;
                        }
                        passwordStrengthProgressbar
                                .setProgress(progress);
                        if (progress <= 1) {
                            passwordStrengthProgressbar
                                    .getProgressDrawable()
                                    .setTint(
                                            Color.parseColor(
                                                    "#F44336"
                                            )
                                    );
                        } else if (progress <= 3) {
                            passwordStrengthProgressbar
                                    .getProgressDrawable()
                                    .setTint(
                                            Color.parseColor(
                                                    "#FFEB3B"
                                            )
                                    );
                        } else {
                            passwordStrengthProgressbar
                                    .getProgressDrawable()
                                    .setTint(
                                            Color.parseColor(
                                                    "#4CAF50"
                                            )
                                    );
                        }
                        registerBtn.setEnabled(
                                progress == 4
                                        && password.length() >= 8
                        );
                    }
                }
        );
        registerBtn.setOnClickListener(v -> {
            Intent currentIntent =
                    getIntent();
            String username =
                    currentIntent.getStringExtra(
                            "USER_NAME"
                    );
            String surname =
                    currentIntent.getStringExtra(
                            "USER_SURNAME"
                    );
            String phone =
                    currentIntent.getStringExtra(
                            "USER_PHONE"
                    );
            String email =
                    currentIntent.getStringExtra(
                            "USER_EMAIL"
                    );
            String role =
                    currentIntent.getStringExtra(
                            "USER_ROLE"
                    );
            String password =
                    editPassword
                            .getText()
                            .toString();
            Intent nextIntent =
                    new Intent(
                            CreatePasswordActivity.this,
                            PersonalActivity.class
                    );
            nextIntent.putExtra(
                    "USER_NAME",
                    username
            );
            nextIntent.putExtra(
                    "USER_SURNAME",
                    surname
            );
            nextIntent.putExtra(
                    "USER_PHONE",
                    phone
            );
            nextIntent.putExtra(
                    "USER_EMAIL",
                    email
            );
            nextIntent.putExtra(
                    "USER_ROLE",
                    role
            );
            nextIntent.putExtra(
                    "USER_PASSWORD",
                    password
            );
            startActivity(nextIntent);
        });
    }
    private void updateRequirementUI(
            boolean isMet,
            ImageView icon,
            TextView text
    ) {
        if (isMet) {
            icon.setColorFilter(
                    Color.parseColor("#4CAF50")
            );
            text.setTextColor(
                    Color.parseColor("#4CAF50")
            );
        } else {
            icon.setColorFilter(
                    Color.parseColor("#808080")
            );
            text.setTextColor(
                    Color.parseColor("#808080")
            );
        }
    }
}