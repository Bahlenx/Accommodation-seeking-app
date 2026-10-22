package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LandlordProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_profile);

        View back = findViewById(R.id.btnBack);

        if (back != null) {
            back.setOnClickListener(v -> finish());
        }

        TextView name = findViewById(R.id.txtLandlordName);
        TextView type = findViewById(R.id.txtLandlordType);

        if (type != null) {
            type.setText(R.string.landlord);
        }

        if (name != null) {
            String saved = getSharedPreferences(
                    "iKhayaSession",
                    MODE_PRIVATE
            ).getString("USER_NAME", "");

            name.setText(
                    saved == null || saved.trim().isEmpty()
                            ? getString(R.string.landlord_name)
                            : saved.trim()
            );
        }

        open(
                R.id.btnPersonalInfo,
                LandlordPersonalInformationActivity.class
        );

        open(
                R.id.btnChangeLanguage,
                LanguageSettingsActivity.class
        );

        open(
                R.id.btnMyListings,
                MyListingsActivity.class
        );

        open(
                R.id.btnEnquiries,
                EnquiriesActivity.class
        );

        open(
                R.id.btnNotifications,
                NotificationsActivity.class
        );

        open(
                R.id.btnChangePassword,
                ChangePasswordActivity.class
        );

        open(
                R.id.btnReportProblem,
                ReportProblemActivity.class
        );

        Button logout = findViewById(R.id.btnLogout);

        if (logout != null) {
            logout.setOnClickListener(v -> logout());
        }
    }

    private void open(int id, Class<?> destination) {
        Button button = findViewById(id);

        if (button != null) {
            button.setOnClickListener(v ->
                    startActivity(new Intent(this, destination))
            );
        }
    }

    private void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            android.util.Log.e(
                    "LandlordProfile",
                    "Firebase sign-out failed",
                    e
            );
        }

        try {
            SupabaseHelper.clearSession(this);
        } catch (Exception e) {
            android.util.Log.e(
                    "LandlordProfile",
                    "Supabase sign-out failed",
                    e
            );
        }

        getSharedPreferences(
                "iKhayaSession",
                MODE_PRIVATE
        ).edit().clear().apply();

        Toast.makeText(
                this,
                R.string.logged_out_successfully,
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(
                this,
                OuterMainActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}