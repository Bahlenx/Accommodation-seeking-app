package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LandlordProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_profile);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPersonalInfo = findViewById(R.id.btnPersonalInfo);
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        Button btnMyListings = findViewById(R.id.btnMyListings);
        Button btnEnquiries = findViewById(R.id.btnEnquiries);
        Button btnPaymentsReceived = findViewById(R.id.btnPaymentsReceived);
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
                    startActivity(new Intent(LandlordProfileActivity.this, LandlordPersonalInformationActivity.class)));
        }

        // Multi-language settings navigation
        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, LanguageSettingsActivity.class)));
        }

        if (btnMyListings != null) {
            btnMyListings.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, MyListingsActivity.class)));
        }

        if (btnEnquiries != null) {
            btnEnquiries.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, EnquiriesActivity.class)));
        }

        if (btnPaymentsReceived != null) {
            btnPaymentsReceived.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, PaymentsReceivedActivity.class)));
        }

        if (btnPaymentHistory != null) {
            btnPaymentHistory.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, PaymentHistoryActivity.class)));
        }

        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, NotificationsActivity.class)));
        }

        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, ChangePasswordActivity.class)));
        }

        if (btnReportProblem != null) {
            btnReportProblem.setOnClickListener(v ->
                    startActivity(new Intent(LandlordProfileActivity.this, ReportProblemActivity.class)));
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
        Intent intent = new Intent(LandlordProfileActivity.this, OuterMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}