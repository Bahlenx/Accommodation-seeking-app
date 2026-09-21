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

        btnPersonalInfo.setOnClickListener(v ->
                Toast.makeText(this,
                        "Personal Information coming soon",
                        Toast.LENGTH_SHORT).show());

        btnMyListings.setOnClickListener(v -> {
            Intent intent = new Intent(LandlordProfileActivity.this,
                    MyListingsActivity.class);
            startActivity(intent);
        });

        btnEnquiries.setOnClickListener(v ->
                Toast.makeText(this,
                        "Listing Enquiries coming soon",
                        Toast.LENGTH_SHORT).show());

        btnPaymentsReceived.setOnClickListener(v ->
                Toast.makeText(this,
                        "Payments Received coming soon",
                        Toast.LENGTH_SHORT).show());

        btnPaymentHistory.setOnClickListener(v ->
                Toast.makeText(this,
                        "Payment History coming soon",
                        Toast.LENGTH_SHORT).show());

        btnNotifications.setOnClickListener(v ->
                Toast.makeText(this,
                        "Notifications coming soon",
                        Toast.LENGTH_SHORT).show());

        btnChangePassword.setOnClickListener(v ->
                Toast.makeText(this,
                        "Change Password coming soon",
                        Toast.LENGTH_SHORT).show());

        btnReportProblem.setOnClickListener(v ->
                Toast.makeText(this,
                        "Report a Problem coming soon",
                        Toast.LENGTH_SHORT).show());

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Logged out",
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}