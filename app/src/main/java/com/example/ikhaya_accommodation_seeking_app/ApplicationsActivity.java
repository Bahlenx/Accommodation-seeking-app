package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Color;
import android.graphics.Typeface;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applications);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

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
        List<ApplicationManager.RentalApplication> list = ApplicationManager.getApplications();

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
            cardParams.setMargins(0, 0, 0, 20);
            card.setLayoutParams(cardParams);
            card.setRadius(20);
            card.setCardElevation(3);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(24, 24, 24, 24);

            // Property Title & Rate
            TextView tvTitle = new TextView(this);
            tvTitle.setText(app.propertyTitle + " (" + app.rentalPrice + ")");
            tvTitle.setTextSize(16);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.BLACK);

            // Detailed Applicant Bio
            TextView tvDetails = new TextView(this);
            String info = "• Applicant: " + app.applicantName + " (" + app.occupation + ")\n"
                    + "• Contact: " + app.phone + " | Ref: " + app.emergencyContact + "\n"
                    + "• Move-in: " + app.moveInDate + " (" + app.leaseDuration + ")\n"
                    + "• Occupants: " + app.occupants + "\n"
                    + "• Note: \"" + app.note + "\"";
            tvDetails.setText(info);
            tvDetails.setTextSize(13);
            tvDetails.setTextColor(Color.parseColor("#444444"));
            tvDetails.setLineSpacing(4, 1);
            tvDetails.setPadding(0, 10, 0, 14);

            // Status Badge
            TextView tvStatus = new TextView(this);
            tvStatus.setText("Status: " + app.status);
            tvStatus.setTextSize(13);
            tvStatus.setTypeface(null, Typeface.BOLD);
            tvStatus.setTextColor("APPROVED".equalsIgnoreCase(app.status)
                    ? Color.parseColor("#2E7D32")
                    : ("DECLINED".equalsIgnoreCase(app.status) ? Color.RED : Color.parseColor("#C85A32")));
            tvStatus.setPadding(0, 0, 0, 14);

            // Actions Layout
            LinearLayout actionsLayout = new LinearLayout(this);
            actionsLayout.setOrientation(LinearLayout.HORIZONTAL);

            Button btnApprove = new Button(this);
            btnApprove.setText(getString(R.string.btn_approve_lease));
            btnApprove.setAllCaps(false);
            btnApprove.setTextColor(Color.WHITE);
            btnApprove.setBackgroundResource(R.drawable.selector_regular_button);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, 88, 1);
            p1.setMarginEnd(6);
            btnApprove.setLayoutParams(p1);

            Button btnDecline = new Button(this);
            btnDecline.setText(getString(R.string.btn_decline_lease));
            btnDecline.setAllCaps(false);
            btnDecline.setTextColor(Color.DKGRAY);
            btnDecline.setBackgroundResource(R.drawable.selector_regular_button);
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, 88, 1);
            p2.setMarginStart(6);
            btnDecline.setLayoutParams(p2);

            tvStatus.setText("APPROVED".equalsIgnoreCase(app.status)
                    ? getString(R.string.status_approved)
                    : ("DECLINED".equalsIgnoreCase(app.status) ? getString(R.string.status_declined) : getString(R.string.status_pending)));

            if (!"PENDING".equalsIgnoreCase(app.status)) {
                btnApprove.setVisibility(View.GONE);
                btnDecline.setVisibility(View.GONE);
            }

            btnApprove.setOnClickListener(v -> {
                ApplicationManager.updateStatus(app.id, "APPROVED");
                Toast.makeText(this, getString(R.string.msg_application_approved), Toast.LENGTH_SHORT).show();
                renderApplications();
            });

            btnDecline.setOnClickListener(v -> {
                ApplicationManager.updateStatus(app.id, "DECLINED");
                Toast.makeText(this, getString(R.string.msg_application_declined), Toast.LENGTH_SHORT).show();
                renderApplications();
            });

            actionsLayout.addView(btnApprove);
            actionsLayout.addView(btnDecline);

            content.addView(tvTitle);
            content.addView(tvDetails);
            content.addView(tvStatus);
            content.addView(actionsLayout);

            card.addView(content);
            applicationsContainer.addView(card);
        }
    }
}