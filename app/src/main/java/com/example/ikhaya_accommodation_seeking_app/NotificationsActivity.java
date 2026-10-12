package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.net.HttpURLConnection;

public class NotificationsActivity extends AppCompatActivity {

    private TextView txtNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        Button btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        loadNotifications();
    }

    private void loadNotifications() {

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || accessToken == null) {
            return;
        }

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/notifications" +
                                "?select=*" +
                                "&user_uid=eq." +
                                SupabaseHelper.encode(userId) +
                                "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                String response =
                        SupabaseHelper.readResponse(connection);

                JSONArray notifications =
                        new JSONArray(response);

                runOnUiThread(() -> {

                    if (notifications.length() > 0) {

                        TextView textView =
                                new TextView(
                                        NotificationsActivity.this
                                );

                        textView.setText(
                                notifications.length() +
                                        " notification(s) available."
                        );

                        textView.setTextSize(17);

                        ((android.widget.LinearLayout)
                                findViewById(
                                        android.R.id.content
                                )).addView(textView);
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                NotificationsActivity.this,
                                "Could not load notifications.",
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