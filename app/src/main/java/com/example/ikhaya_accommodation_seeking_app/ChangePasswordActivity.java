package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ChangePasswordActivity extends AppCompatActivity {

    private Button btnBack;
    private Button btnChangePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        btnBack = findViewById(R.id.btnBack);
        btnChangePassword = findViewById(R.id.btnChangePassword);

        if (btnBack == null || btnChangePassword == null) {
            Toast.makeText(
                    this,
                    "Change password screen is missing a required view.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        btnBack.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(v -> {
            Toast.makeText(
                    ChangePasswordActivity.this,
                    "Password change is not connected yet.",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }
}

