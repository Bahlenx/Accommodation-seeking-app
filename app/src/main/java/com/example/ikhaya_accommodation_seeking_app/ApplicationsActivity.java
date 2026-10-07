package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.List;

public class ApplicationsActivity extends AppCompatActivity {

    private LinearLayout applicationsContainer;
    private TextView txtNoApplications;
    private boolean isLandlordView = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applications);

        isLandlordView = getIntent().getBooleanExtra("isLandlord", true);

        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        applicationsContainer = findViewById(R.id.applicationsContainer);
        txtNoApplications = findViewById(R.id.txtNoApplications);

        renderApplications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderApplications();
    }

    private void renderApplications() {
        if (applicationsContainer == null) return;

        applicationsContainer.removeAllViews();
        List<ApplicationManager.RentalApplication> list = ApplicationManager.getApplications(this);

        if (list.isEmpty()) {
            if (txtNoApplications != null) txtNoApplications.setVisibility(View.VISIBLE);
            return;
        }

        if (txtNoApplications != null) txtNoApplications.setVisibility(View.GONE);

        for (ApplicationManager.RentalApplication app : list) {
            CardView card = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 24);
            card.setLayoutParams(cardParams);
            card.setRadius(20);
            card.setCardElevation(4);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(28, 28, 28, 28);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(app.propertyTitle + " (" + app.rentalPrice + ")");
            tvTitle.setTextSize(16);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(Color.BLACK);

            TextView tvApplicant = new TextView(this);
            tvApplicant.setText("Applicant: " + app.applicantName + "\nMove-in: " + app.moveInDate + "\nDuration: " + app.leaseDuration);
            tvApplicant.setTextSize(13);
            tvApplicant.setTextColor(Color.DKGRAY);
            tvApplicant.setPadding(0, 8, 0, 12);

            TextView tvStatus = new TextView(this);
            tvStatus.setText("Status: " + app.status);
            tvStatus.setTextSize(13);
            tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
            tvStatus.setTextColor("APPROVED".equalsIgnoreCase(app.status)
                    ? Color.parseColor("#2E7D32")
                    : ("DECLINED".equalsIgnoreCase(app.status) ? Color.RED : Color.parseColor("#C85A32")));
            tvStatus.setPadding(0, 0, 0, 14);

            content.addView(tvTitle);
            content.addView(tvApplicant);
            content.addView(tvStatus);

            // Landlord Action Buttons (only visible to Landlord if PENDING)
            if (isLandlordView && "PENDING".equalsIgnoreCase(app.status)) {
                LinearLayout actionsLayout = new LinearLayout(this);
                actionsLayout.setOrientation(LinearLayout.HORIZONTAL);

                Button btnApprove = new Button(this);
                btnApprove.setText(getString(R.string.btn_approve_lease));
                btnApprove.setAllCaps(false);
                btnApprove.setTextColor(Color.WHITE);
                btnApprove.setBackgroundResource(R.drawable.selector_regular_button);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, 90, 1);
                p1.setMarginEnd(8);
                btnApprove.setLayoutParams(p1);

                Button btnDecline = new Button(this);
                btnDecline.setText(getString(R.string.btn_decline_lease));
                btnDecline.setAllCaps(false);
                btnDecline.setTextColor(Color.DKGRAY);
                btnDecline.setBackgroundResource(R.drawable.selector_regular_button);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, 90, 1);
                p2.setMarginStart(8);
                btnDecline.setLayoutParams(p2);

                btnApprove.setOnClickListener(v -> {
                    ApplicationManager.updateStatus(this, app.id, "APPROVED");
                    Toast.makeText(this, getString(R.string.msg_application_approved), Toast.LENGTH_SHORT).show();
                    renderApplications();
                });

                btnDecline.setOnClickListener(v -> {
                    ApplicationManager.updateStatus(this, app.id, "DECLINED");
                    Toast.makeText(this, getString(R.string.msg_application_declined), Toast.LENGTH_SHORT).show();
                    renderApplications();
                });

                actionsLayout.addView(btnApprove);
                actionsLayout.addView(btnDecline);
                content.addView(actionsLayout);
            }

            card.addView(content);
            applicationsContainer.addView(card);
        }
    }
}