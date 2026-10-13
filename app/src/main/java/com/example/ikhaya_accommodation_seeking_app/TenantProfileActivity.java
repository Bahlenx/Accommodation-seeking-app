package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

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

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnPersonalInfo != null) {
            btnPersonalInfo.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, PersonalInformationActivity.class))
            );
        }

        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, LanguageSettingsActivity.class))
            );
        }

        if (btnSavedListings != null) {
            btnSavedListings.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, SavedListingsActivity.class))
            );
        }

        if (btnApplications != null) {
            btnApplications.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, ApplicationsActivity.class))
            );
        }

        if (btnPaymentHistory != null) {
            btnPaymentHistory.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, PaymentHistoryActivity.class))
            );
        }

        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, NotificationsActivity.class))
            );
        }

        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, ChangePasswordActivity.class))
            );
        }

        if (btnReportProblem != null) {
            btnReportProblem.setOnClickListener(v ->
                    startActivity(new Intent(TenantProfileActivity.this, ReportProblemActivity.class))
            );
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }
    }

    private void logout() {
        // Clear Firebase session
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}

        // Clear Supabase session
        try {
            SupabaseHelper.clearSession(this);
        } catch (Exception ignored) {}

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Redirect to Greeting / OuterMainActivity and clear task stack
        Intent intent = new Intent(TenantProfileActivity.this, OuterMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}