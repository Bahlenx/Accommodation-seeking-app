package com.example.ikhaya;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import android.content.Intent;

public class TenantProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenant_profile);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPersonalInfo = findViewById(R.id.btnPersonalInfo);
        Button btnSavedListings = findViewById(R.id.btnSavedListings);
        Button btnApplications = findViewById(R.id.btnApplications);
        Button btnPaymentHistory = findViewById(R.id.btnPaymentHistory);
        Button btnNotifications = findViewById(R.id.btnNotifications);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        Button btnReportProblem = findViewById(R.id.btnReportProblem);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> finish());

        btnPersonalInfo.setOnClickListener(v ->
                Toast.makeText(this,
                        "Personal Information coming soon",
                        Toast.LENGTH_SHORT).show());

        btnSavedListings.setOnClickListener(v ->
                Toast.makeText(this,
                        "Saved Listings coming soon",
                        Toast.LENGTH_SHORT).show());

        btnApplications.setOnClickListener(v ->
                Toast.makeText(this,
                        "Applications coming soon",
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

        btnChangeLanguage.setOnClickListener(v -> {
            Intent intent = new Intent(TenantProfileActivity.this, LanguageSettingsActivity.class);
            startActivity(intent);
        });

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

    private void showLanguageDialog() {
        String[] languages = {"English", "isiZulu", "isiXhosa", "Sesotho"};
        String[] langCodes = {"en", "zu", "xh", "st"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.change_language));
        builder.setItems(languages, (dialog, which) -> {
            String selectedCode = langCodes[which];
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(selectedCode);
            AppCompatDelegate.setApplicationLocales(appLocale);
        });
        builder.create().show();
    }
}