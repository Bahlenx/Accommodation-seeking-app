package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class AdminProfileActivity extends AppCompatActivity {

    private TextView txtAdminName, txtAdminEmail, tvAvatarInitials, tvCurrentLanguage;
    private View viewNotificationDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_profile);

        View back = findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }

        txtAdminName = findViewById(R.id.txtAdminName);
        txtAdminEmail = findViewById(R.id.txtAdminEmail);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);
        viewNotificationDot = findViewById(R.id.viewNotificationDot);

        open(R.id.btnAdminBansAppeals, AdminBansAppealsActivity.class);
        open(R.id.btnAdminReports, AdminReportsActivity.class);
        open(R.id.btnAdminUsers, AdminUsersActivity.class);

        open(R.id.btnPersonalInfo, PersonalInformationActivity.class);
        open(R.id.btnChangeLanguage, LanguageSettingsActivity.class);
        open(R.id.btnNotifications, NotificationsActivity.class);
        open(R.id.btnChangePassword, ChangePasswordActivity.class);

        View btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
        TextView tvNotifSettingsSummary = findViewById(R.id.tvNotifSettingsSummary);

        if (btnNotificationSettings != null) {
            btnNotificationSettings.setOnClickListener(v -> NotificationHelper.openNotificationSettingsDialog(this, () -> {
                if (tvNotifSettingsSummary != null) {
                    tvNotifSettingsSummary.setText(NotificationHelper.getPreferencesSummaryLabel(this));
                }
            }));
        }

        Button logout = findViewById(R.id.btnLogout);
        if (logout != null) {
            logout.setOnClickListener(v -> logout());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tvNotifSettingsSummary = findViewById(R.id.tvNotifSettingsSummary);
        if (tvNotifSettingsSummary != null) {
            tvNotifSettingsSummary.setText(NotificationHelper.getPreferencesSummaryLabel(this));
        }

        if (viewNotificationDot != null && NotificationHelper.hasUnreadDot(this)) {
            viewNotificationDot.setVisibility(View.VISIBLE);
        } else if (viewNotificationDot != null) {
            viewNotificationDot.setVisibility(View.GONE);
        }

        NotificationHelper.checkAndTriggerNotifications(this);
        loadDynamicData();
        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(getActiveLanguageDisplayName());
        }
    }

    private void loadDynamicData() {
        android.content.SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String savedName = prefs.getString("USER_NAME", "");
        String savedEmail = prefs.getString("USER_EMAIL", "");

        if (txtAdminName != null) {
            txtAdminName.setText(savedName == null || savedName.trim().isEmpty() ? "Admin" : savedName.trim());
        }
        if (txtAdminEmail != null) {
            txtAdminEmail.setText(savedEmail == null || savedEmail.trim().isEmpty() ? "" : savedEmail.trim());
        }
        if (tvAvatarInitials != null) {
            tvAvatarInitials.setText(extractInitials(savedName));
        }
    }

    private String extractInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "AD";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            String w = parts[0];
            return w.length() >= 2 ? w.substring(0, 2).toUpperCase() : w.toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private String getActiveLanguageDisplayName() {
        android.content.SharedPreferences p = getSharedPreferences("LanguagePrefs", MODE_PRIVATE);
        String currentLang = p.getString("SelectedLanguage", "en");
        switch (currentLang) {
            case "zu": return "isiZulu";
            case "xh": return "isiXhosa";
            case "af": return "Afrikaans";
            case "st": return "Sesotho";
            case "nso": return "Sepedi";
            default: return "English";
        }
    }

    private void open(int id, Class<?> target) {
        View v = findViewById(id);
        if (v != null) {
            v.setOnClickListener(view -> startActivity(new Intent(this, target)));
        }
    }

    private void logout() {
        SupabaseHelper.clearSession(this);
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}