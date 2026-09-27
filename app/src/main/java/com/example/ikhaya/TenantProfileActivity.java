package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TenantProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenant_profile);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPersonalInfo = findViewById(R.id.btnPersonalInfo);
        Button btnSavedListings = findViewById(R.id.btnSavedListings);
        Button btnApplications = findViewById(R.id.btnApplications);
        Button btnPaymentHistory = findViewById(R.id.btnPaymentHistory);
        Button btnNotifications = findViewById(R.id.btnNotifications);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        Button btnReportProblem = findViewById(R.id.btnReportProblem);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> finish());

        btnPersonalInfo.setOnClickListener(v -> {
            startActivity(new Intent(
                    TenantProfileActivity.this,
                    PersonalInformationActivity.class
            ));
        });
        btnSavedListings.setOnClickListener(v -> {
            Intent intent = new Intent(
                    TenantProfileActivity.this,
                    SavedListingsActivity.class
            );

            startActivity(intent);
        });

        btnApplications.setOnClickListener(v -> {
            startActivity(new Intent(
                    TenantProfileActivity.this,
                    ApplicationsActivity.class
            ));
        });

        btnPaymentHistory.setOnClickListener(v -> {
            startActivity(new Intent(
                    TenantProfileActivity.this,
                    PaymentHistoryActivity.class
            ));
        });

        btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(
                    TenantProfileActivity.this,
                    NotificationsActivity.class
            ));
        });
        btnChangePassword.setOnClickListener(v -> {
            startActivity(new Intent(
                    TenantProfileActivity.this,
                    ChangePasswordActivity.class
            ));
        });

        btnReportProblem.setOnClickListener(v -> {
            startActivity(new Intent(
                    TenantProfileActivity.this,
                    ReportProblemActivity.class
            ));
        });

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Logged out",
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}