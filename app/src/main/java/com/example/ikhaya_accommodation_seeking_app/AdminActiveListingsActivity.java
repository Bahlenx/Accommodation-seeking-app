package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminActiveListingsActivity extends AppCompatActivity implements AdminActiveListingAdapter.OnListingRevokedListener {

    private RecyclerView rvActiveListings;
    private AdminActiveListingAdapter listingAdapter;

    private final List<AdminListing> masterQueue = new ArrayList<>();
    private final List<AdminListing> displayedQueue = new ArrayList<>();

    private EditText etSearchActive;
    private TextView tvEmptyActive;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_active_listings);

        ImageButton btnBack = findViewById(R.id.btnBackToHub);
        btnBack.setOnClickListener(v -> finish());

        etSearchActive = findViewById(R.id.etSearchActive);
        tvEmptyActive = findViewById(R.id.tvEmptyActive);

        rvActiveListings = findViewById(R.id.rvActiveListings);
        rvActiveListings.setLayoutManager(new LinearLayoutManager(this));

        listingAdapter = new AdminActiveListingAdapter(this, displayedQueue, this);
        rvActiveListings.setAdapter(listingAdapter);

        setupSearch();
        loadActiveListings();
    }

    private void setupSearch() {
        etSearchActive.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applySearch(String query) {
        String lowerQuery = query.toLowerCase().trim();
        displayedQueue.clear();

        for (AdminListing listing : masterQueue) {
            if (listing.getTitle().toLowerCase().contains(lowerQuery) ||
                    listing.getLandlordName().toLowerCase().contains(lowerQuery) ||
                    listing.getDetails().toLowerCase().contains(lowerQuery)) {
                displayedQueue.add(listing);
            }
        }

        listingAdapter.notifyDataSetChanged();

        if (displayedQueue.isEmpty()) {
            rvActiveListings.setVisibility(View.GONE);
            tvEmptyActive.setVisibility(View.VISIBLE);
        } else {
            rvActiveListings.setVisibility(View.VISIBLE);
            tvEmptyActive.setVisibility(View.GONE);
        }
    }

    private void loadActiveListings() {
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = "/rest/v1/listings?select=id,property_name,price,location,landlord_uid,status,image_url"
                        + "&status=ilike.APPROVED&order=created_at.desc";

                conn = SupabaseHelper.createConnection(endpoint, "GET", token);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(response);
                    List<AdminListing> fetched = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id = obj.optString("id", "");
                        String name = obj.optString("property_name", "Untitled Listing");
                        String price = obj.optString("price", "0");
                        String loc = obj.optString("location", "No location");
                        String landlordUid = obj.optString("landlord_uid", "");
                        String status = obj.optString("status", "APPROVED");
                        String imageUrl = obj.optString("image_url", "");

                        String details = loc + " • R " + price + " / month";
                        String landlordLabel = landlordUid.length() > 8 ? "Landlord (" + landlordUid.substring(0, 8) + "...)" : "Landlord";

                        fetched.add(new AdminListing(id, name, details, landlordUid, landlordLabel, status, imageUrl));
                    }

                    runOnUiThread(() -> {
                        masterQueue.clear();
                        masterQueue.addAll(fetched);
                        applySearch(etSearchActive.getText().toString());
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_loading_queue_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    public void onListingRevoked() {
        loadActiveListings();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}