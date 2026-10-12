package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;

public class ApplicationsActivity extends AppCompatActivity {

    private LinearLayout applicationsContainer;
    private TextView txtNoApplications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applications);

        Button btnBack = findViewById(R.id.btnBack);

        applicationsContainer =
                findViewById(R.id.applicationsContainer);

        txtNoApplications =
                findViewById(R.id.txtNoApplications);

        btnBack.setOnClickListener(v -> finish());

        loadApplications();
    }

    private void loadApplications() {

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || accessToken == null) {
            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/applications" +
                                "?select=*" +
                                "&tenant_uid=eq." +
                                SupabaseHelper.encode(userId) +
                                "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                String response =
                        SupabaseHelper.readResponse(connection);

                JSONArray applications =
                        new JSONArray(response);

                runOnUiThread(() -> {

                    applicationsContainer.removeAllViews();

                    if (applications.length() == 0) {

                        txtNoApplications.setVisibility(View.VISIBLE);

                        return;
                    }

                    txtNoApplications.setVisibility(View.GONE);

                    for (int i = 0; i < applications.length(); i++) {

                        JSONObject application =
                                applications.optJSONObject(i);

                        if (application != null) {
                            addApplication(application);
                        }
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                ApplicationsActivity.this,
                                "Could not load applications.",
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

    private void addApplication(JSONObject application) {

        String message =
                application.optString(
                        "message",
                        "No message"
                );

        String status =
                application.optString(
                        "status",
                        "Pending"
                );

        TextView applicationView =
                new TextView(this);

        applicationView.setText(
                "Application\n\n" +
                        "Message: " + message +
                        "\n\nStatus: " + status
        );

        applicationView.setTextSize(16);
        applicationView.setPadding(
                16,
                16,
                16,
                16
        );

        applicationsContainer.addView(applicationView);
    }
}