
package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PersonalInformationActivity extends AppCompatActivity {

    private TextView txtName;
    private TextView txtEmail;
    private TextView txtPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_information);

        android.view.View btnBack = findViewById(R.id.btnBack);

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPhone = findViewById(R.id.txtPhone);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        showLoading();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInformation();
    }

    private void showLoading() {
        if (txtName != null) {
            txtName.setText("Loading...");
        }

        if (txtEmail != null) {
            txtEmail.setText("Loading...");
        }

        if (txtPhone != null) {
            txtPhone.setText("Loading...");
        }
    }

    private void loadInformation() {
        UserProfileLoader.load(this, (name, email, phone) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (txtName != null) {
                txtName.setText(
                        name.isEmpty() ? "Not provided" : name
                );
            }

            if (txtEmail != null) {
                txtEmail.setText(
                        email.isEmpty() ? "Not provided" : email
                );
            }

            if (txtPhone != null) {
                txtPhone.setText(
                        phone.isEmpty() ? "Not provided" : phone
                );
            }
        });
    }
}
