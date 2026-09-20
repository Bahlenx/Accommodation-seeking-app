package com.example.ikhaya_accomadationapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private String selectedLanguage = "English"; // Default selection

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_greetingpage);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. Reference all language buttons and the Get Started button
        Button btnEnglish = findViewById(R.id.English_btn);
        Button btnIsiXhosa = findViewById(R.id.isiXhosa_btn);
        Button btnAfrikaans = findViewById(R.id.afrikaans_btn);
        Button btnSepedi = findViewById(R.id.sepedi_btn);
        Button btnSesotho = findViewById(R.id.sesotho_btn);
        Button btnIsiZulu = findViewById(R.id.isiZulu_btn);
        Button btnGetStarted = findViewById(R.id.get_started_btn);
        Button btnExistingUser = findViewById(R.id.existingUserBtn);

        // 2. Detect device language and set the default selection
        String deviceLang = Locale.getDefault().getLanguage();
        // deviceLang is "en" for English, "xh" for isiXhosa, "af" for Afrikaans, "zu" for isiZulu, "st" for Sesotho, "nso" for Sepedi.
        
        if (deviceLang.equals("xh")) {
            selectedLanguage = "isiXhosa";
            btnIsiXhosa.setActivated(true);
        } else if (deviceLang.equals("af")) {
            selectedLanguage = "Afrikaans";
            btnAfrikaans.setActivated(true);
        } else if (deviceLang.equals("nso")) {
            selectedLanguage = "Sepedi";
            btnSepedi.setActivated(true);
        } else if (deviceLang.equals("st")) {
            selectedLanguage = "Sesotho";
            btnSesotho.setActivated(true);
        } else if (deviceLang.equals("zu")) {
            selectedLanguage = "isiZulu";
            btnIsiZulu.setActivated(true);
        } else {
            selectedLanguage = "English";
            btnEnglish.setActivated(true);
        }

        // 3. Create a listener to handle toggling language selections
        View.OnClickListener languageClickListener = v -> {
            // Turn off activation state for all language buttons
            btnEnglish.setActivated(false);
            btnIsiXhosa.setActivated(false);
            btnAfrikaans.setActivated(false);
            btnSepedi.setActivated(false);
            btnSesotho.setActivated(false);
            btnIsiZulu.setActivated(false);

            // Activate only the button the user just clicked
            v.setActivated(true);

            // Track which language was chosen
            int id = v.getId();
            if (id == R.id.English_btn) {
                selectedLanguage = "English";
            } else if (id == R.id.isiXhosa_btn) {
                selectedLanguage = "isiXhosa";
            } else if (id == R.id.afrikaans_btn) {
                selectedLanguage = "Afrikaans";
            } else if (id == R.id.sepedi_btn) {
                selectedLanguage = "Sepedi";
            } else if (id == R.id.sesotho_btn) {
                selectedLanguage = "Sesotho";
            } else if (id == R.id.isiZulu_btn) {
                selectedLanguage = "isiZulu";
            }
        };

        // Assign the listener to every language button
        btnEnglish.setOnClickListener(languageClickListener);
        btnIsiXhosa.setOnClickListener(languageClickListener);
        btnAfrikaans.setOnClickListener(languageClickListener);
        btnSepedi.setOnClickListener(languageClickListener);
        btnSesotho.setOnClickListener(languageClickListener);
        btnIsiZulu.setOnClickListener(languageClickListener);

        // 4. "Get Started" button logic to move to the Registration page
        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegistrationActivity.class);
            intent.putExtra("CHOSEN_LANGUAGE", selectedLanguage); // Passes selected language forward
            startActivity(intent);
        });

        // 5. "Existing User" button logic to move to the Login page
        btnExistingUser.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }
}