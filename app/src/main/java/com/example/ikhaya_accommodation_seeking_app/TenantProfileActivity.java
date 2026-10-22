package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class TenantProfileActivity extends AppCompatActivity {

    private static final String TAG = "TenantProfile";
    private TextView txtUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenant_profile);

        txtUserName = findViewById(R.id.txtUserName);
        if (txtUserName != null) {
            txtUserName.setText(R.string.loading_text);
        }

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        bindNavAction(R.id.btnPersonalInfo, PersonalInformationActivity.class);
        bindNavAction(R.id.btnChangeLanguage, LanguageSettingsActivity.class);
        bindNavAction(R.id.btnSavedListings, SavedListingsActivity.class);
        bindNavAction(R.id.btnApplications, ApplicationsActivity.class);
        bindNavAction(R.id.btnPaymentHistory, PaymentHistoryActivity.class);
        bindNavAction(R.id.btnNotifications, NotificationsActivity.class);
        bindNavAction(R.id.btnChangePassword, ChangePasswordActivity.class);
        bindNavAction(R.id.btnReportProblem, ReportProblemActivity.class);

        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }
    }

    private void bindNavAction(int viewId, Class<?> destination) {
        View target = findViewById(viewId);
        if (target != null) {
            target.setOnClickListener(v -> startActivity(new Intent(TenantProfileActivity.this, destination)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTenantName();
    }

    private void loadTenantName() {
        UserProfileLoader.load(this, (name, email, phone) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (txtUserName != null) {
                if (!name.isEmpty()) {
                    txtUserName.setText(name);
                } else if (!email.isEmpty()) {
                    txtUserName.setText(email);
                } else {
                    txtUserName.setText(R.string.tenant);
                }
            }
        });
    }

    private void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            Log.e(TAG, "Firebase sign-out failed", e);
        }

        try {
            SupabaseHelper.clearSession(this);
        } catch (Exception e) {
            Log.e(TAG, "Supabase sign-out failed", e);
        }

        getSharedPreferences("iKhayaSession", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Toast.makeText(
                this,
                R.string.logged_out_successfully,
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(TenantProfileActivity.this, OuterMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}