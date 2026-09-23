package com.example.ikhaya;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class LanguageSettingsActivity extends AppCompatActivity {

    private String selectedLanguageCode = "en";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_settings);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnApply = findViewById(R.id.btnApplyLanguage);
        RadioGroup radioGroup = findViewById(R.id.radioGroupLanguages);

        RadioButton rbEnglish = findViewById(R.id.rbEnglish);
        RadioButton rbZulu = findViewById(R.id.rbZulu);
        RadioButton rbXhosa = findViewById(R.id.rbXhosa);
        RadioButton rbSesotho = findViewById(R.id.rbSesotho);

        btnBack.setOnClickListener(v -> finish());

        // Check the currently active locale radio button
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        String currentLang = currentLocale.getLanguage();
        selectedLanguageCode = currentLang;

        switch (currentLang) {
            case "zu":
                rbZulu.setChecked(true);
                break;
            case "xh":
                rbXhosa.setChecked(true);
                break;
            case "st":
                rbSesotho.setChecked(true);
                break;
            default:
                rbEnglish.setChecked(true);
                break;
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbZulu) {
                selectedLanguageCode = "zu";
            } else if (checkedId == R.id.rbXhosa) {
                selectedLanguageCode = "xh";
            } else if (checkedId == R.id.rbSesotho) {
                selectedLanguageCode = "st";
            } else {
                selectedLanguageCode = "en";
            }
        });

        btnApply.setOnClickListener(v -> {
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(selectedLanguageCode);
            AppCompatDelegate.setApplicationLocales(appLocale);
            finish();
        });
    }
}