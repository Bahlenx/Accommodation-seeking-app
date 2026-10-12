package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class PersonalInformationActivity extends AppCompatActivity {

    private TextView txtName;
    private TextView txtEmail;
    private TextView txtPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_information);

        Button btnBack = findViewById(R.id.btnBack);

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPhone = findViewById(R.id.txtPhone);

        btnBack.setOnClickListener(v -> finish());

        loadInformation();
    }

    private void loadInformation() {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        String email = user.getEmail();

        if (email != null && !email.isEmpty()) {
            txtEmail.setText("Email: " + email);
        }

        String displayName = user.getDisplayName();

        if (displayName != null && !displayName.isEmpty()) {
            txtName.setText("Name: " + displayName);
        }

        String phone = user.getPhoneNumber();

        if (phone != null && !phone.isEmpty()) {
            txtPhone.setText("Phone: " + phone);
        }
    }
}