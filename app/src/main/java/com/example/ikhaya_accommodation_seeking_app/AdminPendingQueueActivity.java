package com.example.ikhaya_accommodation_seeking_app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminPendingQueueActivity extends AppCompatActivity implements AdminListingAdapter.OnListingActionListener {

    private RecyclerView rvPendingQueue;
    private AdminListingAdapter listingAdapter;

    private final List<AdminListing> masterQueue = new ArrayList<>();
    private final List<AdminListing> displayedQueue = new ArrayList<>();

    private EditText etSearchListings;
    private Button btnSortNewest, btnSortOldest;
    private TextView tvEmptyQueue;

    private boolean isSortNewest = true;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_pending_queue);

        ImageButton btnBack = findViewById(R.id.btnBackToHub);
        btnBack.setOnClickListener(v -> finish());

        etSearchListings = findViewById(R.id.etSearchListings);
        btnSortNewest = findViewById(R.id.btnSortNewest);
        btnSortOldest = findViewById(R.id.btnSortOldest);
        tvEmptyQueue = findViewById(R.id.tvEmptyQueue);

        rvPendingQueue = findViewById(R.id.rvPendingQueue);
        rvPendingQueue.setLayoutManager(new LinearLayoutManager(this));

        listingAdapter = new AdminListingAdapter(this, displayedQueue, this);
        rvPendingQueue.setAdapter(listingAdapter);

        setupSorters();
        setupSearch();
        loadPendingQueue();
    }

    private void setupSorters() {
        btnSortNewest.setOnClickListener(v -> {
            isSortNewest = true;
            updateSortUI();
            applySearchAndSort(etSearchListings.getText().toString());
        });

        btnSortOldest.setOnClickListener(v -> {
            isSortNewest = false;
            updateSortUI();
            applySearchAndSort(etSearchListings.getText().toString());
        });
    }

    private void updateSortUI() {
        btnSortNewest.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(isSortNewest ? "#5E72E4" : "#E9ECEF")));
        btnSortNewest.setTextColor(Color.parseColor(isSortNewest ? "#FFFFFF" : "#32325D"));

        btnSortOldest.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(!isSortNewest ? "#5E72E4" : "#E9ECEF")));
        btnSortOldest.setTextColor(Color.parseColor(!isSortNewest ? "#FFFFFF" : "#32325D"));
    }

    private void setupSearch() {
        etSearchListings.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearchAndSort(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applySearchAndSort(String query) {
        String lowerQuery = query.toLowerCase().trim();
        displayedQueue.clear();

        // Filter
        for (AdminListing listing : masterQueue) {
            if (listing.getTitle().toLowerCase().contains(lowerQuery) ||
                    listing.getLandlordName().toLowerCase().contains(lowerQuery) ||
                    listing.getDetails().toLowerCase().contains(lowerQuery)) {
                displayedQueue.add(listing);
            }
        }

        // Sort (Assuming masterQueue is fetched descending by default)
        if (!isSortNewest) {
            Collections.reverse(displayedQueue);
        }

        listingAdapter.notifyDataSetChanged();

        if (displayedQueue.isEmpty()) {
            rvPendingQueue.setVisibility(View.GONE);
            tvEmptyQueue.setVisibility(View.VISIBLE);
        } else {
            rvPendingQueue.setVisibility(View.VISIBLE);
            tvEmptyQueue.setVisibility(View.GONE);
        }
    }

    private void loadPendingQueue() {
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Fetch ordered descending by default
                String endpoint = "/rest/v1/listings?select=id,property_name,price,location,landlord_uid,status,image_url"
                        + "&status=ilike.PENDING&order=created_at.desc";

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
                        String status = obj.optString("status", "PENDING");
                        String imageUrl = obj.optString("image_url", "");

                        String details = loc + " • R " + price + " / month";
                        String landlordLabel = landlordUid.length() > 8 ? "Landlord (" + landlordUid.substring(0, 8) + "...)" : "Landlord";

                        fetched.add(new AdminListing(id, name, details, landlordUid, landlordLabel, status, imageUrl));
                    }

                    runOnUiThread(() -> {
                        masterQueue.clear();
                        masterQueue.addAll(fetched);
                        applySearchAndSort(etSearchListings.getText().toString());
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
    public void onListingActionTaken() {
        // Automatically reload the queue when an item is approved/rejected
        loadPendingQueue();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}