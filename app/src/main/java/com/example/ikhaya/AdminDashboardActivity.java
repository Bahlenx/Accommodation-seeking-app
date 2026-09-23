package com.example.ikhaya;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private RecyclerView rvPendingListings;
    private AdminListingAdapter adapter;
    private List<AdminListing> mockListings;
    private TextView tvEmptyPending;
    private TextView tvStatPending;
    private RecyclerView rvUserManagement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Role-Based Access Control
        String userRole = getIntent().getStringExtra("USER_ROLE");
        if (userRole == null || !userRole.equals("ADMIN")) {
            Toast.makeText(this, "Unauthorized Access! Admin privileges required.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard);

        // Bind Views
        rvPendingListings = findViewById(R.id.rvPendingListings);
        tvEmptyPending = findViewById(R.id.tvEmptyPending);
        tvStatPending = findViewById(R.id.tvStatPending);
        Button btnAdminExit = findViewById(R.id.btnAdminExit);
        TextView tvAdminGreeting = findViewById(R.id.tvAdminGreeting);

        String adminName = getIntent().getStringExtra("USER_NAME");
        if (adminName != null) {
            tvAdminGreeting.setText(adminName);
        }

        // Setup Exit Button
        btnAdminExit.setOnClickListener(v -> finish());

        // Setup Pending Listings RecyclerView
        rvPendingListings.setLayoutManager(new LinearLayoutManager(this));
        loadMockPendingListings();
        adapter = new AdminListingAdapter(this, mockListings, this::refreshDashboardState);
        rvPendingListings.setAdapter(adapter);

        refreshDashboardState();

        // ---------------------------------------------------
        // STAGE 2: Setup User Management RecyclerView
        // ---------------------------------------------------
        rvUserManagement = findViewById(R.id.rvUserManagement);
        rvUserManagement.setLayoutManager(new LinearLayoutManager(this));

        List<AdminUser> mockUsers = new ArrayList<>();
        mockUsers.add(new AdminUser("Landlord Dave", "LANDLORD", false));
        mockUsers.add(new AdminUser("Sarah Nkosi", "LANDLORD", false));
        mockUsers.add(new AdminUser("Bongani M.", "TENANT", true)); // Suspended user

        AdminUserAdapter userAdapter = new AdminUserAdapter(this, mockUsers);
        rvUserManagement.setAdapter(userAdapter);
    }

    private void loadMockPendingListings() {
        mockListings = new ArrayList<>();
        mockListings.add(new AdminListing("1", "Standard Single Room", "Centurion, Gauteng • R 3,500 / month", "Landlord Dave", "PENDING"));
        mockListings.add(new AdminListing("2", "2-Bedroom Apartment", "Pretoria Central • R 6,200 / month", "Sarah Nkosi", "PENDING"));
        mockListings.add(new AdminListing("3", "Shared Student Commune", "Hatfield, Pretoria • R 2,800 / month", "Campus Homes", "PENDING"));
    }

    private void refreshDashboardState() {
        // Update Pending Counter
        if (tvStatPending != null) {
            tvStatPending.setText(String.valueOf(mockListings.size()));
        }

        // Toggle Empty Placeholder
        if (mockListings.isEmpty()) {
            rvPendingListings.setVisibility(View.GONE);
            tvEmptyPending.setVisibility(View.VISIBLE);
        } else {
            rvPendingListings.setVisibility(View.VISIBLE);
            tvEmptyPending.setVisibility(View.GONE);
        }
    }
}