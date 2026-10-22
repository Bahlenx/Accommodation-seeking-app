
package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class EnquiriesActivity extends AppCompatActivity {

    private LinearLayout enquiriesContainer;
    private TextView txtNoEnquiries;

    private String currentUserId;
    private String accessToken;

    private volatile boolean loadingEnquiries = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enquiries);

        View enquiriesRoot = findViewById(R.id.enquiriesRoot);
        if (enquiriesRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(enquiriesRoot, (v, insets) -> {
                Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                int density = (int) v.getResources().getDisplayMetrics().density;
                int sidePadding = 20 * density;
                v.setPadding(
                        sidePadding,
                        statusBar.top + (16 * density),
                        sidePadding,
                        navBar.bottom + (16 * density)
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        enquiriesContainer = findViewById(R.id.enquiriesContainer);
        txtNoEnquiries = findViewById(R.id.txtNoEnquiries);

        if (txtNoEnquiries != null) {
            GradientDrawable emptyBg = new GradientDrawable();
            emptyBg.setColor(Color.WHITE);
            emptyBg.setCornerRadius(dp(16));
            emptyBg.setStroke(dp(1), Color.parseColor("#E2DDD9"));
            txtNoEnquiries.setBackground(emptyBg);
        }

        View btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        currentUserId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        loadEnquiries();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void loadEnquiries() {
        if (loadingEnquiries) {
            return;
        }

        currentUserId = SupabaseHelper.getUserId(this);
        accessToken = SupabaseHelper.getAccessToken(this);

        if (currentUserId == null
                || currentUserId.trim().isEmpty()
                || accessToken == null
                || accessToken.trim().isEmpty()) {

            showMessage("Please log in again.");
            return;
        }

        loadingEnquiries = true;

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/enquiries"
                                + "?select=*"
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(currentUserId)
                                + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    showMessage("Could not load enquiries.");
                    return;
                }

                JSONArray enquiries = new JSONArray(response);

                LandlordNotificationState.markSeen(
                        getApplicationContext(),
                        "enquiries",
                        currentUserId,
                        enquiries
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    enquiriesContainer.removeAllViews();

                    if (enquiries.length() == 0) {
                        txtNoEnquiries.setVisibility(View.VISIBLE);
                        return;
                    }

                    txtNoEnquiries.setVisibility(View.GONE);

                    for (int i = 0; i < enquiries.length(); i++) {
                        JSONObject enquiry = enquiries.optJSONObject(i);

                        if (enquiry != null) {
                            addEnquiry(enquiry);
                        }
                    }
                });

            } catch (Exception e) {
                showMessage(
                        "Could not load enquiries. Please try again."
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                loadingEnquiries = false;
            }
        }).start();
    }

    private void addEnquiry(JSONObject enquiry) {
        String id = enquiry.optString("id", "");

        String message = firstValue(
                enquiry,
                "message",
                "enquiry_message",
                "content"
        );

        String status = enquiry.optString(
                "status",
                "Pending"
        );

        String senderName = firstValue(
                enquiry,
                "sender_name",
                "tenant_name",
                "applicant_name",
                "full_name",
                "name"
        );

        String senderPhone = firstValue(
                enquiry,
                "sender_phone",
                "tenant_phone",
                "phone",
                "phone_number"
        );

        String senderEmail = firstValue(
                enquiry,
                "sender_email",
                "tenant_email",
                "email"
        );

        if (senderName.isEmpty()) {
            senderName = "Tenant";
        }

        if (message.isEmpty()) {
            message = "No message provided.";
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        card.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#E2DDD9"));
        card.setBackground(cardBg);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        // Header: Sender Name & Status Badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView sender = new TextView(this);
        sender.setText(senderName);
        sender.setTextSize(17);
        sender.setTypeface(null, Typeface.BOLD);
        sender.setTextColor(Color.parseColor("#1F1614"));

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );
        header.addView(sender, nameParams);

        TextView statusView = new TextView(this);
        statusView.setText(status);
        statusView.setTextSize(12);
        statusView.setTypeface(null, Typeface.BOLD);
        statusView.setPadding(dp(10), dp(4), dp(10), dp(4));

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp(12));
        if (status.equalsIgnoreCase("Approved") || status.equalsIgnoreCase("Confirmed")) {
            statusView.setTextColor(Color.parseColor("#1B5E20"));
            statusBg.setColor(Color.parseColor("#E8F5E9"));
        } else if (status.equalsIgnoreCase("Declined") || status.equalsIgnoreCase("Denied")) {
            statusView.setTextColor(Color.parseColor("#C62828"));
            statusBg.setColor(Color.parseColor("#FFEBEE"));
        } else {
            statusView.setTextColor(Color.parseColor("#E65100"));
            statusBg.setColor(Color.parseColor("#FFF3E0"));
        }
        statusView.setBackground(statusBg);
        header.addView(statusView);

        card.addView(header);

        // Details block
        TextView details = new TextView(this);
        StringBuilder detailsText = new StringBuilder();

        if (!senderPhone.isEmpty()) {
            detailsText.append("📞 ").append(senderPhone).append("\n");
        }

        if (!senderEmail.isEmpty()) {
            detailsText.append("✉️ ").append(senderEmail).append("\n");
        }

        if (detailsText.length() > 0) {
            detailsText.append("\n");
        }

        detailsText.append("💬 Message:\n").append(message);

        details.setText(detailsText.toString());
        details.setTextSize(14);
        details.setTextColor(Color.parseColor("#5A524E"));
        details.setLineSpacing(dp(3), 1f);

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        detailsParams.topMargin = dp(10);
        card.addView(details, detailsParams);

        if (status.equalsIgnoreCase("pending")) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams actionsParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            actionsParams.topMargin = dp(14);
            actions.setLayoutParams(actionsParams);

            Button approveButton = new Button(this);
            approveButton.setText("Approve contact");
            approveButton.setTransformationMethod(null);
            approveButton.setTextSize(13);
            approveButton.setTypeface(null, Typeface.BOLD);
            approveButton.setTextColor(Color.WHITE);

            GradientDrawable approveBg = new GradientDrawable();
            approveBg.setColor(Color.parseColor("#34734A"));
            approveBg.setCornerRadius(dp(12));
            approveButton.setBackground(approveBg);

            Button denyButton = new Button(this);
            denyButton.setText("Deny");
            denyButton.setTransformationMethod(null);
            denyButton.setTextSize(13);
            denyButton.setTypeface(null, Typeface.BOLD);
            denyButton.setTextColor(Color.WHITE);

            GradientDrawable denyBg = new GradientDrawable();
            denyBg.setColor(Color.parseColor("#7A2525"));
            denyBg.setCornerRadius(dp(12));
            denyButton.setBackground(denyBg);

            LinearLayout.LayoutParams buttonParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(44),
                            1f
                    );
            buttonParams.setMarginEnd(dp(6));
            approveButton.setLayoutParams(buttonParams);

            LinearLayout.LayoutParams denyParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(44),
                            1f
                    );
            denyParams.setMarginStart(dp(6));
            denyButton.setLayoutParams(denyParams);

            approveButton.setOnClickListener(v ->
                    updateEnquiryStatus(
                            id,
                            "Approved",
                            approveButton,
                            denyButton
                    )
            );

            denyButton.setOnClickListener(v ->
                    updateEnquiryStatus(
                            id,
                            "Declined",
                            approveButton,
                            denyButton
                    )
            );

            actions.addView(approveButton);
            actions.addView(denyButton);
            card.addView(actions);
        }

        enquiriesContainer.addView(card);
    }

    private void updateEnquiryStatus(
            String id,
            String newStatus,
            Button approveButton,
            Button denyButton
    ) {
        if (id == null || id.isEmpty()) {
            Toast.makeText(
                    this,
                    "This enquiry has no ID; it cannot be updated.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null
                || userId.isEmpty()
                || token == null
                || token.isEmpty()) {

            showMessage("Please log in again.");
            return;
        }

        approveButton.setEnabled(false);
        denyButton.setEnabled(false);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/enquiries"
                                + "?id=eq."
                                + SupabaseHelper.encode(id)
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(userId);

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "PATCH",
                        token
                );

                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setRequestProperty(
                        "Prefer",
                        "return=minimal"
                );

                JSONObject body = new JSONObject();
                body.put("status", newStatus);

                try (OutputStream output =
                             connection.getOutputStream()) {

                    output.write(
                            body.toString().getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }

                int code = connection.getResponseCode();
                boolean success = code >= 200 && code < 300;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (success) {
                        Toast.makeText(
                                this,
                                newStatus.equals("Approved")
                                        ? "Contact request approved."
                                        : "Contact request declined.",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadEnquiries();

                    } else {
                        approveButton.setEnabled(true);
                        denyButton.setEnabled(true);

                        Toast.makeText(
                                this,
                                "Could not update this enquiry. Check your Supabase permissions and status values.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    approveButton.setEnabled(true);
                    denyButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Could not update enquiry.",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String firstValue(
            JSONObject object,
            String... keys
    ) {
        for (String key : keys) {
            String value = object.optString(key, "");

            if (!value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(value.trim())) {

                return value.trim();
            }
        }

        return "";
    }

    private GradientDrawable roundedBackground(
            int fillColor,
            String strokeColor,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));

        drawable.setStroke(
                dp(1),
                Color.parseColor(strokeColor)
        );

        return drawable;
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
