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

public class EnquiriesActivity extends AppCompatActivity {

    private LinearLayout enquiriesContainer;
    private TextView txtNoEnquiries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enquiries);

        Button btnBack = findViewById(R.id.btnBack);

        enquiriesContainer =
                findViewById(R.id.enquiriesContainer);

        txtNoEnquiries =
                findViewById(R.id.txtNoEnquiries);

        btnBack.setOnClickListener(v -> finish());

        loadEnquiries();
    }

    private void loadEnquiries() {

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || accessToken == null) {
            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/enquiries" +
                                "?select=*" +
                                "&landlord_uid=eq." +
                                SupabaseHelper.encode(userId) +
                                "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                String response =
                        SupabaseHelper.readResponse(connection);

                JSONArray enquiries =
                        new JSONArray(response);

                runOnUiThread(() -> {

                    enquiriesContainer.removeAllViews();

                    if (enquiries.length() == 0) {

                        txtNoEnquiries.setVisibility(View.VISIBLE);

                        return;
                    }

                    txtNoEnquiries.setVisibility(View.GONE);

                    for (int i = 0; i < enquiries.length(); i++) {

                        JSONObject enquiry =
                                enquiries.optJSONObject(i);

                        if (enquiry != null) {
                            addEnquiry(enquiry);
                        }
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                EnquiriesActivity.this,
                                "Could not load enquiries.",
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

    private void addEnquiry(JSONObject enquiry) {

        String message =
                enquiry.optString(
                        "message",
                        "No message"
                );

        String status =
                enquiry.optString(
                        "status",
                        "Pending"
                );

        TextView enquiryView =
                new TextView(this);

        enquiryView.setText(
                "Message: " + message +
                        "\n\nStatus: " + status
        );

        enquiryView.setTextSize(16);
        enquiryView.setPadding(
                16,
                16,
                16,
                16
        );

        enquiriesContainer.addView(enquiryView);
    }
}