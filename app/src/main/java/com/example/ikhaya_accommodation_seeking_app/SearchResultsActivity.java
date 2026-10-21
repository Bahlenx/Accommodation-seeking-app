package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchResultsActivity extends AppCompatActivity {

    private static final String TAG = "SearchResults";

    private LinearLayout searchResultsContainer;
    private LinearLayout filtersPanel;

    private TextView txtNoResults;
    private TextView txtResultsCount;

    private EditText searchBox;
    private EditText filterMaxPrice;
    private EditText filterMinRooms;

    private Spinner filterPropertyType;
    private ProgressBar progressSearch;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    private JSONArray allListings = new JSONArray();

    private boolean loading = false;
    private int requestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        searchResultsContainer =
                findViewById(R.id.searchResultsContainer);

        filtersPanel =
                findViewById(R.id.filtersPanel);

        txtNoResults =
                findViewById(R.id.txtNoResults);

        txtResultsCount =
                findViewById(R.id.txtResultsCount);

        searchBox =
                findViewById(R.id.searchBox);

        filterMaxPrice =
                findViewById(R.id.filterMaxPrice);

        filterMinRooms =
                findViewById(R.id.filterMinRooms);

        filterPropertyType =
                findViewById(R.id.filterPropertyType);

        progressSearch =
                findViewById(R.id.progressSearch);

        Button btnBack =
                findViewById(R.id.btnBack);

        Button btnSearchAgain =
                findViewById(R.id.btnSearchAgain);

        Button btnApplyFilters =
                findViewById(R.id.btnApplyFilters);

        View btnFilterToggle =
                findViewById(R.id.btnFilterToggle);

        String query =
                getIntent().getStringExtra("SEARCH_QUERY");

        if (query == null || query.trim().isEmpty()) {
            query = getIntent().getStringExtra("QUERY");
        }

        if (query == null || query.trim().isEmpty()) {
            query = getIntent().getStringExtra("SEARCH_TEXT");
        }

        if (query == null) {
            query = "";
        }

        searchBox.setText(query.trim());
        searchBox.setSelection(searchBox.length());

        setupPropertyTypeSpinner(new JSONArray());

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnSearchAgain != null) {
            btnSearchAgain.setOnClickListener(v ->
                    loadListingsFromSupabase()
            );
        }

        if (btnApplyFilters != null) {
            btnApplyFilters.setOnClickListener(v ->
                    displayFilteredListings()
            );
        }

        if (btnFilterToggle != null) {
            btnFilterToggle.setOnClickListener(v -> {
                boolean show =
                        filtersPanel.getVisibility() != View.VISIBLE;

                filtersPanel.setVisibility(
                        show ? View.VISIBLE : View.GONE
                );
            });
        }

        searchBox.setOnEditorActionListener(
                (textView, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        loadListingsFromSupabase();
                        return true;
                    }

                    return false;
                }
        );

        loadListingsFromSupabase();
    }

    private void setupPropertyTypeSpinner(JSONArray listings) {
        String previousSelection = "";

        if (filterPropertyType.getSelectedItem() != null) {
            previousSelection =
                    filterPropertyType.getSelectedItem().toString();
        }

        String initialIntentType = getIntent().getStringExtra("PROPERTY_TYPE");
        if (previousSelection.isEmpty() && initialIntentType != null && !initialIntentType.trim().isEmpty()) {
            previousSelection = initialIntentType.trim();
        }

        List<String> types = new ArrayList<>();
        types.add(getString(R.string.search_any_type));

        for (int i = 0; i < listings.length(); i++) {
            JSONObject listing = listings.optJSONObject(i);

            if (listing == null) {
                continue;
            }

            String type = firstValue(
                    listing,
                    "property_type",
                    "type",
                    "accommodation_type"
            );

            if (type.isEmpty()) {
                continue;
            }

            boolean exists = false;

            for (String existing : types) {
                if (existing.equalsIgnoreCase(type)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                types.add(type);
            }
        }

        if (types.size() > 2) {
            List<String> sorted =
                    new ArrayList<>(types.subList(1, types.size()));

            Collections.sort(
                    sorted,
                    String.CASE_INSENSITIVE_ORDER
            );

            types.clear();
            types.add(getString(R.string.search_any_type));
            types.addAll(sorted);
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        types
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        filterPropertyType.setAdapter(adapter);

        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).equalsIgnoreCase(previousSelection)) {
                filterPropertyType.setSelection(i);
                break;
            }
        }
    }

    private void loadListingsFromSupabase() {
        if (loading || executor.isShutdown()) {
            return;
        }

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (accessToken == null || accessToken.trim().isEmpty()) {
            showMessage(getString(R.string.search_login_again));
            showStatus("Please sign in to search properties.");
            return;
        }

        loading = true;
        final int currentRequest = ++requestVersion;

        progressSearch.setVisibility(View.VISIBLE);
        txtNoResults.setVisibility(View.GONE);
        txtResultsCount.setText("Searching properties...");
        searchResultsContainer.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listings?select=*"
                                + "&order=created_at.desc"
                                + "&limit=1000";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    throw new java.io.IOException(
                            "HTTP " + responseCode + ": " + response
                    );
                }

                JSONArray results = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing()
                            || isDestroyed()
                            || currentRequest != requestVersion) {
                        return;
                    }

                    loading = false;
                    progressSearch.setVisibility(View.GONE);

                    allListings = results;

                    setupPropertyTypeSpinner(allListings);
                    displayFilteredListings();
                });

            } catch (Exception e) {
                Log.e(TAG, "Could not load listings", e);

                runOnUiThread(() -> {
                    if (isFinishing()
                            || isDestroyed()
                            || currentRequest != requestVersion) {
                        return;
                    }

                    loading = false;
                    progressSearch.setVisibility(View.GONE);

                    txtResultsCount.setText("Search unavailable");

                    showStatus(
                            "Could not load properties. "
                                    + "Please check your connection and try again."
                    );

                    showMessage(getString(R.string.search_load_failed));
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void displayFilteredListings() {
        if (loading) {
            return;
        }

        String query = searchBox.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        String maxPriceText = filterMaxPrice.getText()
                .toString()
                .trim();

        String minRoomsText = filterMinRooms.getText()
                .toString()
                .trim();

        String selectedType =
                filterPropertyType.getSelectedItem() == null
                        ? ""
                        : filterPropertyType.getSelectedItem()
                        .toString()
                        .trim();

        double maxPrice = parseNumber(
                maxPriceText,
                Double.POSITIVE_INFINITY
        );

        int minRooms = (int) parseNumber(
                minRoomsText,
                0
        );

        List<JSONObject> matches = new ArrayList<>();

        for (int i = 0; i < allListings.length(); i++) {
            JSONObject listing = allListings.optJSONObject(i);

            if (listing == null) {
                continue;
            }

            String name = firstValue(
                    listing,
                    "property_name",
                    "title",
                    "name"
            );

            String location = firstValue(
                    listing,
                    "location",
                    "address",
                    "area"
            );

            String type = firstValue(
                    listing,
                    "property_type",
                    "type",
                    "accommodation_type"
            );

            String description = firstValue(
                    listing,
                    "description"
            );

            String priceText = firstValue(
                    listing,
                    "price",
                    "monthly_price"
            );

            String roomsText = firstValue(
                    listing,
                    "rooms",
                    "bedrooms",
                    "room_count"
            );

            double price = parseNumber(
                    priceText,
                    Double.NaN
            );

            int rooms = (int) parseNumber(
                    roomsText,
                    -1
            );

            String searchable =
                    (name + " "
                            + location + " "
                            + type + " "
                            + description)
                            .toLowerCase(Locale.ROOT);

            String[] queryWords = query.split("\\s+");

            boolean matchesQuery = true;

            for (String word : queryWords) {
                if (!word.isEmpty() && !searchable.contains(word)) {
                    matchesQuery = false;
                    break;
                }
            }

            if (!matchesQuery) {
                continue;
            }

            if (!maxPriceText.isEmpty()
                    && (!Double.isFinite(price)
                    || price > maxPrice)) {
                continue;
            }

            if (minRooms > 0 && rooms < minRooms) {
                continue;
            }

            if (!isAnyType(selectedType)
                    && !type.equalsIgnoreCase(selectedType)) {
                continue;
            }

            matches.add(listing);
        }

        searchResultsContainer.removeAllViews();

        txtResultsCount.setText(
                matches.size() == 1
                        ? "1 property found"
                        : matches.size() + " properties found"
        );

        if (matches.isEmpty()) {
            showStatus(
                    allListings.length() == 0
                            ? "No properties are available right now."
                            : "No properties match your search. "
                            + "Try another location or adjust your filters."
            );
            return;
        }

        txtNoResults.setVisibility(View.GONE);

        for (JSONObject listing : matches) {
            addListingCard(listing);
        }
    }

    private boolean isAnyType(String type) {
        return type == null
                || type.isEmpty()
                || type.equalsIgnoreCase(
                getString(R.string.search_any_type)
        );
    }

    private void addListingCard(JSONObject listing) {
        String listingId = firstValue(
                listing,
                "id"
        );

        String propertyName = firstValue(
                listing,
                "property_name",
                "title",
                "name"
        );

        String location = firstValue(
                listing,
                "location",
                "address",
                "area"
        );

        String price = firstValue(
                listing,
                "price",
                "monthly_price"
        );

        String rooms = firstValue(
                listing,
                "rooms",
                "bedrooms",
                "room_count"
        );

        String propertyType = firstValue(
                listing,
                "property_type",
                "type",
                "accommodation_type"
        );

        String imageUrl = firstValue(
                listing,
                "image_url",
                "photo_url",
                "cover_image"
        );

        CardView card = new CardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        card.addView(content);

        ImageView image = new ImageView(this);

        image.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(190)
                )
        );

        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(
                getString(R.string.property_photo)
        );

        image.setBackgroundColor(
                Color.parseColor("#FAF7F5")
        );

        image.setImageResource(
                android.R.drawable.ic_menu_gallery
        );

        content.addView(image);

        if (!imageUrl.isEmpty()) {
            loadImageIntoView(imageUrl, image);
        }

        TextView title = new TextView(this);

        title.setText(
                propertyName.isEmpty()
                        ? getString(R.string.search_unnamed_property)
                        : propertyName
        );

        title.setTextSize(19);
        title.setTextColor(Color.parseColor("#1F1614"));
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dp(12);
        title.setLayoutParams(titleParams);
        content.addView(title);

        TextView details = new TextView(this);

        StringBuilder detailsText = new StringBuilder();

        if (!location.isEmpty()) {
            detailsText.append(location);
        }

        if (!price.isEmpty()) {
            if (detailsText.length() > 0) {
                detailsText.append("\n");
            }

            detailsText.append(
                    getString(
                            R.string.search_price_per_month,
                            price
                    )
            );
        }

        if (!rooms.isEmpty()) {
            if (detailsText.length() > 0) {
                detailsText.append("\n");
            }

            detailsText.append(
                    getString(
                            R.string.search_rooms_count,
                            rooms
                    )
            );
        }

        if (!propertyType.isEmpty()) {
            if (detailsText.length() > 0) {
                detailsText.append("\n");
            }

            detailsText.append(propertyType);
        }

        details.setText(detailsText.toString());
        details.setTextSize(15);
        details.setTextColor(Color.parseColor("#756763"));

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailsParams.topMargin = dp(8);
        detailsParams.bottomMargin = dp(12);

        details.setLayoutParams(detailsParams);
        content.addView(details);

        Button viewButton = new Button(this);

        viewButton.setText(
                getString(R.string.search_view_details)
        );

        viewButton.setAllCaps(false);
        viewButton.setBackgroundResource(
                R.drawable.selector_regular_button
        );

        viewButton.setTextColor(Color.WHITE);

        View.OnClickListener openDetailsListener = v -> {
            if (listingId.isEmpty()) {
                Toast.makeText(
                        this,
                        getString(R.string.search_listing_unavailable),
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            Intent intent = new Intent(
                    SearchResultsActivity.this,
                    PropertyDetailsActivity.class
            );

            intent.putExtra("LISTING_ID", listingId);

            startActivity(intent);
        };

        viewButton.setOnClickListener(openDetailsListener);
        card.setOnClickListener(openDetailsListener);

        content.addView(viewButton);
        searchResultsContainer.addView(card);
    }

    private void loadImageIntoView(
            String imageUrl,
            ImageView imageView
    ) {
        if (executor.isShutdown()) {
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        new URL(imageUrl).openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("GET");

                int responseCode =
                        connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {
                    try (InputStream input =
                                 connection.getInputStream()) {

                        Bitmap bitmap =
                                BitmapFactory.decodeStream(input);

                        if (bitmap != null) {
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    imageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Could not load image", e);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String firstValue(
            JSONObject object,
            String... keys
    ) {
        for (String key : keys) {
            String value = clean(
                    object.optString(key, "")
            );

            if (!value.isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private String clean(String value) {
        if (value == null
                || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }

        return value.trim();
    }

    private double parseNumber(
            String value,
            double fallback
    ) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Double.parseDouble(
                    value.replace("R", "")
                            .replace("r", "")
                            .replace(",", "")
                            .replaceAll("[^0-9.\\-]", "")
                            .trim()
            );

        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void showStatus(String message) {
        txtNoResults.setText(message);
        txtNoResults.setVisibility(View.VISIBLE);
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Override
    protected void onDestroy() {
        requestVersion++;
        executor.shutdownNow();
        super.onDestroy();
    }
}
