package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchResultsActivity extends AppCompatActivity {

    private static final String TAG = "SearchResults";

    private LinearLayout searchResultsContainer;
    private TextView txtNoResults;
    private EditText searchBox;
    private EditText filterMaxPrice;
    private EditText filterMinRooms;
    private Spinner filterPropertyType;

    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(3);

    private JSONArray allListings = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        searchResultsContainer =
                findViewById(R.id.searchResultsContainer);

        txtNoResults = findViewById(R.id.txtNoResults);
        searchBox = findViewById(R.id.searchBox);
        filterMaxPrice = findViewById(R.id.filterMaxPrice);
        filterMinRooms = findViewById(R.id.filterMinRooms);
        filterPropertyType = findViewById(R.id.filterPropertyType);

        Button btnSearchAgain =
                findViewById(R.id.btnSearchAgain);

        Button btnApplyFilters =
                findViewById(R.id.btnApplyFilters);

        Button btnBack = findViewById(R.id.btnBack);

        String query = getIntent().getStringExtra("SEARCH_QUERY");

        if (query == null) {
            query = "";
        }

        searchBox.setText(query.trim());

        btnSearchAgain.setOnClickListener(v ->
                loadListingsFromSupabase()
        );

        btnApplyFilters.setOnClickListener(v ->
                displayFilteredListings()
        );

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadListingsFromSupabase();
    }

    private void loadListingsFromSupabase() {

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (accessToken == null || accessToken.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    getString(R.string.search_login_again),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        txtNoResults.setVisibility(View.GONE);
        searchResultsContainer.removeAllViews();

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/listings"
                                + "?select=*"
                                + "&order=created_at.desc"
                                + "&limit=100";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {

                    Log.e(
                            TAG,
                            "Supabase returned HTTP "
                                    + responseCode
                                    + ": "
                                    + response
                    );

                    showMessage(
                            getString(R.string.search_load_failed)
                    );
                    return;
                }

                JSONArray results = new JSONArray(response);

                runOnUiThread(() -> {
                    allListings = results;
                    displayFilteredListings();
                });

            } catch (Exception e) {

                Log.e(TAG, "Error loading listings", e);

                showMessage(
                        getString(R.string.search_load_error)
                );

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

            String name = clean(
                    listing.optString("property_name", "")
            );

            String location = clean(
                    listing.optString("location", "")
            );

            String type = clean(
                    listing.optString("property_type", "")
            );

            double price = parseNumber(
                    listing.optString("price", ""),
                    Double.NaN
            );

            int rooms = (int) parseNumber(
                    listing.optString("rooms", ""),
                    -1
            );

            boolean matchesQuery =
                    query.isEmpty()
                            || name.toLowerCase(Locale.ROOT)
                            .contains(query)
                            || location.toLowerCase(Locale.ROOT)
                            .contains(query);

            if (!matchesQuery) {
                continue;
            }

            if (Double.isFinite(maxPrice)
                    && (!Double.isFinite(price) || price > maxPrice)) {
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

        if (matches.isEmpty()) {
            txtNoResults.setVisibility(View.VISIBLE);
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

        String listingId = clean(
                listing.optString("id", "")
        );

        String propertyName = clean(
                listing.optString("property_name", "")
        );

        String location = clean(
                listing.optString("location", "")
        );

        String price = clean(
                listing.optString("price", "")
        );

        String rooms = clean(
                listing.optString("rooms", "")
        );

        String propertyType = clean(
                listing.optString("property_type", "")
        );

        String imageUrl = clean(
                listing.optString("image_url", "")
        );

        CardView card = new CardView(this);

        card.setRadius(dp(14));
        card.setCardElevation(dp(3));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(
                getColor(R.color.white)
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);

        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        card.addView(content);

        // Accommodation image appears before the details button.
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
                getColor(R.color.appBackground)
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

        title.setTextSize(20);
        title.setTextColor(getColor(R.color.black));
        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dp(10);
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
        details.setTextColor(getColor(R.color.black));

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailsParams.topMargin = dp(6);
        detailsParams.bottomMargin = dp(8);
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

        viewButton.setTextColor(
                getColor(R.color.white)
        );

        viewButton.setOnClickListener(v -> {

            if (listingId.isEmpty()) {

                Toast.makeText(
                        SearchResultsActivity.this,
                        getString(
                                R.string.search_listing_unavailable
                        ),
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
        });

        content.addView(viewButton);
        searchResultsContainer.addView(card);
    }

    private void loadImageIntoView(
            String imageUrl,
            ImageView imageView
    ) {

        imageExecutor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                connection = (HttpURLConnection)
                        new URL(imageUrl).openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {

                    try (InputStream input =
                                 connection.getInputStream()) {

                        Bitmap bitmap =
                                BitmapFactory.decodeStream(input);

                        if (bitmap != null && !isFinishing()
                                && !isDestroyed()) {

                            runOnUiThread(() -> {
                                imageView.setImageBitmap(bitmap);
                                imageView.setScaleType(
                                        ImageView.ScaleType.CENTER_CROP
                                );
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
                            .replace(",", "")
                            .trim()
            );
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private void showMessage(String message) {

        runOnUiThread(() ->
                Toast.makeText(
                        SearchResultsActivity.this,
                        message,
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}