package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.net.HttpURLConnection;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";

    private TextView tvStatPending;
    private TextView tvStatActive;
    private TextView tvStatUsers;
    private TextView tvStatTotalProperties;
    private TextView tvClearancePercent;
    private ProgressBar pbApprovalRatio;

    private int pendingCount = 0;
    private int activeCount = 0;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    
    private Handler refreshHandler = new Handler(Looper.getMainLooper());
    private boolean isDashboardVisible = false;

    private final Runnable dataPoll = new Runnable() {
        @Override
        public void run() {
            if (isDashboardVisible) {
                loadAllDashboardCounts();
                refreshHandler.postDelayed(this, 10000); // Check every 10 seconds
            }
        }
    };

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
        tvStatActive = findViewById(R.id.tvStatActive);
        tvStatUsers = findViewById(R.id.tvStatUsers);
        tvStatTotalProperties = findViewById(R.id.tvStatTotalProperties);
        tvClearancePercent = findViewById(R.id.tvClearancePercent);
        pbApprovalRatio = findViewById(R.id.pbApprovalRatio);

        View btnAdminLogout = findViewById(R.id.btnAdminLogout);
        TextView tvAdminGreeting = findViewById(R.id.tvAdminGreeting);

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String adminName = getIntent().getStringExtra("USER_NAME");
        if (adminName == null || adminName.isEmpty() || "Admin Preview".equalsIgnoreCase(adminName)) {
            adminName = prefs.getString("USER_NAME", "Administrator");
        }

        if (tvAdminGreeting != null) {
            String firstName = adminName.trim().split("\\s+")[0];
            tvAdminGreeting.setText("Welcome, " + firstName);
        }
        if (btnAdminLogout != null) {
            btnAdminLogout.setOnClickListener(v -> performLogout());
        }

        setupActionCards();
        updateGraphHighlight();
    }

    private void updateGraphHighlight() {
        View barMon = findViewById(R.id.barMonday);
        View barTue = findViewById(R.id.barTuesday);
        View barWed = findViewById(R.id.barWednesday);
        View barThu = findViewById(R.id.barThursday);
        View barFri = findViewById(R.id.barFriday);
        View barSat = findViewById(R.id.barSaturday);
        View barSun = findViewById(R.id.barSunday);

        View[] bars = {null, barSun, barMon, barTue, barWed, barThu, barFri, barSat};

        int inactiveColor = Color.parseColor("#FFD8CC");
        int activeColor = Color.parseColor("#BA5536");

        for (int i = 1; i < bars.length; i++) {
            if (bars[i] != null) {
                if (bars[i].getBackground() != null) {
                    bars[i].getBackground().setTint(inactiveColor);
                } else {
                    bars[i].setBackgroundColor(inactiveColor);
                }
                bars[i].getLayoutParams().height = dpToPx(35);
                bars[i].requestLayout();
            }
        }

        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_WEEK);

        if (bars[currentDay] != null) {
            if (bars[currentDay].getBackground() != null) {
                bars[currentDay].getBackground().setTint(activeColor);
            } else {
                bars[currentDay].setBackgroundColor(activeColor);
            }
            bars[currentDay].getLayoutParams().height = dpToPx(62);
            bars[currentDay].requestLayout();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isDashboardVisible = true;
        refreshHandler.post(dataPoll);
    }

    @Override
    protected void onPause() {
        isDashboardVisible = false;
        refreshHandler.removeCallbacks(dataPoll);
        super.onPause();
    }

    private void setupActionCards() {
        // Top 4 Metrics Cards
        View cardMetricPending = findViewById(R.id.cardMetricPending);
        if (cardMetricPending != null) {
            cardMetricPending.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminPendingQueueActivity.class))
            );
        }

        View cardMetricActive = findViewById(R.id.cardMetricActive);
        if (cardMetricActive != null) {
            cardMetricActive.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminActiveListingsActivity.class))
            );
        }

        View cardMetricUsers = findViewById(R.id.cardMetricUsers);
        if (cardMetricUsers != null) {
            cardMetricUsers.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminUsersActivity.class))
            );
        }

        View cardMetricIngest = findViewById(R.id.cardMetricIngest);
        if (cardMetricIngest != null) {
            cardMetricIngest.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminActiveListingsActivity.class))
            );
        }

        // Moderation Queues Section Cards
        View cardPending = findViewById(R.id.cardPending);
        if (cardPending != null) {
            cardPending.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminPendingQueueActivity.class))
            );
        }

        View cardActive = findViewById(R.id.cardActive);
        if (cardActive != null) {
            cardActive.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminActiveListingsActivity.class))
            );
        }

        View cardUsers = findViewById(R.id.cardUsers);
        if (cardUsers != null) {
            cardUsers.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminUsersActivity.class))
            );
        }

        View cardReports = findViewById(R.id.cardReports);
        if (cardReports != null) {
            cardReports.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminReportsActivity.class))
            );
        }

        // External Link to Supabase Web Console
        View rowSupabaseEngine = findViewById(R.id.rowSupabaseEngine);
        if (rowSupabaseEngine != null) {
            rowSupabaseEngine.setOnClickListener(v -> {
                Intent browserIntent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://supabase.com/dashboard")
                );
                startActivity(browserIntent);
            });
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
            } catch (Exception e) {
                Log.e(TAG, "Error fetching pending listings", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
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
            } catch (Exception e) {
                Log.e(TAG, "Error fetching active listings", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
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
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user counts", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void updateAggregatedMetrics() {
        if (tvStatPending != null) tvStatPending.setText(String.valueOf(pendingCount));
        if (tvStatActive != null) tvStatActive.setText(String.valueOf(activeCount));

        int totalProperties = pendingCount + activeCount;
        if (tvStatTotalProperties != null) {
            tvStatTotalProperties.setText(String.valueOf(totalProperties));
        }

        if (totalProperties > 0) {
            int rate = (int) Math.round(((double) activeCount / totalProperties) * 100);
            if (pbApprovalRatio != null) pbApprovalRatio.setProgress(rate);
            if (tvClearancePercent != null) tvClearancePercent.setText(rate + "%");
        } else {
            if (pbApprovalRatio != null) pbApprovalRatio.setProgress(100);
            if (tvClearancePercent != null) tvClearancePercent.setText("100%");
        }
    }

    private void performLogout() {
        // Clear cached Supabase credentials
        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Redirect to LoginActivity and clear back stack
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}