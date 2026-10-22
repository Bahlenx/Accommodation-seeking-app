package com.example.ikhaya_accommodation_seeking_app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportProblemActivity extends AppCompatActivity {

    private static final String TAG = "ReportProblem";

    private AutoCompleteTextView dropdownCategory;
    private EditText edtProblem;
    private Button btnSubmitProblem;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);

        View btnBack = findViewById(R.id.btnBack);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        edtProblem = findViewById(R.id.edtProblem);
        btnSubmitProblem = findViewById(R.id.btnSubmitProblem);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        setupCategoryDropdown();

        if (btnSubmitProblem != null) {
            btnSubmitProblem.setOnClickListener(v -> submitReport());
        }

        loadMyTickets();
    }

    private void setupCategoryDropdown() {
        if (dropdownCategory == null) return;

        String[] categories = {
                getString(R.string.report_category_fraud),
                getString(R.string.report_category_bug),
                getString(R.string.report_category_payment),
                getString(R.string.report_category_conduct),
                getString(R.string.report_category_account),
                getString(R.string.report_category_other)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                categories
        );
        dropdownCategory.setAdapter(adapter);
        dropdownCategory.setText(categories[0], false);
        dropdownCategory.setKeyListener(null);

        dropdownCategory.setOnClickListener(v -> dropdownCategory.showDropDown());
        dropdownCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownCategory.showDropDown();
        });
    }

    private void submitReport() {
        String category = dropdownCategory != null ? dropdownCategory.getText().toString().trim() : "";
        String description = edtProblem != null ? edtProblem.getText().toString().trim() : "";

        if (TextUtils.isEmpty(description)) {
            if (edtProblem != null) {
                edtProblem.setError(getString(R.string.error_describe_problem));
                edtProblem.requestFocus();
            }
            return;
        }

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()) {
            userId = prefs.getString("USER_ID", "guest_user");
        }
        if (accessToken == null || accessToken.isEmpty()) {
            accessToken = SupabaseHelper.getSupabaseKey();
        }

        String userName = prefs.getString("USER_NAME", getString(R.string.notification_user));
        String userRole = prefs.getString("USER_ROLE", "tenant");

        if (btnSubmitProblem != null) {
            btnSubmitProblem.setEnabled(false);
            btnSubmitProblem.setText(R.string.btn_submitting_ticket);
        }

        String finalUserId = userId;
        String finalAccessToken = accessToken;

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject report = new JSONObject();
                report.put("reporter_uid", finalUserId);
                report.put("reporter_name", userName);
                report.put("reporter_role", userRole);
                report.put("reason", category);
                report.put("description", description);
                report.put("status", "open");

                connection = SupabaseHelper.createConnection("/rest/v1/reports", "POST", finalAccessToken);
                connection.setRequestProperty("Prefer", "return=minimal");

                SupabaseHelper.writeJson(connection, report);

                int responseCode = connection.getResponseCode();
                String errorResponse = "";
                if (responseCode >= 400) {
                    errorResponse = SupabaseHelper.readResponse(connection);
                    Log.e(TAG, "HTTP " + responseCode + " - " + errorResponse);
                }

                final String finalErr = errorResponse;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (btnSubmitProblem != null) {
                        btnSubmitProblem.setEnabled(true);
                        btnSubmitProblem.setText(R.string.submit_report);
                    }

                    if (responseCode >= 200 && responseCode < 300) {
                        Toast.makeText(ReportProblemActivity.this, R.string.msg_ticket_submitted, Toast.LENGTH_LONG).show();
                        if (edtProblem != null) edtProblem.setText("");
                        loadMyTickets();
                    } else {
                        String userMsg = getString(R.string.error_could_not_submit_ticket) + " (HTTP " + responseCode + ").";
                        if (finalErr.contains("row-level security") || finalErr.contains("42501")) {
                            userMsg += "\n" + getString(R.string.error_rls_check_failed);
                        }
                        Toast.makeText(ReportProblemActivity.this, userMsg, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (btnSubmitProblem != null) {
                        btnSubmitProblem.setEnabled(true);
                        btnSubmitProblem.setText(R.string.submit_report);
                    }
                    Toast.makeText(ReportProblemActivity.this, R.string.error_network_submitting_ticket, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void loadMyTickets() {
        LinearLayout myTicketsContainer = findViewById(R.id.myTicketsContainer);
        if (myTicketsContainer == null) return;

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/reports?reporter_uid=eq." + SupabaseHelper.encode(userId) + "&order=created_at.desc",
                        "GET",
                        accessToken
                );
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(response);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        displayMyTickets(arr, myTicketsContainer);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching tickets: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void displayMyTickets(JSONArray arr, LinearLayout container) {
        container.removeAllViews();
        if (arr.length() == 0) {
            TextView emptyMsg = new TextView(this);
            emptyMsg.setText(R.string.no_tickets_submitted);
            emptyMsg.setTextColor(Color.parseColor("#8C7A70"));
            container.addView(emptyMsg);
            return;
        }

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                String reason = obj.optString("reason", getString(R.string.default_issue_reason));
                String desc = obj.optString("description", "");
                String status = obj.optString("status", "OPEN").toUpperCase(Locale.getDefault());
                String adminResponse = obj.optString("admin_response", "");

                CardView card = new CardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardParams.setMargins(0, 0, 0, dpToPx(12));
                card.setLayoutParams(cardParams);
                card.setRadius(dpToPx(12));
                card.setCardElevation(dpToPx(2));
                card.setCardBackgroundColor(Color.WHITE);

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

                RelativeLayout header = new RelativeLayout(this);
                TextView tvReason = new TextView(this);
                tvReason.setText(reason);
                tvReason.setTextColor(Color.parseColor("#BA5536"));
                tvReason.setTextSize(15f);
                tvReason.setTypeface(null, Typeface.BOLD);
                header.addView(tvReason);

                TextView tvStatus = new TextView(this);
                tvStatus.setText(status);
                tvStatus.setTextSize(11f);
                tvStatus.setTypeface(null, Typeface.BOLD);
                RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                statusParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                tvStatus.setLayoutParams(statusParams);

                if ("RESOLVED".equalsIgnoreCase(status)) {
                    tvStatus.setTextColor(Color.parseColor("#007749"));
                    tvStatus.setBackgroundColor(Color.parseColor("#E6F4EA"));
                } else if ("DISMISSED".equalsIgnoreCase(status)) {
                    tvStatus.setTextColor(Color.parseColor("#1F1614"));
                    tvStatus.setBackgroundColor(Color.parseColor("#EAEAEA"));
                } else {
                    tvStatus.setTextColor(Color.parseColor("#2DCE89"));
                }
                header.addView(tvStatus);
                layout.addView(header);

                TextView tvDesc = new TextView(this);
                tvDesc.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.prefix_you), desc));
                tvDesc.setTextColor(Color.parseColor("#495057"));
                tvDesc.setTextSize(13f);
                tvDesc.setPadding(0, dpToPx(4), 0, dpToPx(4));
                layout.addView(tvDesc);

                if (!adminResponse.isEmpty() && !"null".equalsIgnoreCase(adminResponse)) {
                    View divider = new View(this);
                    LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(1)
                    );
                    divParams.setMargins(0, dpToPx(6), 0, dpToPx(6));
                    divider.setLayoutParams(divParams);
                    divider.setBackgroundColor(Color.parseColor("#F0EBE6"));
                    layout.addView(divider);

                    TextView tvResponse = new TextView(this);
                    tvResponse.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.prefix_admin), adminResponse));
                    tvResponse.setTextColor(Color.parseColor("#1F1614"));
                    tvResponse.setTextSize(13f);
                    tvResponse.setTypeface(null, Typeface.ITALIC);
                    layout.addView(tvResponse);
                }

                card.addView(layout);
                container.addView(card);
            } catch (Exception e) {
                Log.e(TAG, "Error rendering ticket card", e);
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}