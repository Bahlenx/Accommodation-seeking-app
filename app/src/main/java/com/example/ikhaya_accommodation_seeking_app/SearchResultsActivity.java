package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchResultsActivity extends AppCompatActivity {

    private static final String TAG = "SearchResults";

    private LinearLayout searchResultsContainer;
    private TextView txtNoResults;
    private EditText searchBox;

    // Active in-memory filters
    private String activePropertyType = "";
    private double activeMaxPrice = Double.POSITIVE_INFINITY;
    private boolean activeFilterWifi = false;
    private boolean activeFilterWater = false;
    private boolean activeFilterBackupPower = false;

    // Quick filter chip references for active state highlighting
    private Button chipAllTypes;
    private Button chipRooms;
    private Button chipShared;
    private Button chipHouses;

    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private JSONArray allListings = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        txtNoResults = findViewById(R.id.txtNoResults);
        searchBox = findViewById(R.id.searchBox);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnSearchAgain = findViewById(R.id.btnSearchAgain);
        ImageButton btnFilterOptions = findViewById(R.id.btnFilterOptions);

        chipAllTypes = findViewById(R.id.chipAllTypes);
        chipRooms = findViewById(R.id.chipRooms);
        chipShared = findViewById(R.id.chipShared);
        chipHouses = findViewById(R.id.chipHouses);

        // Pre-fill query or category intent extras
        String query = getIntent().getStringExtra("SEARCH_QUERY");
        if (query != null) {
            searchBox.setText(query.trim());
        }

        String initialType = getIntent().getStringExtra("PROPERTY_TYPE");
        if (initialType != null && !initialType.isEmpty()) {
            activePropertyType = initialType;
        }

        updateChipVisualStates();

        // 1. Navigation & Search Actions
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnSearchAgain != null) {
            btnSearchAgain.setOnClickListener(v -> displayFilteredListings());
        }

        if (searchBox != null) {
            searchBox.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    displayFilteredListings();
                    return true;
                }
                return false;
            });
        }

        // 2. Quick Category Chips
        if (chipAllTypes != null) {
            chipAllTypes.setOnClickListener(v -> {
                activePropertyType = "";
                updateChipVisualStates();
                displayFilteredListings();
            });
        }

        if (chipRooms != null) {
            chipRooms.setOnClickListener(v -> {
                activePropertyType = "Room";
                updateChipVisualStates();
                displayFilteredListings();
            });
        }

        if (chipShared != null) {
            chipShared.setOnClickListener(v -> {
                activePropertyType = "Shared";
                updateChipVisualStates();
                displayFilteredListings();
            });
        }

        if (chipHouses != null) {
            chipHouses.setOnClickListener(v -> {
                activePropertyType = "House";
                updateChipVisualStates();
                displayFilteredListings();
            });
        }

        // 3. Bottom Sheet Filter Dialog
        if (btnFilterOptions != null) {
            btnFilterOptions.setOnClickListener(v -> {
                FilterBottomSheetDialog dialog = new FilterBottomSheetDialog();
                dialog.setFilterListener((maxPrice, roomType, wifi, water, backupPower) -> {
                    activeMaxPrice = parseNumber(maxPrice, Double.POSITIVE_INFINITY);
                    if (roomType != null && !roomType.isEmpty()) {
                        activePropertyType = roomType;
                        updateChipVisualStates();
                    }
                    activeFilterWifi = wifi;
                    activeFilterWater = water;
                    activeFilterBackupPower = backupPower;

                    displayFilteredListings();
                });
                dialog.show(getSupportFragmentManager(), "FilterBottomSheet");
            });
        }

        loadListingsFromSupabase();
    }

    private void updateChipVisualStates() {
        setChipStyle(chipAllTypes, activePropertyType.isEmpty());
        setChipStyle(chipRooms, activePropertyType.equalsIgnoreCase("Room"));
        setChipStyle(chipShared, activePropertyType.equalsIgnoreCase("Shared"));
        setChipStyle(chipHouses, activePropertyType.equalsIgnoreCase("House"));
    }

    private void setChipStyle(Button chip, boolean isSelected) {
        if (chip == null) return;
        if (isSelected) {
            chip.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.terracotta_primary));
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
            chip.setTextColor(Color.parseColor("#1F1614"));
        }
    }

    private void loadListingsFromSupabase() {
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (accessToken == null || accessToken.trim().isEmpty()) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        txtNoResults.setVisibility(View.GONE);
        searchResultsContainer.removeAllViews();

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings?select=*&order=created_at.desc&limit=100";
                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    Log.e(TAG, "Supabase returned HTTP " + responseCode + ": " + response);
                    showMessage("Could not load listings.");
                    return;
                }

                JSONArray results = new JSONArray(response);

                runOnUiThread(() -> {
                    allListings = results;
                    displayFilteredListings();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading listings", e);
                showMessage("Error loading listings.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void displayFilteredListings() {
        if (searchResultsContainer == null) {
            return;
        }

        String query = searchBox.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        List<JSONObject> matches = new ArrayList<>();

        for (int i = 0; i < allListings.length(); i++) {
            JSONObject listing = allListings.optJSONObject(i);
            if (listing == null) continue;

            String name = clean(listing.optString("property_name", ""));
            String location = clean(listing.optString("location", ""));
            String type = clean(listing.optString("property_type", ""));
            double price = parseNumber(listing.optString("price", ""), Double.NaN);

            // Inclusions / Utilities
            boolean hasWifi = listing.optBoolean("wifi_included", false);
            boolean hasWater = listing.optBoolean("water_included", false);
            boolean hasBackupPower = listing.optBoolean("backup_power", false);

            boolean matchesQuery = query.isEmpty()
                    || name.toLowerCase(Locale.ROOT).contains(query)
                    || location.toLowerCase(Locale.ROOT).contains(query);

            if (!matchesQuery) continue;

            if (Double.isFinite(activeMaxPrice) && (!Double.isFinite(price) || price > activeMaxPrice)) {
                continue;
            }

            if (!activePropertyType.isEmpty() && !type.equalsIgnoreCase(activePropertyType)) {
                continue;
            }

            if (activeFilterWifi && !hasWifi) continue;
            if (activeFilterWater && !hasWater) continue;
            if (activeFilterBackupPower && !hasBackupPower) continue;

            matches.add(listing);
        }

        searchResultsContainer.removeAllViews();

        if (matches.isEmpty()) {
            txtNoResults.setVisibility(View.VISIBLE);
            return;
        }

        txtNoResults.setVisibility(View.GONE);

        for (JSONObject listing : matches) {
            addListingCard(listing);
        }
    }

    private void addListingCard(JSONObject listing) {
        String listingId = clean(listing.optString("id", ""));
        String propertyName = clean(listing.optString("property_name", ""));
        String location = clean(listing.optString("location", ""));
        String price = clean(listing.optString("price", ""));
        String rooms = clean(listing.optString("rooms", ""));
        String propertyType = clean(listing.optString("property_type", ""));
        String imageUrl = clean(listing.optString("image_url", ""));

        CardView card = new CardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(14));
        card.addView(content);

        // Property Preview Image
        ImageView image = new ImageView(this);
        image.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(180)
        ));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.parseColor("#FAF7F5"));
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        content.addView(image);

        if (!imageUrl.isEmpty()) {
            loadImageIntoView(imageUrl, image);
        }

        // Title
        TextView title = new TextView(this);
        title.setText(propertyName.isEmpty() ? "Accommodation" : propertyName);
        title.setTextSize(18);
        title.setTextColor(Color.parseColor("#1F1614"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(10);
        title.setLayoutParams(titleParams);
        content.addView(title);

        // Location Row
        TextView tvLocation = new TextView(this);
        tvLocation.setText("📍 " + (location.isEmpty() ? "Location available on request" : location));
        tvLocation.setTextSize(13);
        tvLocation.setTextColor(Color.parseColor("#8C7A70"));
        LinearLayout.LayoutParams locParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        locParams.topMargin = dp(4);
        tvLocation.setLayoutParams(locParams);
        content.addView(tvLocation);

        // Price & Rooms Row
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dp(10);
        metaRow.setLayoutParams(metaParams);

        TextView tvPrice = new TextView(this);
        tvPrice.setText("R " + price + " / month");
        tvPrice.setTextSize(16);
        tvPrice.setTextColor(Color.parseColor("#BA5536"));
        tvPrice.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPrice.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        metaRow.addView(tvPrice);

        if (!rooms.isEmpty()) {
            TextView tvRooms = new TextView(this);
            tvRooms.setText(rooms + " rooms");
            tvRooms.setTextSize(12);
            tvRooms.setTextColor(Color.parseColor("#1F1614"));
            tvRooms.setBackgroundColor(Color.parseColor("#F5EFEB"));
            tvRooms.setPadding(dp(8), dp(4), dp(8), dp(4));
            metaRow.addView(tvRooms);
        }

        content.addView(metaRow);

        card.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                Toast.makeText(SearchResultsActivity.this, "Listing details currently unavailable.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(SearchResultsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            startActivity(intent);
        });

        searchResultsContainer.addView(card);
    }

    private void loadImageIntoView(String imageUrl, ImageView imageView) {
        imageExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    try (InputStream input = connection.getInputStream()) {
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        if (bitmap != null && !isFinishing() && !isDestroyed()) {
                            runOnUiThread(() -> {
                                imageView.setImageBitmap(bitmap);
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not load listing image", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String clean(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private double parseNumber(String value, double fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(
                    value.replace("R", "")
                            .replace(",", "")
                            .trim()
            );
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(SearchResultsActivity.this, message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}