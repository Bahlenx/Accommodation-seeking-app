package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchResultsActivity extends AppCompatActivity {

    private LinearLayout searchResultsContainer;
    private TextView txtNoResults;
    private android.widget.EditText searchBox;

    private String initialPropertyType;

    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        txtNoResults = findViewById(R.id.txtNoResults);
        searchBox = findViewById(R.id.searchBox);

        Button btnSearchAgain = findViewById(R.id.btnSearchAgain);

        String initialQuery = getIntent().getStringExtra("SEARCH_QUERY");
        if (initialQuery == null) {
            initialQuery = "";
        }
        initialQuery = initialQuery.trim();

        initialPropertyType = getIntent().getStringExtra("PROPERTY_TYPE");

        if (searchBox != null) {
            searchBox.setText(initialQuery);
        }

        if (btnSearchAgain != null) {
            btnSearchAgain.setOnClickListener(v -> {
                String query = (searchBox != null) ? searchBox.getText().toString().trim() : "";
                loadListings(query, initialPropertyType);
            });
        }

        loadListings(initialQuery, initialPropertyType);
    }

    private void loadListings(String searchQuery, String propertyType) {
        String accessToken = SupabaseHelper.getAccessToken(this);

        // Fallback: If not authenticated via Supabase (e.g. dev bypass / demo), load local listing store
        if (accessToken == null || accessToken.trim().isEmpty()) {
            loadLocalListings(searchQuery, propertyType);
            return;
        }

        final String query = searchQuery == null ? "" : searchQuery.trim();
        final String selectedType = propertyType == null ? "" : propertyType.trim();

        if (txtNoResults != null) txtNoResults.setVisibility(View.GONE);
        if (searchResultsContainer != null) searchResultsContainer.removeAllViews();

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                StringBuilder endpoint = new StringBuilder("/rest/v1/listings?select=*");

                if (!query.isEmpty()) {
                    String encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20");
                    String filter = "(location.ilike.*" + encodedQuery + "*,property_name.ilike.*" + encodedQuery + "*)";
                    endpoint.append("&or=").append(URLEncoder.encode(filter, "UTF-8").replace("+", "%20"));
                }

                if (!selectedType.isEmpty()) {
                    endpoint.append("&property_type=eq.").append(URLEncoder.encode(selectedType, "UTF-8").replace("+", "%20"));
                }

                endpoint.append("&order=created_at.desc&limit=20");

                connection = SupabaseHelper.createConnection(endpoint.toString(), "GET", accessToken);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    runOnUiThread(() -> loadLocalListings(searchQuery, propertyType));
                    return;
                }

                JSONArray listings = new JSONArray(response);
                runOnUiThread(() -> {
                    if (listings.length() == 0) {
                        loadLocalListings(searchQuery, propertyType);
                    } else {
                        displayListings(listings);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> loadLocalListings(searchQuery, propertyType));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void loadLocalListings(String searchQuery, String propertyType) {
        if (searchResultsContainer == null) return;
        searchResultsContainer.removeAllViews();

        List<ListingManager.Listing> localList = ListingManager.getListings(this);
        int matched = 0;

        for (ListingManager.Listing item : localList) {
            boolean matchesQuery = searchQuery == null || searchQuery.isEmpty()
                    || item.title.toLowerCase().contains(searchQuery.toLowerCase())
                    || item.location.toLowerCase().contains(searchQuery.toLowerCase());

            if (matchesQuery) {
                matched++;
                addLocalListingCard(item);
            }
        }

        if (matched == 0 && txtNoResults != null) {
            txtNoResults.setVisibility(View.VISIBLE);
        } else if (txtNoResults != null) {
            txtNoResults.setVisibility(View.GONE);
        }
    }

    private void addLocalListingCard(ListingManager.Listing item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF222222);

        TextView details = new TextView(this);
        details.setText(item.location + "\n" + item.price + "\nStatus: " + item.status);
        details.setTextSize(14);
        details.setPadding(0, 8, 0, 12);
        details.setTextColor(0xFF555555);

        Button viewButton = new Button(this);
        viewButton.setText("View Details");
        viewButton.setAllCaps(false);

        viewButton.setOnClickListener(v -> {
            Intent intent = new Intent(SearchResultsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("listingId", item.id);
            intent.putExtra("propertyName", item.title);
            intent.putExtra("listingPrice", item.price);
            intent.putExtra("listingLocation", item.location);
            startActivity(intent);
        });

        card.addView(title);
        card.addView(details);
        card.addView(viewButton);

        searchResultsContainer.addView(card);
    }

    private void displayListings(JSONArray listings) {
        searchResultsContainer.removeAllViews();

        if (listings.length() == 0) {
            if (txtNoResults != null) txtNoResults.setVisibility(View.VISIBLE);
            return;
        }

        if (txtNoResults != null) txtNoResults.setVisibility(View.GONE);

        for (int i = 0; i < listings.length(); i++) {
            try {
                addListingCard(listings.getJSONObject(i));
            } catch (Exception ignored) {}
        }
    }

    private void addListingCard(JSONObject listing) {
        String listingId = listing.optString("id", "");
        String propertyName = listing.optString("property_name", "Accommodation");
        String location = listing.optString("location", "Location unavailable");
        String price = listing.optString("price", "Price unavailable");
        String rooms = listing.optString("rooms", "Not specified");
        String propertyType = listing.optString("property_type", "Property type unavailable");
        String availability = listing.optString("availability", "Availability unknown");
        String imageUrl = listing.optString("image_url", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cardParams);

        ImageView photo = new ImageView(this);
        int photoHeight = (int) (190 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                photoHeight
        );
        photo.setLayoutParams(photoParams);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackgroundColor(0xFFEFEBE4);
        photo.setContentDescription("Property photo");
        card.addView(photo);

        if (!imageUrl.trim().isEmpty() && !imageUrl.equalsIgnoreCase("null")) {
            loadCardImage(imageUrl, photo);
        }

        TextView title = new TextView(this);
        title.setText(propertyName);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.START);
        title.setPadding(0, 12, 0, 0);

        TextView details = new TextView(this);
        details.setText(location + "\nR" + price + " per month\n" + rooms + " rooms\n" + propertyType + "\n" + availability);
        details.setTextSize(16);
        details.setPadding(0, 12, 0, 12);

        Button viewButton = new Button(this);
        viewButton.setText("View Details");
        viewButton.setAllCaps(false);

        viewButton.setOnClickListener(v -> {
            Intent intent = new Intent(SearchResultsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);
            intent.putExtra("propertyName", propertyName);
            intent.putExtra("listingPrice", "R" + price + " per month");
            intent.putExtra("listingLocation", location);
            startActivity(intent);
        });

        card.addView(title);
        card.addView(details);
        card.addView(viewButton);

        searchResultsContainer.addView(card);
    }

    private void loadCardImage(String imageUrl, ImageView imageView) {
        imageExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) return;

                Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                if (bitmap != null && !isFinishing()) {
                    runOnUiThread(() -> {
                        if (!isFinishing()) imageView.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}