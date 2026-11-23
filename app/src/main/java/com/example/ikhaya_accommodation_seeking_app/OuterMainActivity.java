package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OuterMainActivity extends AppCompatActivity {

    private TextView tvBootStatus;
    private final ExecutorService bootExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_outer_main);

        tvBootStatus = findViewById(R.id.tvBootStatus);

        bootExecutor.execute(() -> {
            try {
                updateBootStatus("Loading preferences & language...");
                Thread.sleep(400);

                updateBootStatus("Syncing diagnostics...");
                DiagnosticLogger.flushPendingCrashReports(this);

                updateBootStatus("Verifying session...");
                boolean hasSession = SupabaseHelper.isSessionWithinThirtyDays(this) && SupabaseHelper.getUserId(this) != null;
                
                Intent intent;
                if (hasSession) {
                    SupabaseHelper.refreshAccessToken(this);
                    String role = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_ROLE", "");

                    if ("landlord".equalsIgnoreCase(role)) {
                        intent = new Intent(OuterMainActivity.this, LandlordDashboardActivity.class);
                    } else if ("admin".equalsIgnoreCase(role)) {
                        intent = new Intent(OuterMainActivity.this, AdminDashboardActivity.class);
                        String fullName = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_NAME", "Admin");
                        intent.putExtra("USER_ROLE", "ADMIN");
                        intent.putExtra("USER_NAME", fullName);
                    } else {
                        intent = new Intent(OuterMainActivity.this, HomeActivity.class);
                    }
                } else {
                    intent = new Intent(OuterMainActivity.this, GreetingActivity.class);
                }

                updateBootStatus("Preparing your workspace...");
                Thread.sleep(300);

                final Intent finalIntent = intent;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    finalIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(finalIntent);
                    finish();
                });
            } catch (Exception e) {
                // fallback
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    startActivity(new Intent(OuterMainActivity.this, GreetingActivity.class));
                    finish();
                });
            }
        });
    }

    private void updateBootStatus(String processText) {
        runOnUiThread(() -> {
            if (tvBootStatus != null) {
                tvBootStatus.animate().cancel();
                tvBootStatus.setText(processText);
                tvBootStatus.setAlpha(0.25f);
                tvBootStatus.animate().alpha(1f).setDuration(140).start();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bootExecutor != null) {
            bootExecutor.shutdownNow();
        }
    }
}
