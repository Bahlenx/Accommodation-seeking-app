package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyListingsActivity extends AppCompatActivity {

    private static final String TAG = "MyListingsActivity";

    private String activeFilter = "ALL";
    private LinearLayout listingsContainer;
    private TextView txtNoListings;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private final ActivityResultLauncher<Intent> editListingLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadMyListings()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_listings);

        listingsContainer = findViewById(R.id.myListingsContainer);
        txtNoListings = findViewById(R.id.txtNoListings);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String passedFilter = getIntent().getStringExtra("FILTER_STATUS");
        if (passedFilter != null) {
            activeFilter = passedFilter;
        }

        setupBottomNavigation();
        loadMyListings();
    }

    private void setupBottomNavigation() {
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);
        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);

            View navHome = findViewById(R.id.navHome);
            if (navHome != null) {
                navHome.setOnClickListener(v -> {
                    Intent intent = new Intent(MyListingsActivity.this, LandlordDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }

            View navListings = findViewById(R.id.navListings);
            if (navListings != null) {
                navListings.setOnClickListener(v -> {
                    View scroll = findViewById(R.id.myListingsScroll);
                    if (scroll != null) scroll.scrollTo(0, 0);
                });
            }

            View navAdd = findViewById(R.id.navAdd);
            if (navAdd != null) {
                navAdd.setOnClickListener(v -> startActivity(new Intent(MyListingsActivity.this, AddListingActivity.class)));
            }

            View navMessages = findViewById(R.id.navMessages);
            if (navMessages != null) {
                navMessages.setOnClickListener(v -> {
                    Intent intent = new Intent(MyListingsActivity.this, UserSelectionActivity.class);
                    intent.putExtra("isLandlord", true);
                    startActivity(intent);
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listingsContainer != null) {
            loadMyListings();
        }
    }

    private void loadMyListings() {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            showMessage(getString(R.string.please_log_in_again));
            return;
        }

        if (txtNoListings != null) {
            txtNoListings.setText(R.string.loading_listings);
            txtNoListings.setVisibility(View.VISIBLE);
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings"
                        + "?select=*"
                        + "&landlord_uid=eq." + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc";

                connection = SupabaseHelper.createConnection(endpoint, "GET", token);

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    showMessage(getString(R.string.error_could_not_load_listing) + " " + code);
                    return;
                }

                JSONArray listings = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    listingsContainer.removeAllViews();
                    int visibleCount = 0;

                    for (int i = 0; i < listings.length(); i++) {
                        JSONObject listing = listings.optJSONObject(i);
                        if (listing != null) {
                            String status = firstValue(listing, "status", "availability");

                            if (!activeFilter.equals("ALL") && !activeFilter.equalsIgnoreCase(status)) {
                                continue;
                            }

                            addListingCard(listing);
                            visibleCount++;
                        }
                    }

                    if (visibleCount == 0) {
                        txtNoListings.setText(activeFilter.equals("ALL") ?
                                getString(R.string.no_listings_yet) :
                                getString(R.string.no_listings_for_category));
                        txtNoListings.setVisibility(View.VISIBLE);
                    } else {
                        txtNoListings.setVisibility(View.GONE);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Could not load listings", e);
                showMessage(getString(R.string.error_could_not_load_listing_general));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void addListingCard(JSONObject listing) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_listing, listingsContainer, false);

        ImageView imgListing = card.findViewById(R.id.imgListing);
        TextView txtListingName = card.findViewById(R.id.txtListingName);
        TextView txtListingLocation = card.findViewById(R.id.txtListingLocation);
        TextView txtListingPrice = card.findViewById(R.id.txtListingPrice);
        TextView txtListingRooms = card.findViewById(R.id.txtListingRooms);
        Button btnEditListing = card.findViewById(R.id.btnEditListing);
        Button btnDeleteListing = card.findViewById(R.id.btnDeleteListing);

        String listingId = listing.optString("id", "");
        String propertyName = firstValue(listing, "property_name", "title", "name");
        String location = firstValue(listing, "location", "address", "area");
        String price = firstValue(listing, "price", "monthly_price");
        String rooms = firstValue(listing, "rooms", "bedrooms", "room_count");
        String imageUrl = firstValue(listing, "image_url", "image", "image_path", "photo_url", "property_image", "imageUrl", "cover_image", "photo");
        String status = firstValue(listing, "status", "availability");

        if (propertyName.isEmpty()) propertyName = getString(R.string.accommodation);

        if ("PENDING".equalsIgnoreCase(status)) {
            txtListingName.setText(String.format(Locale.getDefault(), "%s (%s)", propertyName, getString(R.string.status_in_review)));
            txtListingName.setTextColor(Color.parseColor("#DC3545"));
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            txtListingName.setText(String.format(Locale.getDefault(), "%s (%s)", propertyName, getString(R.string.status_requires_fixes)));
            txtListingName.setTextColor(Color.parseColor("#DC3545"));
        } else {
            txtListingName.setText(String.format(Locale.getDefault(), "%s (%s)", propertyName, getString(R.string.status_live)));
            txtListingName.setTextColor(Color.parseColor("#2DCE89"));
        }

        if (location.isEmpty()) location = getString(R.string.default_unknown_location);
        if (price.isEmpty()) price = "0";
        if (rooms.isEmpty()) rooms = getString(R.string.label_rooms_not_specified);

        txtListingLocation.setText(location);
        txtListingPrice.setText(getString(R.string.listing_price_month, price));
        txtListingRooms.setText(String.format(Locale.getDefault(), "%s %s", rooms, getString(R.string.label_rooms_count)));

        imgListing.setImageResource(android.R.drawable.ic_menu_gallery);
        imgListing.setScaleType(ImageView.ScaleType.CENTER_CROP);

        if (!imageUrl.isEmpty()) {
            loadListingImage(imageUrl, imgListing);
        }

        String finalPropertyName = propertyName;

        if (btnEditListing != null) {
            btnEditListing.setOnClickListener(v -> {
                if (listingId.isEmpty()) {
                    showMessage(getString(R.string.error_missing_credentials_or_listing));
                    return;
                }

                Intent intent = new Intent(MyListingsActivity.this, EditListingActivity.class);
                intent.putExtra("LISTING_ID", listingId);
                intent.putExtra("listingId", listingId);
                intent.putExtra("propertyName", finalPropertyName);
                editListingLauncher.launch(intent);
            });
        }

        if (btnDeleteListing != null) {
            btnDeleteListing.setOnClickListener(v -> {
                if (listingId.isEmpty()) {
                    showMessage(getString(R.string.error_missing_credentials_or_listing));
                    return;
                }

                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title_delete_listing)
                        .setMessage(String.format(Locale.getDefault(), "%s \"%s\"?", getString(R.string.dialog_msg_delete_listing_confirm), finalPropertyName))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteListing(listingId))
                        .show();
            });
        }

        card.setOnClickListener(v -> {
            if (listingId.isEmpty()) {
                showMessage(getString(R.string.error_missing_credentials_or_listing));
                return;
            }

            Intent intent = new Intent(MyListingsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("isLandlord", true);
            startActivity(intent);
        });

        listingsContainer.addView(card);
    }

    private String firstValue(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value != null && value != JSONObject.NULL) {
                String result = String.valueOf(value).trim();
                if (!result.isEmpty() && !result.equalsIgnoreCase("null")) {
                    return result;
                }
            }
        }
        return "";
    }

    private void loadListingImage(String imageUrl, ImageView imageView) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("http://")) {
                    return;
                }

                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    return;
                }

                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void deleteListing(String listingId) {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            showMessage(getString(R.string.please_log_in_again));
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings"
                        + "?id=eq." + SupabaseHelper.encode(listingId)
                        + "&landlord_uid=eq." + SupabaseHelper.encode(userId);

                connection = SupabaseHelper.createConnection(endpoint, "DELETE", token);
                int code = connection.getResponseCode();
                SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    showMessage(getString(R.string.msg_listing_deleted));
                    loadMyListings();
                } else {
                    showMessage(getString(R.string.error_could_not_delete_listing) + " " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not delete listing", e);
                showMessage(getString(R.string.error_delete_listing_general));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(MyListingsActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}