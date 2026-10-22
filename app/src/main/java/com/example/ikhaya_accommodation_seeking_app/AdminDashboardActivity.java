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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";

    private TextView tvStatPending;
    private TextView tvTrendPending;
    private TextView tvStatActive;
    private TextView tvStatUsers;
    private TextView tvStatTotalProperties;
    private TextView tvStatReports;
    private TextView tvTrendReports;
    private TextView tvClearancePercent;
    private ProgressBar pbApprovalRatio;

    private int pendingCount = 0;
    private int activeCount = 0;

    private JSONArray problemReportsCache = new JSONArray();

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private boolean isDashboardVisible = false;

    private final Runnable dataPoll = new Runnable() {
        @Override
        public void run() {
            if (isDashboardVisible) {
                loadAllDashboardCounts();
                refreshHandler.postDelayed(this, 10000);
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
            Toast.makeText(this, R.string.unauthorized_admin_access, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard);

        tvStatPending = findViewById(R.id.tvStatPending);
        // tvTrendPending = findViewById(R.id.tvTrendPending);
        tvStatActive = findViewById(R.id.tvStatActive);
        tvStatUsers = findViewById(R.id.tvStatUsers);
        tvStatTotalProperties = findViewById(R.id.tvStatTotalProperties);
        tvStatReports = findViewById(R.id.tvStatReports);
        // tvTrendReports = findViewById(R.id.tvTrendReports);
        tvClearancePercent = findViewById(R.id.tvClearancePercent);
        pbApprovalRatio = findViewById(R.id.pbApprovalRatio);

        View btnAdminLogout = findViewById(R.id.btnAdminLogout);
        // View btnAdminExit = findViewById(R.id.btnAdminExit);
        TextView tvAdminGreeting = findViewById(R.id.tvAdminGreeting);

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String adminName = getIntent().getStringExtra("USER_NAME");
        if (adminName == null || adminName.isEmpty() || "Admin Preview".equalsIgnoreCase(adminName)) {
            adminName = prefs.getString("USER_NAME", getString(R.string.default_admin_title));
        }

        if (tvAdminGreeting != null) {
            String firstName = adminName.trim().split("\\s+")[0];
            tvAdminGreeting.setText(String.format(Locale.getDefault(), "%s, %s", getString(R.string.greeting_welcome), firstName));
            tvAdminGreeting.setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class))
            );
        }

        if (btnAdminLogout != null) {
            btnAdminLogout.setOnClickListener(v -> performLogout());
        }

        View btnNotificationsAdmin = findViewById(R.id.btnNotificationsAdmin);
        if (btnNotificationsAdmin != null) {
            btnNotificationsAdmin.setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class))
            );
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

        // View cardProblemReports = findViewById(R.id.cardProblemReports);

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
                        if (isFinishing() || isDestroyed()) return;
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
                        if (isFinishing() || isDestroyed()) return;
                        if (tvStatReports != null) tvStatReports.setText(String.valueOf(count));
                        if (tvTrendReports != null) {
                            if (count > 0) {
                                tvTrendReports.setText(String.format(Locale.getDefault(), "%d %s", count, getString(R.string.reports_pending_suffix)));
                                tvTrendReports.setTextColor(Color.parseColor("#FB6340"));
                            } else {
                                tvTrendReports.setText(R.string.no_issues_reported);
                                tvTrendReports.setTextColor(Color.parseColor("#2DCE89"));
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading reports", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void showProblemReportsDialog() {
        if (problemReportsCache.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.title_user_problem_reports)
                    .setMessage(R.string.msg_no_problem_reports)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show();
            return;
        }

        executor.execute(() -> {
            String token = SupabaseHelper.getAccessToken(this);
            HashMap<String, String> userMap = new HashMap<>();
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
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                String[] items = new String[problemReportsCache.length()];
                for (int i = 0; i < problemReportsCache.length(); i++) {
                    JSONObject report = problemReportsCache.optJSONObject(i);
                    if (report == null) continue;

                    String uid = report.optString("reporter_uid", getString(R.string.unknown_reporter_uid));
                    String reporterInfo = userMap.containsKey(uid) ? userMap.get(uid) : uid;
                    String date = report.optString("created_at", "").split("T")[0];
                    String desc = report.optString("description", getString(R.string.no_report_description));

                    items[i] = String.format(Locale.getDefault(), "%s: %s\n%s: %s\n\n%s",
                            getString(R.string.label_date), date,
                            getString(R.string.label_reporter), reporterInfo,
                            desc);
                }

                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_user_problem_reports)
                        .setItems(items, null)
                        .setPositiveButton(R.string.btn_close, null)
                        .show();
            });
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
            if (tvClearancePercent != null) {
                tvClearancePercent.setText(String.format(Locale.getDefault(), "%d%%", rate));
            }
        } else {
            if (pbApprovalRatio != null) pbApprovalRatio.setProgress(100);
            if (tvClearancePercent != null) {
                tvClearancePercent.setText(R.string.clearance_complete_percent);
            }
        }

        if (tvTrendPending != null) {
            if (pendingCount > 0) {
                tvTrendPending.setText(String.format(Locale.getDefault(), "%d %s", pendingCount, getString(R.string.trend_awaiting_review)));
                tvTrendPending.setTextColor(Color.parseColor("#FB6340"));
            } else {
                tvTrendPending.setText(R.string.trend_all_caught_up);
                tvTrendPending.setTextColor(Color.parseColor("#2DCE89"));
            }
        }
    }

    private void performLogout() {
        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        prefs.edit().clear().apply();

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