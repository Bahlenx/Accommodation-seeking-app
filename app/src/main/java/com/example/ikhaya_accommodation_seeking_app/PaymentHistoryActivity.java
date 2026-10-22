package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.net.HttpURLConnection;
import java.util.Locale;

public class PaymentHistoryActivity extends AppCompatActivity {

    private TextView txtNoPayments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_history);

        View btnBack = findViewById(R.id.btnBack);
        txtNoPayments = findViewById(R.id.txtNoPayments);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

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
                String endpoint = "/rest/v1/payments"
                        + "?select=*"
                        + "&tenant_uid=eq." + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                String response = SupabaseHelper.readResponse(connection);
                JSONArray payments = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || txtNoPayments == null) {
                        return;
                    }

                    if (payments.length() > 0) {
                        txtNoPayments.setText(String.format(
                                Locale.getDefault(),
                                "%d %s",
                                payments.length(),
                                getString(R.string.payment_records_found_suffix)
                        ));
                        txtNoPayments.setVisibility(View.VISIBLE);
                    } else {
                        txtNoPayments.setText(R.string.no_payment_records_found);
                        txtNoPayments.setVisibility(View.VISIBLE);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(
                            PaymentHistoryActivity.this,
                            R.string.error_could_not_load_payment_history,
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
}