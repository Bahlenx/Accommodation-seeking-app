package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.net.HttpURLConnection;

public class PaymentHistoryActivity extends AppCompatActivity {

    private TextView txtNoPayments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_history);

        Button btnBack = findViewById(R.id.btnBack);
        txtNoPayments = findViewById(R.id.txtNoPayments);

        btnBack.setOnClickListener(v -> finish());

        loadPaymentHistory();
    }

    private void loadPaymentHistory() {

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || accessToken == null) {
            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/payments" +
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

                JSONArray payments =
                        new JSONArray(response);

                runOnUiThread(() -> {

                    if (payments.length() > 0) {

                        txtNoPayments.setText(
                                payments.length() +
                                        " payment record(s) found."
                        );

                    } else {

                        txtNoPayments.setVisibility(View.VISIBLE);
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                PaymentHistoryActivity.this,
                                "Could not load payment history.",
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