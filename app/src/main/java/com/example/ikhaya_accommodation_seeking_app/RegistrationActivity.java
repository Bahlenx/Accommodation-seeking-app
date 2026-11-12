package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.FileProvider;
import androidx.transition.TransitionManager;

import java.io.File;

public class RegistrationActivity extends AppCompatActivity {

    private LinearLayout layoutLandlordRegistrationIdentity;
    private EditText etRegSaIdNumber;
    private Button btnRegUploadIdDoc;
    private Button btnRegUploadSelfie;
    private TextView tvRegIdDocStatus;
    private TextView tvRegSelfieStatus;

    private Uri selectedIdDocUri = null;
    private Uri selectedSelfieUri = null;
    private Uri tempCameraUri = null;
    private boolean isCapturingSelfieForCamera = false;

    private final ActivityResultLauncher<Uri> takePhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && tempCameraUri != null) {
                    if (isCapturingSelfieForCamera) {
                        selectedSelfieUri = tempCameraUri;
                        if (tvRegSelfieStatus != null) tvRegSelfieStatus.setText("Selected: Camera Selfie");
                    } else {
                        selectedIdDocUri = tempCameraUri;
                        if (tvRegIdDocStatus != null) tvRegIdDocStatus.setText("Selected: Camera ID Document");
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> pickGalleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    if (isCapturingSelfieForCamera) {
                        selectedSelfieUri = uri;
                        if (tvRegSelfieStatus != null) tvRegSelfieStatus.setText("Selected: Gallery Selfie");
                    } else {
                        selectedIdDocUri = uri;
                        if (tvRegIdDocStatus != null) tvRegIdDocStatus.setText("Selected: Gallery ID Document");
                    }
                }
            }
    );

    private void showImageSourceDialog(boolean isSelfie) {
        isCapturingSelfieForCamera = isSelfie;
        String[] options = {"Take Photo with Camera", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle(isSelfie ? "Take Verification Selfie" : "Upload ID Document")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        try {
                            File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                    (isSelfie ? "selfie_" : "id_") + System.currentTimeMillis() + ".jpg");
                            tempCameraUri = FileProvider.getUriForFile(this,
                                    getApplicationContext().getPackageName() + ".provider", photoFile);
                            takePhotoLauncher.launch(tempCameraUri);
                        } catch (Exception e) {
                            Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        pickGalleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_regitration);


        ImageButton backButton =
                findViewById(R.id.back_button);

        if (backButton != null) {
            backButton.setOnClickListener(
                    v -> finish()
            );
        }

        RadioGroup roleRadioGroup =
                findViewById(R.id.roleRadioGroup);

        ConstraintLayout toggleContainer =
                findViewById(R.id.toggleContainer);

        layoutLandlordRegistrationIdentity = findViewById(R.id.layoutLandlordRegistrationIdentity);
        etRegSaIdNumber = findViewById(R.id.etRegSaIdNumber);
        btnRegUploadIdDoc = findViewById(R.id.btnRegUploadIdDoc);
        btnRegUploadSelfie = findViewById(R.id.btnRegUploadSelfie);
        tvRegIdDocStatus = findViewById(R.id.tvRegIdDocStatus);
        tvRegSelfieStatus = findViewById(R.id.tvRegSelfieStatus);

        btnRegUploadIdDoc.setOnClickListener(v -> showImageSourceDialog(false));
        btnRegUploadSelfie.setOnClickListener(v -> showImageSourceDialog(true));

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
                        layoutLandlordRegistrationIdentity.setVisibility(View.GONE);

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

                    } else if (checkedId == R.id.radioLandlord) {
                        layoutLandlordRegistrationIdentity.setVisibility(View.VISIBLE);

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
                
        android.view.View btnSupportMail = findViewById(R.id.btnSupportMail);
        if (btnSupportMail != null) {
            btnSupportMail.setOnClickListener(v -> {
                String hint = "";
                if (editEmail != null && !editEmail.getText().toString().trim().isEmpty()) {
                    hint = editEmail.getText().toString().trim();
                } else if (editFirstName != null && !editFirstName.getText().toString().trim().isEmpty()) {
                    hint = editFirstName.getText().toString().trim();
                }
                SupabaseHelper.openAdminSupportChat(RegistrationActivity.this, hint);
            });
        }

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
                                editFirstName.getText()
                                        .toString()
                                        .trim();

                        String surname =
                                editUserSurname.getText()
                                        .toString()
                                        .trim();

                        String phone =
                                editPhone.getText()
                                        .toString()
                                        .trim();

                        String email =
                                editEmail.getText()
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
                            editFirstName.getText()
                                    .toString()
                                    .trim();

                    if (!hasFocus
                            && !value.isEmpty()
                            && !value.matches(
                            "[a-zA-Z\\s-]+"
                    )) {

                        editFirstName.setError(null);
                        Toast.makeText(this, R.string.error_first_name, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        editUserSurname.setOnFocusChangeListener(
                (v, hasFocus) -> {

                    String value =
                            editUserSurname.getText()
                                    .toString()
                                    .trim();

                    if (!hasFocus
                            && !value.isEmpty()
                            && !value.matches(
                            "[a-zA-Z\\s-]+"
                    )) {

                        editUserSurname.setError(null);
                        Toast.makeText(this, R.string.error_surname, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        editPhone.setOnFocusChangeListener(
                (v, hasFocus) -> {

                    String value =
                            editPhone.getText()
                                    .toString()
                                    .trim();

                    if (!hasFocus
                            && !value.isEmpty()
                            && !value.matches(
                            "^(\\+27|0)[6-8][0-9]{8}$"
                    )) {

                        editPhone.setError(null);
                        Toast.makeText(this, R.string.error_phone, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        editEmail.setOnFocusChangeListener(
                (v, hasFocus) -> {

                    String value =
                            editEmail.getText()
                                    .toString()
                                    .trim();

                    if (!hasFocus
                            && !value.isEmpty()
                            && !Patterns.EMAIL_ADDRESS
                            .matcher(value)
                            .matches()) {

                        editEmail.setError(null);
                        Toast.makeText(this, R.string.error_email, Toast.LENGTH_SHORT).show();
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

            SupabaseHelper.clearSession(this);

            String firstName =
                    editFirstName.getText()
                            .toString()
                            .trim();

            String surname =
                    editUserSurname.getText()
                            .toString()
                            .trim();

            String phone =
                    editPhone.getText()
                            .toString()
                            .trim();

            String email =
                    editEmail.getText()
                            .toString()
                            .trim();

            int checkedId =
                    roleRadioGroup.getCheckedRadioButtonId();

            String role =
                    checkedId == R.id.radioLandlord
                            ? "landlord"
                            : "tenant";

            String saId = "";
            if ("landlord".equalsIgnoreCase(role)) {
                saId = etRegSaIdNumber.getText().toString().trim();
                if (!IdValidationUtils.isValidSAID(saId)) {
                    Toast.makeText(this, "Please enter a valid 13-digit South African ID number.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedIdDocUri == null || selectedSelfieUri == null) {
                    Toast.makeText(this, "Landlords must upload both an ID document and verification selfie.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Intent intent =
                    new Intent(
                            RegistrationActivity.this,
                            CreatePasswordActivity.class
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

            if ("landlord".equalsIgnoreCase(role)) {
                intent.putExtra("USER_SA_ID", saId);
                intent.putExtra("USER_ID_DOC_URI", selectedIdDocUri.toString());
                intent.putExtra("USER_SELFIE_URI", selectedSelfieUri.toString());
            }

            startActivity(intent);
        });
    }
}
