package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class OuterMainActivity extends AppCompatActivity {

    private String selectedLanguage = "English";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_greetingpage);

        // Ensure true edge-to-edge: do not pad the root layout so the background fills 100% of the screen
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> insets);
        }

        // --- DEVELOPER BYPASS SHORTCUT ---
        ImageView appIcon = findViewById(R.id.app_icon);
        if (appIcon != null) {
            appIcon.setOnLongClickListener(v -> {
                String[] destinations = {
                        "Landlord Dashboard",
                        "Tenant Home / Search",
                        "Admin Console",
                        "Add Listing Screen",
                        "Property Details (Map & Gemini)",
                        "Tenant Profile",
                        "Landlord Profile"
                };

                new AlertDialog.Builder(OuterMainActivity.this)
                        .setTitle("Developer Bypass Navigation")
                        .setItems(destinations, (dialog, which) -> {
                            switch (which) {
                                case 0:
                                    startActivity(new Intent(OuterMainActivity.this, LandlordDashboardActivity.class));
                                    break;
                                case 1:
                                    startActivity(new Intent(OuterMainActivity.this, HomeActivity.class));
                                    break;
                                case 2:
                                    Intent adminIntent = new Intent(OuterMainActivity.this, AdminDashboardActivity.class);
                                    adminIntent.putExtra("USER_ROLE", "ADMIN");
                                    adminIntent.putExtra("USER_NAME", "Admin Preview");
                                    startActivity(adminIntent);
                                    break;
                                case 3:
                                    startActivity(new Intent(OuterMainActivity.this, AddListingActivity.class));
                                    break;
                                case 4:
                                    startActivity(new Intent(OuterMainActivity.this, PropertyDetailsActivity.class));
                                    break;
                                case 5:
                                    startActivity(new Intent(OuterMainActivity.this, TenantProfileActivity.class));
                                    break;
                                case 6:
                                    startActivity(new Intent(OuterMainActivity.this, LandlordProfileActivity.class));
                                    break;
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }

        // 1. Reference all buttons
        Button btnEnglish = findViewById(R.id.English_btn);
        Button btnIsiXhosa = findViewById(R.id.isiXhosa_btn);
        Button btnAfrikaans = findViewById(R.id.afrikaans_btn);
        Button btnSepedi = findViewById(R.id.sepedi_btn);
        Button btnSesotho = findViewById(R.id.sesotho_btn);
        Button btnIsiZulu = findViewById(R.id.isiZulu_btn);
        Button btnGetStarted = findViewById(R.id.get_started_btn);
        Button btnExistingUser = findViewById(R.id.existingUserBtn);

        Button[] langButtons = {btnEnglish, btnIsiXhosa, btnAfrikaans, btnSepedi, btnSesotho, btnIsiZulu};

        int activeColor = android.graphics.Color.parseColor("#C85A32");
        int inactiveColor = android.graphics.Color.parseColor("#EADFD5");

        java.util.function.Consumer<Button> highlightButton = (activeBtn) -> {
            for (Button b : langButtons) {
                if (b != null) {
                    boolean isActive = (b == activeBtn);
                    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isActive ? activeColor : inactiveColor));
                    b.setTextColor(isActive ? android.graphics.Color.WHITE : android.graphics.Color.BLACK);
                }
            }
        };

        // 2. Fetch current locale & highlight matching button
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        String currentLang = currentLocale.getLanguage();

        if ("xh".equals(currentLang)) {
            selectedLanguage = "isiXhosa";
            highlightButton.accept(btnIsiXhosa);
        } else if ("af".equals(currentLang)) {
            selectedLanguage = "Afrikaans";
            highlightButton.accept(btnAfrikaans);
        } else if ("nso".equals(currentLang)) {
            selectedLanguage = "Sepedi";
            highlightButton.accept(btnSepedi);
        } else if ("st".equals(currentLang)) {
            selectedLanguage = "Sesotho";
            highlightButton.accept(btnSesotho);
        } else if ("zu".equals(currentLang)) {
            selectedLanguage = "isiZulu";
            highlightButton.accept(btnIsiZulu);
        } else {
            selectedLanguage = "English";
            highlightButton.accept(btnEnglish);
        }

        // 3. Click listener to change language dynamically
        View.OnClickListener languageClickListener = v -> {
            highlightButton.accept((Button) v);

            String tag = "en";
            int id = v.getId();
            if (id == R.id.English_btn) {
                selectedLanguage = "English";
                tag = "en";
            } else if (id == R.id.isiXhosa_btn) {
                selectedLanguage = "isiXhosa";
                tag = "xh";
            } else if (id == R.id.afrikaans_btn) {
                selectedLanguage = "Afrikaans";
                tag = "af";
            } else if (id == R.id.sepedi_btn) {
                selectedLanguage = "Sepedi";
                tag = "nso";
            } else if (id == R.id.sesotho_btn) {
                selectedLanguage = "Sesotho";
                tag = "st";
            } else if (id == R.id.isiZulu_btn) {
                selectedLanguage = "isiZulu";
                tag = "zu";
            }

            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(tag);
            AppCompatDelegate.setApplicationLocales(appLocale);
        };

        if (btnEnglish != null) btnEnglish.setOnClickListener(languageClickListener);
        if (btnIsiXhosa != null) btnIsiXhosa.setOnClickListener(languageClickListener);
        if (btnAfrikaans != null) btnAfrikaans.setOnClickListener(languageClickListener);
        if (btnSepedi != null) btnSepedi.setOnClickListener(languageClickListener);
        if (btnSesotho != null) btnSesotho.setOnClickListener(languageClickListener);
        if (btnIsiZulu != null) btnIsiZulu.setOnClickListener(languageClickListener);

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> {
                Intent intent = new Intent(OuterMainActivity.this, RegistrationActivity.class);
                intent.putExtra("CHOSEN_LANGUAGE", selectedLanguage);
                startActivity(intent);
            });
        }

        if (btnExistingUser != null) {
            btnExistingUser.setOnClickListener(v -> {
                Intent intent = new Intent(OuterMainActivity.this, LoginActivity.class);
                intent.putExtra("EXPLICIT_LOGIN", true);
                startActivity(intent);
            });
        }
    }
}