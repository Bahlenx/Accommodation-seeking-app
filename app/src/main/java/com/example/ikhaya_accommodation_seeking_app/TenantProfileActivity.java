package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class TenantProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenant_profile);

        Button btnBack =
                findViewById(R.id.btnBack);

        Button btnPersonalInfo =
                findViewById(R.id.btnPersonalInfo);

        Button btnSavedListings =
                findViewById(R.id.btnSavedListings);

        Button btnApplications =
                findViewById(R.id.btnApplications);

        Button btnPaymentHistory =
                findViewById(R.id.btnPaymentHistory);

        Button btnNotifications =
                findViewById(R.id.btnNotifications);

        Button btnChangePassword =
                findViewById(R.id.btnChangePassword);

        Button btnReportProblem =
                findViewById(R.id.btnReportProblem);

        Button btnLogout =
                findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(
                v -> finish()
        );

        btnPersonalInfo.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                PersonalInformationActivity.class
                        )
                )
        );

        btnSavedListings.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                SavedListingsActivity.class
                        )
                )
        );

        btnApplications.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                ApplicationsActivity.class
                        )
                )
        );

        btnPaymentHistory.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                PaymentHistoryActivity.class
                        )
                )
        );

        btnNotifications.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                NotificationsActivity.class
                        )
                )
        );

        btnChangePassword.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                ChangePasswordActivity.class
                        )
                )
        );

        btnReportProblem.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                ReportProblemActivity.class
                        )
                )
        );

        btnLogout.setOnClickListener(
                v -> logout()
        );
    }

    private void logout() {

        FirebaseAuth.getInstance()
                .signOut();

        SupabaseHelper.clearSession(this);

        Intent intent =
                new Intent(
                        this,
                        OuterMainActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}