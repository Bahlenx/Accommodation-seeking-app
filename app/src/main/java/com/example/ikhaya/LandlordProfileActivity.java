package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

        btnPersonalInfo.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    LandlordPersonalInformationActivity.class
            );

            startActivity(intent);
        });

        btnMyListings.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    MyListingsActivity.class
            );

            startActivity(intent);
        });

        btnEnquiries.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    EnquiriesActivity.class
            );

            startActivity(intent);
        });

        btnPaymentsReceived.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    PaymentsReceivedActivity.class
            );

            startActivity(intent);
        });
        btnPaymentHistory.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    PaymentHistoryActivity.class
            );

            startActivity(intent);
        });
        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    NotificationsActivity.class
            );

            startActivity(intent);
        });

        btnChangePassword.setOnClickListener(v ->
                Toast.makeText(this,
                        "Change Password coming soon",
                        Toast.LENGTH_SHORT).show());

        btnReportProblem.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordProfileActivity.this,
                    ReportProblemActivity.class
            );

            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Logged out",
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}