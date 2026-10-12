package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.HttpURLConnection;

public class ReportProblemActivity extends AppCompatActivity {

    private EditText edtProblem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnSubmitProblem =
                findViewById(R.id.btnSubmitProblem);

        edtProblem = findViewById(R.id.edtProblem);

        btnBack.setOnClickListener(v -> finish());

        btnSubmitProblem.setOnClickListener(
                v -> submitReport()
        );
    }

    private void submitReport() {

        String description =
                edtProblem.getText()
                        .toString()
                        .trim();

        if (TextUtils.isEmpty(description)) {

            Toast.makeText(
                    this,
                    "Please describe the problem.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String userId =
                SupabaseHelper.getUserId(this);

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (userId == null || accessToken == null) {

            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                JSONObject report =
                        new JSONObject();

                report.put(
                        "reporter_uid",
                        userId
                );

                report.put(
                        "reason",
                        "Problem reported by user"
                );

                report.put(
                        "description",
                        description
                );

                connection =
                        SupabaseHelper.createConnection(
                                "/rest/v1/reports",
                                "POST",
                                accessToken
                        );

                connection.setRequestProperty(
                        "Prefer",
                        "return=minimal"
                );

                SupabaseHelper.writeJson(
                        connection,
                        report
                );

                int responseCode =
                        connection.getResponseCode();

                if (responseCode >= 200 &&
                        responseCode < 300) {

                    runOnUiThread(() -> {

                        Toast.makeText(
                                ReportProblemActivity.this,
                                "Report submitted.",
                                Toast.LENGTH_SHORT
                        ).show();

                        finish();
                    });

                } else {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    ReportProblemActivity.this,
                                    "Could not submit report.",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                ReportProblemActivity.this,
                                "Could not submit report.",
                                Toast.LENGTH_SHORT
                        ).show()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }
}