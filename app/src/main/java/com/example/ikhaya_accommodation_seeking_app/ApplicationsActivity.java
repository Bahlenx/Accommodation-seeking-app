package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApplicationsActivity extends AppCompatActivity {

    private static final String TAG = "RentalApplications";

    private LinearLayout applicationsContainer;
    private TextView txtNoApplications;
    private boolean isLandlordView;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_applications);

        isLandlordView = getIntent()
                .getBooleanExtra("isLandlord", false);

        applicationsContainer =
                findViewById(R.id.applicationsContainer);

        txtNoApplications =
                findViewById(R.id.txtNoApplications);

        Button back = findViewById(R.id.btnBack);

        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApplications();
    }

    private void loadApplications() {
        String uid = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (uid == null || uid.isEmpty()
                || token == null || token.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.please_log_in_again,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        applicationsContainer.removeAllViews();
        txtNoApplications.setVisibility(View.VISIBLE);
        txtNoApplications.setText(
                R.string.rental_applications_loading
        );

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String field = isLandlordView
                        ? "landlord_uid"
                        : "tenant_uid";

                String endpoint =
                        "/rest/v1/rental_applications?select=*"
                                + "&" + field + "=eq."
                                + SupabaseHelper.encode(uid)
                                + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint, "GET", token
                );

                int code = connection.getResponseCode();
                String response =
                        SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "HTTP " + code + ": " + response
                    );
                }

                JSONArray rows = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    txtNoApplications.setVisibility(
                            rows.length() == 0
                                    ? View.VISIBLE
                                    : View.GONE
                    );

                    txtNoApplications.setText(
                            R.string.no_applications_yet
                    );

                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);

                        if (row != null) {
                            renderCard(row);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Cannot load rental applications",
                        e
                );

                runOnUiThread(() -> {
                    txtNoApplications.setVisibility(
                            View.VISIBLE
                    );
                    txtNoApplications.setText(
                            R.string.rental_applications_load_failed
                    );
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void renderCard(JSONObject application) {
        String id = application.optString("id", "");
        String listingId =
                application.optString("listing_id", "");
        String status =
                application.optString("status", "PENDING");

        CardView card = new CardView(this);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 0, 0, 24);

        card.setLayoutParams(params);
        card.setRadius(20);
        card.setCardElevation(4);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 28);

        TextView title = new TextView(this);
        title.setText(
                getString(
                        R.string.rental_application_for_listing,
                        listingId
                )
        );
        title.setTextColor(Color.BLACK);
        title.setTextSize(16);
        title.setTypeface(null, 1);
        content.addView(title);

        TextView details = new TextView(this);
        details.setText(
                getString(
                        R.string.rental_application_card_details,
                        application.optString(
                                "applicant_name", ""
                        ),
                        application.optString(
                                "move_in_date", ""
                        ),
                        application.optString(
                                "lease_duration", ""
                        ),
                        application.optString(
                                "occupants", ""
                        ),
                        application.optString(
                                "phone", ""
                        )
                )
        );

        details.setTextColor(Color.DKGRAY);
        details.setPadding(0, 12, 0, 12);
        content.addView(details);

        TextView state = new TextView(this);
        state.setText(
                getString(
                        R.string.rental_application_status,
                        status
                )
        );
        state.setTypeface(null, 1);

        state.setTextColor(
                "APPROVED".equalsIgnoreCase(status)
                        ? Color.rgb(46, 125, 50)
                        : "DECLINED".equalsIgnoreCase(status)
                        ? Color.RED
                        : Color.rgb(200, 90, 50)
        );

        content.addView(state);

        if (isLandlordView
                && "PENDING".equalsIgnoreCase(status)) {

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button approve =
                    actionButton(R.string.btn_approve_lease);

            Button decline =
                    actionButton(R.string.btn_decline_lease);

            actions.addView(
                    approve,
                    new LinearLayout.LayoutParams(0, 110, 1)
            );

            actions.addView(
                    decline,
                    new LinearLayout.LayoutParams(0, 110, 1)
            );

            approve.setOnClickListener(v ->
                    updateStatus(
                            id, "APPROVED", approve, decline
                    )
            );

            decline.setOnClickListener(v ->
                    updateStatus(
                            id, "DECLINED", approve, decline
                    )
            );

            content.addView(actions);
        }

        if (!isLandlordView
                && "APPROVED".equalsIgnoreCase(status)) {

            Button view =
                    actionButton(R.string.view_rental_property);

            view.setOnClickListener(v -> {
                Intent intent = new Intent(
                        this,
                        PropertyDetailsActivity.class
                );

                intent.putExtra("LISTING_ID", listingId);
                intent.putExtra("isLandlord", false);
                startActivity(intent);
            });

            content.addView(view);
        }

        card.addView(content);
        applicationsContainer.addView(card);
    }

    private Button actionButton(int stringId) {
        Button button = new Button(this);

        button.setText(stringId);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(
                R.drawable.selector_regular_button
        );

        return button;
    }

    private void updateStatus(
            String id,
            String status,
            Button approve,
            Button decline
    ) {
        String uid = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (uid == null || uid.isEmpty()
                || token == null || token.isEmpty()) {
            return;
        }

        approve.setEnabled(false);
        decline.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/rental_applications?id=eq."
                                + SupabaseHelper.encode(id)
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(uid)
                                + "&status=eq.PENDING";

                connection = SupabaseHelper.createConnection(
                        endpoint, "PATCH", token
                );

                connection.setRequestProperty(
                        "Prefer", "return=representation"
                );

                JSONObject body = new JSONObject();
                body.put("status", status);

                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                String response =
                        SupabaseHelper.readResponse(connection);

                boolean ok = code >= 200
                        && code < 300
                        && new JSONArray(response).length() == 1;

                runOnUiThread(() -> {
                    if (ok) {
                        Toast.makeText(
                                this,
                                "APPROVED".equals(status)
                                        ? R.string.msg_application_approved
                                        : R.string.msg_application_declined,
                                Toast.LENGTH_SHORT
                        ).show();

                        loadApplications();

                    } else {
                        approve.setEnabled(true);
                        decline.setEnabled(true);

                        Toast.makeText(
                                this,
                                R.string.rental_application_update_failed,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Cannot update rental application",
                        e
                );

                runOnUiThread(() -> {
                    approve.setEnabled(true);
                    decline.setEnabled(true);

                    Toast.makeText(
                            this,
                            R.string.rental_application_update_failed,
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

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}