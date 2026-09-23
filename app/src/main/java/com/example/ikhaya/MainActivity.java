package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Dev Bypass Buttons
        Button btnDevTenant = findViewById(R.id.btnDevTenant);
        Button btnDevLandlord = findViewById(R.id.btnDevLandlord);
        Button btnDevAdmin = findViewById(R.id.btnDevAdmin);

        // 1. Bypass as Tenant -> Direct to HomeActivity
        if (btnDevTenant != null) {
            btnDevTenant.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                intent.putExtra("USER_ROLE", "TENANT");
                intent.putExtra("USER_ID", "mock_tenant_001");
                intent.putExtra("USER_NAME", "Test Tenant");
                startActivity(intent);
            });
        }

        // 2. Bypass as Landlord -> Direct to LandlordDashboardActivity
        if (btnDevLandlord != null) {
            btnDevLandlord.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, LandlordDashboardActivity.class);
                intent.putExtra("USER_ROLE", "LANDLORD");
                intent.putExtra("USER_ID", "mock_landlord_001");
                intent.putExtra("USER_NAME", "Test Landlord");
                startActivity(intent);
            });
        }

        // 3. Bypass as Admin -> Direct to AdminDashboardActivity
        if (btnDevAdmin != null) {
            btnDevAdmin.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AdminDashboardActivity.class);
                intent.putExtra("USER_ROLE", "ADMIN");
                intent.putExtra("USER_ID", "mock_admin_001");
                intent.putExtra("USER_NAME", "System Admin");
                startActivity(intent);
            });
        }

        // Language selection buttons
        Button btnZulu = findViewById(R.id.btnZulu);
        Button btnEnglish = findViewById(R.id.btnEnglish);
        Button btnXhosa = findViewById(R.id.btnXhosa);
        Button btnSesotho = findViewById(R.id.btnSesotho);

        // Get Started button
        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        // Set locale listeners
        if (btnZulu != null) {
            btnZulu.setOnClickListener(v -> setAppLocale("zu"));
        }

        if (btnEnglish != null) {
            btnEnglish.setOnClickListener(v -> setAppLocale("en"));
        }

        if (btnXhosa != null) {
            btnXhosa.setOnClickListener(v -> setAppLocale("xh"));
        }

        if (btnSesotho != null) {
            btnSesotho.setOnClickListener(v -> setAppLocale("st"));
        }

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            });
        }
    }

    /**
     * Changes the application's active locale and refreshes strings dynamically.
     * @param languageCode ISO code: "en", "zu", "xh", or "st"
     */
    private void setAppLocale(String languageCode) {
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }
}