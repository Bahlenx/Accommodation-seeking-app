package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;

import androidx.appcompat.app.AppCompatActivity;

public class TenantProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenant_profile);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPersonalInfo = findViewById(R.id.btnPersonalInfo);
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        Button btnSavedListings = findViewById(R.id.btnSavedListings);
        Button btnApplications = findViewById(R.id.btnApplications);
        Button btnPaymentHistory = findViewById(R.id.btnPaymentHistory);
        Button btnNotifications = findViewById(R.id.btnNotifications);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        Button btnReportProblem = findViewById(R.id.btnReportProblem);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> finish());

        btnPersonalInfo.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, PersonalInformationActivity.class));
        });

        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v -> {
                startActivity(new Intent(TenantProfileActivity.this, LanguageSettingsActivity.class));
            });
        }

        btnSavedListings.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, SavedListingsActivity.class));
        });

        btnApplications.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, ApplicationsActivity.class));
        });

        btnPaymentHistory.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, PaymentHistoryActivity.class));
        });

        btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, NotificationsActivity.class));
        });

        btnChangePassword.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, ChangePasswordActivity.class));
        });

        btnReportProblem.setOnClickListener(v -> {
            startActivity(new Intent(TenantProfileActivity.this, ReportProblemActivity.class));
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(TenantProfileActivity.this, OuterMainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}