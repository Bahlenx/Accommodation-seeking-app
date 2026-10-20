package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";

    private TextView tvStatPending, tvTrendPending;
    private TextView tvStatActive;
    private TextView tvStatUsers;
    private TextView tvStatTotalProperties;

    private int pendingCount = 0;
    private int activeCount = 0;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String userRole = getIntent().getStringExtra("USER_ROLE");
        if (userRole == null || userRole.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
            userRole = prefs.getString("USER_ROLE", "");
        }

        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            Toast.makeText(this, "Unauthorized Access! Admin privileges required.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard);

        tvStatPending = findViewById(R.id.tvStatPending);
        tvTrendPending = findViewById(R.id.tvTrendPending);
        tvStatActive = findViewById(R.id.tvStatActive);
        tvStatUsers = findViewById(R.id.tvStatUsers);
        tvStatTotalProperties = findViewById(R.id.tvStatTotalProperties);

        Button btnAdminExit = findViewById(R.id.btnAdminExit);
        TextView tvAdminGreeting = findViewById(R.id.tvAdminGreeting);

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String adminName = getIntent().getStringExtra("USER_NAME");
        if (adminName == null || adminName.isEmpty() || "Admin Preview".equalsIgnoreCase(adminName)) {
            adminName = prefs.getString("USER_NAME", "Administrator");
        }

        if (tvAdminGreeting != null) tvAdminGreeting.setText("Logged in: " + adminName);
        if (btnAdminExit != null) btnAdminExit.setOnClickListener(v -> finish());

        setupMetricCardClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllDashboardCounts();
    }

    private void setupMetricCardClickListeners() {
        View cardPending = findViewById(R.id.cardPending);
        if (cardPending != null) {
            cardPending.setOnClickListener(v -> startActivity(new Intent(this, AdminPendingQueueActivity.class)));
        }

        View cardUsers = findViewById(R.id.cardUsers);
        if (cardUsers != null) {
            cardUsers.setOnClickListener(v -> startActivity(new Intent(this, AdminUsersActivity.class)));
        }

        View cardActive = findViewById(R.id.cardActive);
        if (cardActive != null) {
            cardActive.setOnClickListener(v -> startActivity(new Intent(this, AdminActiveListingsActivity.class)));
        }
    }

    private void loadAllDashboardCounts() {
        loadPendingCount();
        loadActiveCount();
        loadLiveUsersCount();
    }

    private void loadPendingCount() {
        String token = SupabaseHelper.getAccessToken(this);
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/listings?select=id&status=ilike.PENDING", "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    pendingCount = arr.length();
                    runOnUiThread(this::updateAggregatedMetrics);
                }
            } catch (Exception e) { Log.e(TAG, "Error", e); }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void loadActiveCount() {
        String token = SupabaseHelper.getAccessToken(this);
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/listings?select=id&status=ilike.APPROVED", "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    activeCount = arr.length();
                    runOnUiThread(this::updateAggregatedMetrics);
                }
            } catch (Exception e) { Log.e(TAG, "Error", e); }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void loadLiveUsersCount() {
        String token = SupabaseHelper.getAccessToken(this);
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/profiles?select=firebase_uid", "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    Set<String> uniqueIds = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String uid = arr.getJSONObject(i).optString("firebase_uid", "");
                        if (!uid.isEmpty()) uniqueIds.add(uid);
                    }
                    final int count = uniqueIds.size();
                    runOnUiThread(() -> {
                        if (tvStatUsers != null) tvStatUsers.setText(String.valueOf(count));
                    });
                }
            } catch (Exception e) { Log.e(TAG, "Error", e); }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void updateAggregatedMetrics() {
        if (tvStatPending != null) tvStatPending.setText(String.valueOf(pendingCount));
        if (tvStatActive != null) tvStatActive.setText(String.valueOf(activeCount));
        if (tvStatTotalProperties != null) tvStatTotalProperties.setText(String.valueOf(pendingCount + activeCount));

        if (tvTrendPending != null) {
            if (pendingCount > 0) {
                tvTrendPending.setText(pendingCount + " awaiting review");
                tvTrendPending.setTextColor(Color.parseColor("#FB6340"));
            } else {
                tvTrendPending.setText("All caught up");
                tvTrendPending.setTextColor(Color.parseColor("#2DCE89"));
            }
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}