package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LandlordProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_profile);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPersonalInfo = findViewById(R.id.btnPersonalInfo);
        Button btnMyListings = findViewById(R.id.btnMyListings);
        Button btnEnquiries = findViewById(R.id.btnEnquiries);
        Button btnPaymentsReceived = findViewById(R.id.btnPaymentsReceived);
        Button btnPaymentHistory = findViewById(R.id.btnPaymentHistory);
        Button btnNotifications = findViewById(R.id.btnNotifications);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        Button btnReportProblem = findViewById(R.id.btnReportProblem);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> finish());

        btnPersonalInfo.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        LandlordPersonalInformationActivity.class
                ))
        );

        btnMyListings.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        MyListingsActivity.class
                ))
        );

        btnEnquiries.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        EnquiriesActivity.class
                ))
        );

        btnPaymentsReceived.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        PaymentsReceivedActivity.class
                ))
        );

        btnPaymentHistory.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        PaymentHistoryActivity.class
                ))
        );

        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        NotificationsActivity.class
                ))
        );

        btnChangePassword.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        ChangePasswordActivity.class
                ))
        );

        btnReportProblem.setOnClickListener(v ->
                startActivity(new Intent(
                        LandlordProfileActivity.this,
                        ReportProblemActivity.class
                ))
        );

        btnLogout.setOnClickListener(v -> logout());
    }

    private void logout() {

        FirebaseAuth.getInstance().signOut();

        SupabaseHelper.clearSession(this);

        Intent intent = new Intent(
                LandlordProfileActivity.this,
                OuterMainActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}