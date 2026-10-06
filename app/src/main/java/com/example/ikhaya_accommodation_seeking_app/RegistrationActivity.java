package com.example.ikhaya_accommodation_seeking_app;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.TransitionManager;
public class RegistrationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_regitration);
        // Back button
        ImageButton backButton =
                findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(
                    v -> finish()
            );
        }
        // Resident / Landlord toggle
        RadioGroup roleRadioGroup =
                findViewById(R.id.roleRadioGroup);
        ConstraintLayout toggleContainer =
                findViewById(R.id.toggleContainer);
        roleRadioGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    TransitionManager.beginDelayedTransition(
                            toggleContainer
                    );
                    ConstraintSet constraintSet =
                            new ConstraintSet();
                    constraintSet.clone(
                            toggleContainer
                    );
                    if (checkedId == R.id.radioResident) {
                        constraintSet.connect(
                                R.id.slideIndicator,
                                ConstraintSet.START,
                                ConstraintSet.PARENT_ID,
                                ConstraintSet.START
                        );
                        constraintSet.connect(
                                R.id.slideIndicator,
                                ConstraintSet.END,
                                R.id.guidelineCenter,
                                ConstraintSet.END
                        );
                    } else if (checkedId ==
                            R.id.radioLandlord) {
                        constraintSet.connect(
                                R.id.slideIndicator,
                                ConstraintSet.START,
                                R.id.guidelineCenter,
                                ConstraintSet.START
                        );
                        constraintSet.connect(
                                R.id.slideIndicator,
                                ConstraintSet.END,
                                ConstraintSet.PARENT_ID,
                                ConstraintSet.END
                        );
                    }
                    constraintSet.applyTo(
                            toggleContainer
                    );
                }
        );
        EditText editFirstName =
                findViewById(R.id.editFirstName);
        EditText editUserSurname =
                findViewById(R.id.editUserSurname);
        EditText editPhone =
                findViewById(R.id.editPhone_number);
        EditText editEmail =
                findViewById(R.id.editEmailAddress);
        Button otpButton =
                findViewById(R.id.otp_button);
        otpButton.setEnabled(false);
        TextWatcher fieldValidator =
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
                        String firstName =
                                editFirstName
                                        .getText()
                                        .toString()
                                        .trim();
                        String surname =
                                editUserSurname
                                        .getText()
                                        .toString()
                                        .trim();
                        String phone =
                                editPhone
                                        .getText()
                                        .toString()
                                        .trim();
                        String email =
                                editEmail
                                        .getText()
                                        .toString()
                                        .trim();
                        boolean isFirstNameValid =
                                firstName.length() >= 2
                                        && firstName.matches(
                                        "[a-zA-Z\\s-]+"
                                );
                        boolean isSurnameValid =
                                surname.length() >= 2
                                        && surname.matches(
                                        "[a-zA-Z\\s-]+"
                                );
                        boolean isPhoneValid =
                                phone.matches(
                                        "^(\\+27|0)[6-8][0-9]{8}$"
                                );
                        boolean isEmailValid =
                                Patterns.EMAIL_ADDRESS
                                        .matcher(email)
                                        .matches();
                        otpButton.setEnabled(
                                isFirstNameValid
                                        && isSurnameValid
                                        && isPhoneValid
                                        && isEmailValid
                        );
                    }
                };
        editFirstName.setOnFocusChangeListener(
                (v, hasFocus) -> {
                    String value =
                            editFirstName
                                    .getText()
                                    .toString()
                                    .trim();
                    if (!hasFocus
                            && !value.isEmpty()
                            && !value.matches(
                            "[a-zA-Z\\s-]+"
                    )) {
                        editFirstName.setError(
                                getString(
                                        R.string.error_first_name
                                )
                        );
                    }
                }
        );
        editUserSurname.setOnFocusChangeListener(
                (v, hasFocus) -> {
                    String value =
                            editUserSurname
                                    .getText()
                                    .toString()
                                    .trim();
                    if (!hasFocus
                            && !value.isEmpty()
                            && !value.matches(
                            "[a-zA-Z\\s-]+"
                    )) {
                        editUserSurname.setError(
                                getString(
                                        R.string.error_surname
                                )
                        );
                    }
                }
        );
        editPhone.setOnFocusChangeListener(
                (v, hasFocus) -> {
                    String value =
                            editPhone
                                    .getText()
                                    .toString()
                                    .trim();
                    if (!hasFocus
                            && !value.isEmpty()
                            && !value.matches(
                            "^(\\+27|0)[6-8][0-9]{8}$"
                    )) {
                        editPhone.setError(
                                getString(
                                        R.string.error_phone
                                )
                        );
                    }
                }
        );
        editEmail.setOnFocusChangeListener(
                (v, hasFocus) -> {
                    String value =
                            editEmail
                                    .getText()
                                    .toString()
                                    .trim();
                    if (!hasFocus
                            && !value.isEmpty()
                            && !Patterns.EMAIL_ADDRESS
                            .matcher(value)
                            .matches()) {
                        editEmail.setError(
                                getString(
                                        R.string.error_email
                                )
                        );
                    }
                }
        );
        editFirstName.addTextChangedListener(
                fieldValidator
        );
        editUserSurname.addTextChangedListener(
                fieldValidator
        );
        editPhone.addTextChangedListener(
                fieldValidator
        );
        editEmail.addTextChangedListener(
                fieldValidator
        );
        otpButton.setOnClickListener(v -> {
            String firstName =
                    editFirstName
                            .getText()
                            .toString()
                            .trim();
            String surname =
                    editUserSurname
                            .getText()
                            .toString()
                            .trim();
            String phone =
                    editPhone
                            .getText()
                            .toString()
                            .trim();
            String email =
                    editEmail
                            .getText()
                            .toString()
                            .trim();
            int checkedId =
                    roleRadioGroup
                            .getCheckedRadioButtonId();
            String role =
                    checkedId == R.id.radioLandlord
                            ? "Landlord"
                            : "Resident";
            Intent intent =
                    new Intent(
                            RegistrationActivity.this,
                            OtpActivity.class
                    );
            intent.putExtra(
                    "USER_NAME",
                    firstName
            );
            intent.putExtra(
                    "USER_SURNAME",
                    surname
            );
            intent.putExtra(
                    "USER_PHONE",
                    phone
            );
            intent.putExtra(
                    "USER_EMAIL",
                    email
            );
            intent.putExtra(
                    "USER_ROLE",
                    role
            );
            startActivity(intent);
        });
    }
}