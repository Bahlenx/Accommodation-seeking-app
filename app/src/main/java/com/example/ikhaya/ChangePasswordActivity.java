package com.example.ikhaya;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ChangePasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);

        btnBack.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Password change will be connected later",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }
}