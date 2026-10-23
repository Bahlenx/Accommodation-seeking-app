package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;

import android.view.View;
import android.view.inputmethod.EditorInfo;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchResultsActivity extends AppCompatActivity {

    private static final String TAG = "SearchResults";

    private EditText searchBox;
    private TextView txtSearchResultsTitle;
    private TextView txtNoResults;
    private LinearLayout searchResultsContainer;

    private Button chipAllTypes;
    private Button chipRooms;
    private Button chipShared;
    private Button chipHouses;

    // Optional inline filter bindings
    private EditText filterMaxPrice;
    private EditText filterMinRooms;
    private Spinner filterPropertyType;
    private View btnApplyFilters;

    private String currentSearchQuery = "";
    private String currentPropertyType = "";

    // Filter State Variables
    private double minPrice = 0;
    private double maxPrice = 5000000;
    private String currentOfferType = "";
    private String currentArrangement = "";
    private List<String> currentUtilities = null;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        // 1. Bind Layout Views
        View btnBack = findViewById(R.id.btnBack);
        View btnFilterOptions = findViewById(R.id.btnFilterOptions);
        View btnSearchAgain = findViewById(R.id.btnSearchAgain);

        txtSearchResultsTitle = findViewById(R.id.txtSearchResultsTitle);
        searchBox = findViewById(R.id.searchBox);
        txtNoResults = findViewById(R.id.txtNoResults);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);

        chipAllTypes = findViewById(R.id.chipAllTypes);
        chipRooms = findViewById(R.id.chipRooms);
        chipShared = findViewById(R.id.chipShared);
        chipHouses = findViewById(R.id.chipHouses);

        filterMaxPrice = findViewById(R.id.filterMaxPrice);
        filterMinRooms = findViewById(R.id.filterMinRooms);
        filterPropertyType = findViewById(R.id.filterPropertyType);
        btnApplyFilters = findViewById(R.id.btnApplyFilters);

        // 2. Read Incoming Intent Data
        currentSearchQuery = getIntent().getStringExtra("SEARCH_QUERY");
        currentPropertyType = getIntent().getStringExtra("PROPERTY_TYPE");
        if (currentSearchQuery == null) currentSearchQuery = "";
        if (currentPropertyType == null) currentPropertyType = "";

        if (searchBox != null && !currentSearchQuery.isEmpty()) {
            searchBox.setText(currentSearchQuery);
        }

        // 3. Setup Listeners
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnFilterOptions != null) {
            btnFilterOptions.setOnClickListener(v -> {
                FilterBottomSheetDialog filterSheet = new FilterBottomSheetDialog();
                filterSheet.setFilterListener((offerType, min, max, propType, arrangement, utilities) -> {
                    this.minPrice = min;
                    this.maxPrice = max;
                    this.currentOfferType = offerType;
                    this.currentPropertyType = propType;
                    this.currentArrangement = arrangement;
                    this.currentUtilities = utilities;

                    executeSearch();
                });
                filterSheet.show(getSupportFragmentManager(), "FilterBottomSheetDialog");
            });
        }

        if (btnApplyFilters != null) {
            btnApplyFilters.setOnClickListener(v -> {
                if (filterMaxPrice != null && !filterMaxPrice.getText().toString().trim().isEmpty()) {
                    try {
                        this.maxPrice = Double.parseDouble(filterMaxPrice.getText().toString().trim());
                    } catch (NumberFormatException ignored) {}
                }
                if (filterPropertyType != null && filterPropertyType.getSelectedItem() != null) {
                    this.currentPropertyType = filterPropertyType.getSelectedItem().toString().trim();
                }
                executeSearch();
            });
        }

        if (btnSearchAgain != null) {
            btnSearchAgain.setOnClickListener(v -> triggerNewSearch());
        }

        if (searchBox != null) {
            searchBox.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    triggerNewSearch();
                    return true;
                }
                return false;
            });
        }

        setupCategoryChips();
        executeSearch();
    }

    private void setupCategoryChips() {
        if (chipAllTypes != null) {
            chipAllTypes.setOnClickListener(v -> {
                currentPropertyType = "";
                updateChipSelection(chipAllTypes);
                executeSearch();
            });
        }

        if (chipRooms != null) {
            chipRooms.setOnClickListener(v -> {
                currentPropertyType = "Room";
                updateChipSelection(chipRooms);
                executeSearch();
            });
        }

        if (chipShared != null) {
            chipShared.setOnClickListener(v -> {
                currentPropertyType = "Shared";
                updateChipSelection(chipShared);
                executeSearch();
            });
        }

        if (chipHouses != null) {
            chipHouses.setOnClickListener(v -> {
                currentPropertyType = "House";
                updateChipSelection(chipHouses);
                executeSearch();
            });
        }
    }

    private void updateChipSelection(Button selectedChip) {
        Button[] allChips = {chipAllTypes, chipRooms, chipShared, chipHouses};
        for (Button chip : allChips) {
            if (chip == null) continue;
            if (chip == selectedChip) {
                chip.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BA5536")));
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                chip.setTextColor(Color.parseColor("#1F1614"));
            }
        }
    }

    private void triggerNewSearch() {
        if (searchBox != null) {
            currentSearchQuery = searchBox.getText().toString().trim();
        }
        executeSearch();
    }

    private void executeSearch() {
        if (searchResultsContainer != null) {
            searchResultsContainer.removeAllViews();
        }
        if (txtNoResults != null) {
            txtNoResults.setVisibility(View.GONE);
        }

        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                StringBuilder endpoint = new StringBuilder("/rest/v1/listings?select=*");
                endpoint.append("&status=eq.APPROVED");

                if (currentOfferType != null && !currentOfferType.isEmpty()) {
                    endpoint.append("&offer_type=ilike.").append(currentOfferType);
                }

                if (minPrice > 0) {
                    endpoint.append("&price=gte.").append((int) minPrice);
                }
                if (("sale".equals(currentOfferType) && maxPrice < 5000000) || (!"sale".equals(currentOfferType) && maxPrice < 20000)) {
                    endpoint.append("&price=lte.").append((int) maxPrice);
                }

                if (currentPropertyType != null && !currentPropertyType.isEmpty()) {
                    endpoint.append("&property_type=ilike.*").append(SupabaseHelper.encode(currentPropertyType)).append("*");
                }

                if (currentArrangement != null && !currentArrangement.isEmpty()) {
                    endpoint.append("&description=ilike.*").append(SupabaseHelper.encode(currentArrangement)).append("*");
                }

                if (currentUtilities != null && !currentUtilities.isEmpty()) {
                    for (String utility : currentUtilities) {
                        endpoint.append("&amenities=ilike.*").append(SupabaseHelper.encode(utility)).append("*");
                    }
                }

                if (!currentSearchQuery.isEmpty()) {
                    String enc = SupabaseHelper.encode(currentSearchQuery);
                    endpoint.append("&or=(property_name.ilike.*").append(enc)
                            .append("*,location.ilike.*").append(enc).append("*)");
                }

                endpoint.append("&order=created_at.desc&limit=50");

                conn = SupabaseHelper.createConnection(endpoint.toString(), "GET", token);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray results = new JSONArray(response);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (results.length() == 0) {
                            if (txtNoResults != null) {
                                txtNoResults.setText(R.string.search_no_results);
                                txtNoResults.setVisibility(View.VISIBLE);
                            }
                        } else {
                            if (txtNoResults != null) txtNoResults.setVisibility(View.GONE);
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject item = results.optJSONObject(i);
                                if (item != null) {
                                    addListingCard(item);
                                }
                            }
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (txtNoResults != null) {
                            txtNoResults.setText(R.string.error_unable_to_load_listings);
                            txtNoResults.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (txtNoResults != null) {
                        txtNoResults.setText(R.string.error_network_listings);
                        txtNoResults.setVisibility(View.VISIBLE);
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void addListingCard(JSONObject listing) {
        if (searchResultsContainer == null) return;

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16));
        card.setLayoutParams(cardLp);
        card.setRadius(dpToPx(16));
        card.setCardElevation(dpToPx(2));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        ImageView img = new ImageView(this);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(180)
        );
        img.setLayoutParams(imgLp);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EFEBE4"));
        img.setImageResource(android.R.drawable.ic_menu_gallery);
        root.addView(img);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(16));

        String listingId = listing.optString("id", "");
        String name = listing.optString("property_name", getString(R.string.accommodation));
        String location = listing.optString("location", getString(R.string.default_country_location));
        String price = listing.optString("price", "0");
        String imageUrl = listing.optString("image_url", "");

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(16f);
        title.setTextColor(Color.parseColor("#1F1614"));
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        details.addView(title);

        TextView loc = new TextView(this);
        loc.setText(getString(R.string.listing_location_pin, location));
        loc.setTextSize(13f);
        loc.setTextColor(Color.parseColor("#8C7A70"));
        loc.setSingleLine(true);
        LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        locLp.topMargin = dpToPx(2);
        loc.setLayoutParams(locLp);
        details.addView(loc);

        TextView priceTv = new TextView(this);
        priceTv.setText(getString(R.string.listing_price_month, price));
        priceTv.setTextSize(15f);
        priceTv.setTextColor(Color.parseColor("#BA5536"));
        priceTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        priceLp.topMargin = dpToPx(6);
        priceTv.setLayoutParams(priceLp);
        details.addView(priceTv);

        root.addView(details);
        card.addView(root);

        if (!imageUrl.isEmpty()) {
            loadListingImageAsync(imageUrl, img);
        }

        card.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                Toast.makeText(this, R.string.search_listing_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(SearchResultsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);
            startActivity(intent);
        });

        searchResultsContainer.addView(card);
    }

    private void loadListingImageAsync(String urlStr, ImageView imageView) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    try (InputStream in = conn.getInputStream()) {
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        if (bmp != null) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                imageView.setImageBitmap(bmp);
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            });
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}