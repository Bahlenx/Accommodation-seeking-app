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
import org.json.JSONObject;

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
    private TextView tvStatReports;
    private TextView tvTrendReports;

    private int pendingCount = 0;
    private int activeCount = 0;

    private JSONArray problemReportsCache = new JSONArray();

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
        tvStatReports = findViewById(R.id.tvStatReports);
        tvTrendReports = findViewById(R.id.tvTrendReports);

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

        View cardProblemReports = findViewById(R.id.cardProblemReports);
        if (cardProblemReports != null) {
            cardProblemReports.setOnClickListener(v -> showProblemReportsDialog());
        }
    }

    private void loadAllDashboardCounts() {
        loadPendingCount();
        loadActiveCount();
        loadLiveUsersCount();
        loadReportsCount();
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

    private void loadReportsCount() {
        String token = SupabaseHelper.getAccessToken(this);
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/reports?select=*&order=created_at.desc", "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    problemReportsCache = arr;
                    final int count = arr.length();
                    runOnUiThread(() -> {
                        if (tvStatReports != null) tvStatReports.setText(String.valueOf(count));
                        if (tvTrendReports != null) {
                            if (count > 0) {
                                tvTrendReports.setText(count + " pending reports");
                                tvTrendReports.setTextColor(Color.parseColor("#FB6340"));
                            } else {
                                tvTrendReports.setText("No issues reported");
                                tvTrendReports.setTextColor(Color.parseColor("#2DCE89"));
                            }
                        }
                    });
                }
            } catch (Exception e) { Log.e(TAG, "Error loading reports", e); }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void showProblemReportsDialog() {
        if (problemReportsCache.length() == 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("User Problem Reports")
                    .setMessage("No problem reports submitted yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        executor.execute(() -> {
            String token = SupabaseHelper.getAccessToken(this);
            java.util.HashMap<String, String> userMap = new java.util.HashMap<>();
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/profiles?select=firebase_uid,full_name,role", "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray profiles = new JSONArray(SupabaseHelper.readResponse(conn));
                    for (int i = 0; i < profiles.length(); i++) {
                        JSONObject p = profiles.getJSONObject(i);
                        userMap.put(p.optString("firebase_uid"), p.optString("full_name") + " (" + p.optString("role") + ")");
                    }
                }
            } catch (Exception ignored) {}
            finally { if (conn != null) conn.disconnect(); }

            runOnUiThread(() -> {
                String[] items = new String[problemReportsCache.length()];
                for (int i = 0; i < problemReportsCache.length(); i++) {
                    JSONObject report = problemReportsCache.optJSONObject(i);
                    if (report == null) continue;
                    
                    String uid = report.optString("reporter_uid", "Unknown UID");
                    String reporterInfo = userMap.containsKey(uid) ? userMap.get(uid) : uid;
                    String date = report.optString("created_at", "").split("T")[0];
                    String desc = report.optString("description", "No description");
                    
                    items[i] = "Date: " + date + "\nReporter: " + reporterInfo + "\n\n" + desc;
                }

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("User Problem Reports")
                        .setItems(items, null)
                        .setPositiveButton("Close", null)
                        .show();
            });
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