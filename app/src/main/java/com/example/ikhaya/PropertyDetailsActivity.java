package com.example.ikhaya;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class PropertyDetailsActivity extends AppCompatActivity {

    private boolean isTranslated = false;
    private String originalText = "";
    private String translatedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property_details);

        Button btnBack = findViewById(R.id.btnBack);
        TextView propertyDescription = findViewById(R.id.propertyDescription);
        TextView btnTranslate = findViewById(R.id.btnTranslate);
        ProgressBar progressTranslate = findViewById(R.id.progressTranslate);

        btnBack.setOnClickListener(v -> finish());

        // Set the original listing description in English
        originalText = "Affordable room available in a quiet neighborhood. Water and electricity included. Close to public transport and local shops.";
        propertyDescription.setText(originalText);

        // 1. Detect the active app language
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        String currentLang = currentLocale.getLanguage(); // "zu", "xh", "st", or "en"

        // 2. Configure the Translate label based on current locale
        if ("en".equals(currentLang)) {
            // Already in English, no translation needed
            btnTranslate.setVisibility(View.GONE);
        } else {
            btnTranslate.setVisibility(View.VISIBLE);
            String langName = getLanguageDisplayName(currentLang);
            btnTranslate.setText("Translate to " + langName);
        }

        // 3. Handle live translation toggle
        btnTranslate.setOnClickListener(v -> {
            if (isTranslated) {
                // Toggle back to original English
                propertyDescription.setText(originalText);
                btnTranslate.setText("Translate to " + getLanguageDisplayName(currentLang));
                isTranslated = false;
                return;
            }

            // Reuse cached translation without burning an API call
            if (!translatedText.isEmpty()) {
                propertyDescription.setText(translatedText);
                btnTranslate.setText("Show original");
                isTranslated = true;
                return;
            }

            // Show loading spinner
            progressTranslate.setVisibility(View.VISIBLE);
            btnTranslate.setEnabled(false);

            // Trigger Gemini AI Translation
            GeminiTranslator.translate(originalText, currentLang, new GeminiTranslator.TranslationCallback() {
                @Override
                public void onSuccess(String result) {
                    progressTranslate.setVisibility(View.GONE);
                    btnTranslate.setEnabled(true);
                    translatedText = result;
                    propertyDescription.setText(translatedText);
                    btnTranslate.setText("Show original");
                    isTranslated = true;
                }

                @Override
                public void onError(String errorMessage) {
                    progressTranslate.setVisibility(View.GONE);
                    btnTranslate.setEnabled(true);
                    Toast.makeText(PropertyDetailsActivity.this, "Translation failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String getLanguageDisplayName(String code) {
        switch (code) {
            case "zu": return "isiZulu";
            case "xh": return "isiXhosa";
            case "st": return "Sesotho";
            default: return "Local Language";
        }
    }
}