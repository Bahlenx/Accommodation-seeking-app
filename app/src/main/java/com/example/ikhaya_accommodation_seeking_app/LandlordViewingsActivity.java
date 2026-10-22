
package com.example.ikhaya_accommodation_seeking_app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LandlordViewingsActivity extends AppCompatActivity {

    private static final String TAG = "LandlordViewings";

    private LinearLayout requestsContainer;
    private TextView txtEmpty;
    private TextView txtCount;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadViewingRequests();
    }

    private void createScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FAF7F5"));

        root.setPadding(
                dp(20),
                dp(24),
                dp(20),
                dp(12)
        );

        setContentView(root);

        Button btnBack = new Button(this);
        btnBack.setText("← Back");
        btnBack.setTransformationMethod(null);
        btnBack.setTextColor(Color.WHITE);
        btnBack.setBackground(
                roundedBackground("#7A2525", 12)
        );

        root.addView(
                btnBack,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(44)
                )
        );

        btnBack.setOnClickListener(v -> finish());

        TextView heading = new TextView(this);
        heading.setText("Viewing Requests");
        heading.setTextSize(25);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(Color.parseColor("#1F1614"));

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        headingParams.topMargin = dp(22);
        root.addView(heading, headingParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Review viewing appointments requested by residents."
        );
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#756763"));

        root.addView(subtitle);

        txtCount = new TextView(this);
        txtCount.setText("Loading requests...");
        txtCount.setTextSize(14);
        txtCount.setTextColor(Color.parseColor("#BA5536"));

        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        countParams.topMargin = dp(18);
        countParams.bottomMargin = dp(12);

        root.addView(txtCount, countParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(scrollContent);

        txtEmpty = new TextView(this);
        txtEmpty.setText("No viewing requests yet.");
        txtEmpty.setTextSize(15);
        txtEmpty.setGravity(Gravity.CENTER);
        txtEmpty.setTextColor(Color.parseColor("#756763"));

        txtEmpty.setPadding(
                dp(16),
                dp(35),
                dp(16),
                dp(35)
        );

        txtEmpty.setBackground(
                roundedBackground("#FFFFFF", 16)
        );

        scrollContent.addView(txtEmpty);

        requestsContainer = new LinearLayout(this);
        requestsContainer.setOrientation(LinearLayout.VERTICAL);

        scrollContent.addView(requestsContainer);

        Button btnRefresh = new Button(this);
        btnRefresh.setText("Refresh Requests");
        btnRefresh.setTransformationMethod(null);
        btnRefresh.setTextColor(Color.WHITE);
        btnRefresh.setBackground(
                roundedBackground("#BA5536", 14)
        );

        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
                );

        refreshParams.topMargin = dp(12);
        root.addView(btnRefresh, refreshParams);

        btnRefresh.setOnClickListener(
                v -> loadViewingRequests()
        );
    }

    private void loadViewingRequests() {
        String landlordId =
                SupabaseHelper.getUserId(this);

        String token =
                SupabaseHelper.getAccessToken(this);

        if (landlordId == null || landlordId.isEmpty()
                || token == null || token.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        txtCount.setText("Loading requests...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/viewings?select=*"
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(landlordId)
                                + "&order=viewing_date.asc";

                connection =
                        SupabaseHelper.createConnection(
                                endpoint,
                                "GET",
                                token
                        );

                int code = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                Log.d(
                        TAG,
                        "Load requests HTTP "
                                + code
                                + ": "
                                + response
                );

                if (code < 200 || code >= 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        txtCount.setText(
                                "Unable to load requests."
                        );

                        Toast.makeText(
                                this,
                                "Loading failed (HTTP "
                                        + code
                                        + ").",
                                Toast.LENGTH_LONG
                        ).show();
                    });

                    return;
                }

                JSONArray requests =
                        new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    requestsContainer.removeAllViews();

                    int pendingCount = 0;

                    for (int i = 0; i < requests.length(); i++) {
                        JSONObject item =
                                requests.optJSONObject(i);

                        if (item == null) {
                            continue;
                        }

                        if ("Pending Host".equalsIgnoreCase(
                                item.optString("status", "")
                        )) {
                            pendingCount++;
                        }

                        addRequestCard(item);
                    }

                    txtEmpty.setVisibility(
                            requests.length() == 0
                                    ? View.VISIBLE
                                    : View.GONE
                    );

                    txtCount.setText(
                            pendingCount
                                    + " pending • "
                                    + requests.length()
                                    + " total requests"
                    );
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Could not load viewing requests",
                        e
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    txtCount.setText(
                            "Unable to load requests."
                    );

                    Toast.makeText(
                            this,
                            "Network error loading viewing requests.",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void addRequestCard(JSONObject item) {
        String viewingId = clean(
                item.optString("id", "")
        );

        String propertyName = clean(
                item.optString("property_name", "")
        );

        String location = clean(
                item.optString("location", "")
        );

        String viewingDate = clean(
                item.optString("viewing_date", "")
        );

        String status = clean(
                item.optString("status", "Pending Host")
        );

        if (propertyName.isEmpty()) {
            propertyName = "Accommodation";
        }

        if (location.isEmpty()) {
            location = "Location not provided";
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
                roundedBackground("#FFFFFF", 18)
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(14);
        requestsContainer.addView(card, cardParams);

        TextView tvProperty = createText(
                propertyName,
                18,
                "#1F1614",
                true
        );

        card.addView(tvProperty);

        TextView tvDate = createText(
                "🗓 " + viewingDate,
                14,
                "#BA5536",
                true
        );

        addTopMargin(card, tvDate, 10);

        TextView tvLocation = createText(
                "📍 " + location,
                13,
                "#756763",
                false
        );

        addTopMargin(card, tvLocation, 6);

        TextView tvStatus = createText(
                "Status: " + status,
                13,
                status.equalsIgnoreCase("Confirmed")
                        ? "#34734A"
                        : status.equalsIgnoreCase("Declined")
                        ? "#A12626"
                        : "#BA5536",
                true
        );

        addTopMargin(card, tvStatus, 10);

        if (!"Pending Host".equalsIgnoreCase(status)) {
            return;
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams actionsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        actionsParams.topMargin = dp(14);
        card.addView(actions, actionsParams);

        Button btnApprove = new Button(this);
        btnApprove.setText("Approve");
        btnApprove.setTransformationMethod(null);
        btnApprove.setTextColor(Color.WHITE);
        btnApprove.setBackground(
                roundedBackground("#34734A", 12)
        );

        LinearLayout.LayoutParams approveParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(46),
                        1f
                );

        approveParams.rightMargin = dp(8);
        actions.addView(btnApprove, approveParams);

        Button btnDecline = new Button(this);
        btnDecline.setText("Decline");
        btnDecline.setTransformationMethod(null);
        btnDecline.setTextColor(Color.WHITE);
        btnDecline.setBackground(
                roundedBackground("#7A2525", 12)
        );

        actions.addView(
                btnDecline,
                new LinearLayout.LayoutParams(
                        0,
                        dp(46),
                        1f
                )
        );

        btnApprove.setOnClickListener(v ->
                confirmDecision(
                        viewingId,
                        "Confirmed",
                        btnApprove,
                        btnDecline
                )
        );

        btnDecline.setOnClickListener(v ->
                confirmDecision(
                        viewingId,
                        "Declined",
                        btnApprove,
                        btnDecline
                )
        );
    }

    private void confirmDecision(
            String viewingId,
            String newStatus,
            Button btnApprove,
            Button btnDecline
    ) {
        if (viewingId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Viewing ID is missing.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        boolean approving =
                "Confirmed".equals(newStatus);

        new AlertDialog.Builder(this)
                .setTitle(
                        approving
                                ? "Approve Viewing"
                                : "Decline Viewing"
                )
                .setMessage(
                        approving
                                ? "Confirm this resident's viewing appointment?"
                                : "Decline this viewing request?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        approving ? "Approve" : "Decline",
                        (dialog, which) ->
                                updateViewingStatus(
                                        viewingId,
                                        newStatus,
                                        btnApprove,
                                        btnDecline
                                )
                )
                .show();
    }

    private void updateViewingStatus(
            String viewingId,
            String newStatus,
            Button btnApprove,
            Button btnDecline
    ) {
        String landlordId =
                SupabaseHelper.getUserId(this);

        String token =
                SupabaseHelper.getAccessToken(this);

        if (landlordId == null || landlordId.isEmpty()
                || token == null || token.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnApprove.setEnabled(false);
        btnDecline.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/viewings?id=eq."
                                + SupabaseHelper.encode(viewingId)
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(landlordId)
                                + "&status=eq."
                                + SupabaseHelper.encode("Pending Host");

                connection =
                        SupabaseHelper.createConnection(
                                endpoint,
                                "PATCH",
                                token
                        );

                connection.setRequestProperty(
                        "Prefer",
                        "return=representation"
                );

                JSONObject body = new JSONObject();
                body.put("status", newStatus);

                SupabaseHelper.writeJson(
                        connection,
                        body
                );

                int code =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                Log.d(
                        TAG,
                        "Update viewing HTTP "
                                + code
                                + ": "
                                + response
                );

                boolean updated = false;

                if (code >= 200 && code < 300) {
                    JSONArray rows =
                            new JSONArray(response);

                    updated = rows.length() > 0;
                }

                boolean success = updated;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (success) {
                        Toast.makeText(
                                this,
                                "Viewing "
                                        + newStatus.toLowerCase()
                                        + ".",
                                Toast.LENGTH_LONG
                        ).show();

                        loadViewingRequests();

                    } else {
                        btnApprove.setEnabled(true);
                        btnDecline.setEnabled(true);

                        Toast.makeText(
                                this,
                                "Could not update viewing (HTTP "
                                        + code
                                        + "). Check Logcat.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Could not update viewing",
                        e
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    btnApprove.setEnabled(true);
                    btnDecline.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Network error updating viewing.",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private TextView createText(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor(color));

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        return text;
    }

    private void addTopMargin(
            LinearLayout parent,
            View child,
            int marginDp
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private GradientDrawable roundedBackground(
            String color,
            int radiusDp
    ) {
        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.parseColor(color)
        );

        background.setCornerRadius(
                dp(radiusDp)
        );

        return background;
    }

    private String clean(String value) {
        if (value == null
                || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }

        return value.trim();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
