package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ikhaya_accommodation_seeking_app.LandlordProfileActivity;
import com.example.ikhaya_accommodation_seeking_app.MyListingsActivity;
import com.example.ikhaya_accommodation_seeking_app.SupabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LandlordDashboardActivity extends AppCompatActivity {

    private static final String TAG = "iKhayaDashboard";

    private TextView txtTotalListings;
    private TextView txtAvailableListings;
    private TextView txtPendingListings;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_dashboard);

        txtTotalListings = findViewById(R.id.txtTotalListings);
        txtAvailableListings = findViewById(R.id.txtAvailableListings);
        txtPendingListings = findViewById(R.id.txtPendingListings);

        Button btnAddListing = findViewById(R.id.btnAddListing);
        Button btnProfile = findViewById(R.id.btnProfile);

        Button navHome = findViewById(R.id.navHome);
        Button navListings = findViewById(R.id.navListings);
        Button navAdd = findViewById(R.id.navAdd);
        Button navProfile = findViewById(R.id.navProfile);

        if (btnAddListing != null) {
            btnAddListing.setOnClickListener(v ->
                    openActivity(AddListingActivity.class));
        }

        if (navAdd != null) {
            navAdd.setOnClickListener(v ->
                    openActivity(AddListingActivity.class));
        }

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    openActivity(LandlordProfileActivity.class));
        }

        if (navProfile != null) {
            navProfile.setOnClickListener(v ->
                    openActivity(LandlordProfileActivity.class));
        }

        if (navListings != null) {
            navListings.setOnClickListener(v ->
                    openActivity(MyListingsActivity.class));
        }

        if (navHome != null) {
            navHome.setOnClickListener(v ->
                    Toast.makeText(
                            this,
                            "You are already on the dashboard",
                            Toast.LENGTH_SHORT
                    ).show());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadListingStatistics();
    }

    private void openActivity(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void loadListingStatistics() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()) {
            Log.w(TAG, "No signed-in user session; cannot load listings.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String path =
                        "/rest/v1/listings"
                                + "?select=id,availability"
                                + "&landlord_uid=eq." + userId;

                connection = SupabaseHelper.createConnection(
                        path,
                        "GET",
                        accessToken
                );

                int responseCode = connection.getResponseCode();
                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    Log.e(TAG, "Could not load listings. HTTP "
                            + responseCode + ": " + response);
                    return;
                }

                JSONArray listings = new JSONArray(response);

                int total = listings.length();
                int available = 0;
                int pending = 0;

                for (int i = 0; i < listings.length(); i++) {
                    JSONObject listing = listings.getJSONObject(i);

                    String availability =
                            listing.optString("availability", "")
                                    .trim()
                                    .toLowerCase(Locale.ROOT);

                    if ("available".equals(availability)) {
                        available++;
                    } else if ("pending".equals(availability)) {
                        pending++;
                    }
                }

                final int totalCount = total;
                final int availableCount = available;
                final int pendingCount = pending;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (txtTotalListings != null) {
                        txtTotalListings.setText(
                                String.valueOf(totalCount));
                    }

                    if (txtAvailableListings != null) {
                        txtAvailableListings.setText(
                                String.valueOf(availableCount));
                    }

                    if (txtPendingListings != null) {
                        txtPendingListings.setText(
                                String.valueOf(pendingCount));
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh dashboard statistics", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}

