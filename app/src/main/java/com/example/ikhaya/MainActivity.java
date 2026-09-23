package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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