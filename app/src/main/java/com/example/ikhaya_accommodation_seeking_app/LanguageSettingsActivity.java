package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

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

        View btnBack = findViewById(R.id.btnBack);
        Button btnApply = findViewById(R.id.btnApplyLanguage);
        RadioGroup radioGroup = findViewById(R.id.radioGroupLanguages);

        RadioButton rbEnglish = findViewById(R.id.rbEnglish);
        RadioButton rbZulu = findViewById(R.id.rbZulu);
        RadioButton rbXhosa = findViewById(R.id.rbXhosa);
        RadioButton rbSepedi = findViewById(R.id.rbSepedi);
        RadioButton rbSesotho = findViewById(R.id.rbSesotho);
        RadioButton rbAfrikaans = findViewById(R.id.rbAfrikaans);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        String currentLang = currentLocale.getLanguage();
        selectedLanguageCode = currentLang;

        switch (currentLang) {
            case "zu":
                if (rbZulu != null) rbZulu.setChecked(true);
                break;
            case "xh":
                if (rbXhosa != null) rbXhosa.setChecked(true);
                break;
            case "nso":
                if (rbSepedi != null) rbSepedi.setChecked(true);
                break;
            case "st":
                if (rbSesotho != null) rbSesotho.setChecked(true);
                break;
            case "af":
                if (rbAfrikaans != null) rbAfrikaans.setChecked(true);
                break;
            default:
                if (rbEnglish != null) rbEnglish.setChecked(true);
                break;
        }

        if (radioGroup != null) {
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rbZulu) {
                    selectedLanguageCode = "zu";
                } else if (checkedId == R.id.rbXhosa) {
                    selectedLanguageCode = "xh";
                } else if (checkedId == R.id.rbSepedi) {
                    selectedLanguageCode = "nso";
                } else if (checkedId == R.id.rbSesotho) {
                    selectedLanguageCode = "st";
                } else if (checkedId == R.id.rbAfrikaans) {
                    selectedLanguageCode = "af";
                } else {
                    selectedLanguageCode = "en";
                }
            });
        }

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(selectedLanguageCode);
                AppCompatDelegate.setApplicationLocales(appLocale);
                Toast.makeText(this, R.string.language_updated_successfully, Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}