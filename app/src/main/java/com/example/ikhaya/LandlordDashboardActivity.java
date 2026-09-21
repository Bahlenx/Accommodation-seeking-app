package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LandlordDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_dashboard);

        Button btnAddListing = findViewById(R.id.btnAddListing);
        Button btnProfile = findViewById(R.id.btnProfile);

        Button navHome = findViewById(R.id.navHome);
        Button navListings = findViewById(R.id.navListings);
        Button navAdd = findViewById(R.id.navAdd);
        Button navProfile = findViewById(R.id.navProfile);

        // Add Accommodation
        btnAddListing.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordDashboardActivity.this,
                    AddListingActivity.class
            );
            startActivity(intent);
        });

        // Bottom navigation - Add
        navAdd.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordDashboardActivity.this,
                    AddListingActivity.class
            );
            startActivity(intent);
        });

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(LandlordDashboardActivity.this,
                    LandlordProfileActivity.class);
            startActivity(intent);
        });

        navProfile.setOnClickListener(v -> {
            Intent intent = new Intent(LandlordDashboardActivity.this,
                    LandlordProfileActivity.class);
            startActivity(intent);
        });

        navListings.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandlordDashboardActivity.this,
                    MyListingsActivity.class
            );
            startActivity(intent);
        });

        // Home
        navHome.setOnClickListener(v ->
                Toast.makeText(this, "You are already on the dashboard", Toast.LENGTH_SHORT).show()
        );
    }
}