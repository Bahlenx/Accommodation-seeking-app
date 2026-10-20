
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

        enquiriesContainer = findViewById(R.id.enquiriesContainer);
        txtNoEnquiries = findViewById(R.id.txtNoEnquiries);

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

        String senderId = firstValue(
                enquiry,
                "tenant_uid",
                "sender_uid",
                "user_uid",
                "applicant_uid"
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
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        card.setBackground(
                roundedBackground(
                        Color.WHITE,
                        "#E5E5E5",
                        14
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);
        card.setElevation(dp(2));

        TextView sender = new TextView(this);
        sender.setText(senderName);
        sender.setTextSize(18);
        sender.setTypeface(null, Typeface.BOLD);
        sender.setTextColor(Color.rgb(34, 34, 34));

        TextView statusView = new TextView(this);
        statusView.setText("Status: " + status);
        statusView.setTextSize(13);
        statusView.setTextColor(Color.rgb(211, 47, 47));

        statusView.setPadding(
                0,
                dp(5),
                0,
                dp(10)
        );

        TextView details = new TextView(this);

        StringBuilder detailsText = new StringBuilder();

        if (!senderPhone.isEmpty()) {
            detailsText.append("Phone: ")
                    .append(senderPhone)
                    .append("\n");
        }

        if (!senderEmail.isEmpty()) {
            detailsText.append("Email: ")
                    .append(senderEmail)
                    .append("\n");
        }

        if (!senderId.isEmpty()) {
            detailsText.append("User ID: ")
                    .append(senderId)
                    .append("\n");
        }

        detailsText.append("\nMessage\n")
                .append(message);

        details.setText(detailsText.toString());
        details.setTextSize(14);
        details.setTextColor(Color.rgb(75, 75, 75));
        details.setLineSpacing(dp(3), 1f);

        card.addView(sender);
        card.addView(statusView);
        card.addView(details);

        if (status.equalsIgnoreCase("pending")) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            actions.setPadding(
                    0,
                    dp(14),
                    0,
                    0
            );

            Button approveButton = new Button(this);
            approveButton.setText("Approve contact");
            approveButton.setAllCaps(false);
            approveButton.setTextSize(12);

            approveButton.setBackgroundResource(
                    R.drawable.selector_regular_button
            );

            Button denyButton = new Button(this);
            denyButton.setText("Deny");
            denyButton.setAllCaps(false);
            denyButton.setTextSize(12);

            denyButton.setBackgroundResource(
                    R.drawable.selector_regular_button
            );

            LinearLayout.LayoutParams buttonParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                    );

            buttonParams.setMarginEnd(dp(6));
            approveButton.setLayoutParams(buttonParams);

            LinearLayout.LayoutParams denyParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
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
