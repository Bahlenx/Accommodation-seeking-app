
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewingsActivity extends AppCompatActivity {

    private static final String TAG = "ViewingsActivity";

    private LinearLayout viewingsContainer;
    private View layoutEmptyViewings;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewings);

        viewingsContainer = findViewById(R.id.viewingsContainer);
        layoutEmptyViewings = findViewById(R.id.layoutEmptyViewings);

        View btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btnEmpty = findViewById(R.id.btnEmptyFindPlace);

        if (btnEmpty != null) {
            btnEmpty.setOnClickListener(v -> {
                Intent intent = new Intent(
                        ViewingsActivity.this,
                        HomeActivity.class
                );

                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                startActivity(intent);
                finish();
            });
        }

        setupBottomNavigation();

        if (viewingsContainer == null || layoutEmptyViewings == null) {
            Toast.makeText(
                    this,
                    "Viewings screen could not be initialized.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (viewingsContainer != null
                && layoutEmptyViewings != null) {
            loadScheduledViewings();
        }
    }

    private void setupBottomNavigation() {
        View viewingsRoot = findViewById(R.id.viewingsRoot);
        View floatingBottomNav =
                findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null && viewingsRoot != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);

            ViewCompat.setOnApplyWindowInsetsListener(
                    viewingsRoot,
                    (v, insets) -> {
                        Insets statusBar = insets.getInsets(
                                WindowInsetsCompat.Type.statusBars()
                        );

                        Insets navBar = insets.getInsets(
                                WindowInsetsCompat.Type.navigationBars()
                        );

                        v.setPadding(0, statusBar.top, 0, 0);

                        if (floatingBottomNav.getLayoutParams()
                                instanceof CoordinatorLayout.LayoutParams) {

                            CoordinatorLayout.LayoutParams lp =
                                    (CoordinatorLayout.LayoutParams)
                                            floatingBottomNav.getLayoutParams();

                            int baseMarginPx = dpToPx(16);

                            lp.bottomMargin =
                                    navBar.bottom + baseMarginPx;

                            floatingBottomNav.setLayoutParams(lp);
                            floatingBottomNav.setPadding(0, 0, 0, 0);
                        }

                        return WindowInsetsCompat.CONSUMED;
                    }
            );
        }

        View navHome = findViewById(R.id.navHome);

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(
                        ViewingsActivity.this,
                        HomeActivity.class
                );

                intent.setFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                startActivity(intent);
                finish();
            });
        }

        View navFavorites =
                findViewById(R.id.navFavorites);

        if (navFavorites != null) {
            navFavorites.setOnClickListener(v -> {
                startActivity(
                        new Intent(
                                ViewingsActivity.this,
                                SavedListingsActivity.class
                        )
                );

                finish();
            });
        }

        View navMessages =
                findViewById(R.id.navMessages);

        if (navMessages != null) {
            navMessages.setOnClickListener(v -> {
                startActivity(
                        new Intent(
                                ViewingsActivity.this,
                                UserSelectionActivity.class
                        )
                );

                finish();
            });
        }
    }

    private void loadScheduledViewings() {
        if (loading || executor.isShutdown()) {
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.trim().isEmpty()
                || accessToken == null
                || accessToken.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();

            showEmptyState();
            return;
        }

        loading = true;

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/viewings?select=*"
                                + "&tenant_uid=eq."
                                + SupabaseHelper.encode(userId)
                                + "&order=viewing_date.asc";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                Log.d(
                        TAG,
                        "Load viewings HTTP "
                                + responseCode
                                + ": "
                                + response
                );

                if (responseCode < 200 || responseCode >= 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        Toast.makeText(
                                this,
                                "Could not load viewings (HTTP "
                                        + responseCode + ").",
                                Toast.LENGTH_LONG
                        ).show();

                        showEmptyState();
                    });

                    return;
                }

                JSONArray viewings =
                        new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    viewingsContainer.removeAllViews();

                    if (viewings.length() == 0) {
                        showEmptyState();
                        return;
                    }

                    layoutEmptyViewings.setVisibility(View.GONE);

                    for (int i = 0; i < viewings.length(); i++) {
                        JSONObject viewing =
                                viewings.optJSONObject(i);

                        if (viewing != null) {
                            addViewingCard(viewing);
                        }
                    }

                    if (viewingsContainer.getChildCount() == 0) {
                        showEmptyState();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to load viewings", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            this,
                            "Network error loading your viewings.",
                            Toast.LENGTH_LONG
                    ).show();

                    showEmptyState();
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                loading = false;
            }
        });
    }

    private void showEmptyState() {
        if (viewingsContainer != null) {
            viewingsContainer.removeAllViews();
        }

        if (layoutEmptyViewings != null) {
            layoutEmptyViewings.setVisibility(View.VISIBLE);
        }
    }

    private void addViewingCard(JSONObject item) {
        String listingId =
                clean(item.optString("listing_id", ""));

        String title = clean(
                item.optString("property_name", "")
        );

        String location = clean(
                item.optString("location", "")
        );

        String date = clean(
                item.optString("viewing_date", "")
        );

        String status = clean(
                item.optString("status", "")
        );

        String landlordPhone = clean(
                item.optString("landlord_phone", "")
        );

        if (title.isEmpty()) {
            title = "Accommodation Viewing";
        }

        if (location.isEmpty()) {
            location = "Location not provided";
        }

        if (date.isEmpty()) {
            date = "Date not provided";
        }

        if (status.isEmpty()) {
            status = "Pending Host";
        }

        CardView card = new CardView(this);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dpToPx(16)
        );

        card.setLayoutParams(cardParams);
        card.setCardElevation(dpToPx(2));
        card.setRadius(dpToPx(18));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        int padding = dpToPx(16);
        content.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(
                Color.parseColor("#1F1614")
        );

        tvTitle.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        tvTitle.setLayoutParams(titleParams);
        header.addView(tvTitle);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(status);
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(null, Typeface.BOLD);

        tvStatus.setPadding(
                dpToPx(10),
                dpToPx(4),
                dpToPx(10),
                dpToPx(4)
        );

        if ("Confirmed".equalsIgnoreCase(status)
                || "Approved".equalsIgnoreCase(status)) {

            tvStatus.setTextColor(
                    Color.parseColor("#007749")
            );

            tvStatus.setBackgroundColor(
                    Color.parseColor("#E6F4EA")
            );

        } else if ("Declined".equalsIgnoreCase(status)
                || "Cancelled".equalsIgnoreCase(status)
                || "Rejected".equalsIgnoreCase(status)) {

            tvStatus.setTextColor(
                    Color.parseColor("#A12626")
            );

            tvStatus.setBackgroundColor(
                    Color.parseColor("#FCE8E6")
            );

        } else {
            tvStatus.setTextColor(
                    Color.parseColor("#BA5536")
            );

            tvStatus.setBackgroundColor(
                    Color.parseColor("#FDEEE9")
            );
        }

        header.addView(tvStatus);
        content.addView(header);

        TextView tvDate = new TextView(this);
        tvDate.setText("🗓 " + date);
        tvDate.setTextSize(13);
        tvDate.setTextColor(
                Color.parseColor("#BA5536")
        );

        tvDate.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        dateParams.setMargins(
                0,
                dpToPx(8),
                0,
                0
        );

        tvDate.setLayoutParams(dateParams);
        content.addView(tvDate);

        TextView tvLocation = new TextView(this);
        tvLocation.setText("📍 " + location);
        tvLocation.setTextSize(13);
        tvLocation.setTextColor(
                Color.parseColor("#8C7A70")
        );

        LinearLayout.LayoutParams locationParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        locationParams.setMargins(
                0,
                dpToPx(5),
                0,
                dpToPx(14)
        );

        tvLocation.setLayoutParams(locationParams);
        content.addView(tvLocation);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button btnDirections = new Button(this);
        btnDirections.setText("Directions");
        btnDirections.setTextSize(12);

        btnDirections.setTextColor(
                Color.parseColor("#1F1614")
        );

        btnDirections.setBackgroundColor(
                Color.parseColor("#FAF7F5")
        );

        LinearLayout.LayoutParams directionsParams =
                new LinearLayout.LayoutParams(
                        0,
                        dpToPx(44),
                        1f
                );

        directionsParams.setMarginEnd(dpToPx(8));
        btnDirections.setLayoutParams(directionsParams);

        String finalLocation = location;

        btnDirections.setOnClickListener(v -> {
            if (finalLocation.equals("Location not provided")) {
                Toast.makeText(
                        this,
                        "Property location is not available.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Uri uri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1"
                            + "&destination="
                            + Uri.encode(finalLocation)
            );

            try {
                startActivity(
                        new Intent(Intent.ACTION_VIEW, uri)
                );
            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "No maps app or browser available.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        actions.addView(btnDirections);

        Button btnContact = new Button(this);
        btnContact.setText("Contact Host");
        btnContact.setTextSize(12);
        btnContact.setTextColor(Color.WHITE);

        btnContact.setBackgroundColor(
                Color.parseColor("#BA5536")
        );

        LinearLayout.LayoutParams contactParams =
                new LinearLayout.LayoutParams(
                        0,
                        dpToPx(44),
                        1f
                );

        btnContact.setLayoutParams(contactParams);

        btnContact.setOnClickListener(v -> {
            if (!landlordPhone.isEmpty()) {
                try {
                    Intent dialIntent = new Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:" + landlordPhone)
                    );

                    startActivity(dialIntent);

                } catch (Exception e) {
                    Toast.makeText(
                            this,
                            "Could not open the phone dialer.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            } else {
                startActivity(
                        new Intent(
                                ViewingsActivity.this,
                                UserSelectionActivity.class
                        )
                );
            }
        });

        actions.addView(btnContact);
        content.addView(actions);

        card.addView(content);

        if (!listingId.isEmpty()) {
            card.setOnClickListener(v -> {
                Intent intent = new Intent(
                        ViewingsActivity.this,
                        PropertyDetailsActivity.class
                );

                intent.putExtra("LISTING_ID", listingId);
                startActivity(intent);
            });
        }

        viewingsContainer.addView(card);
    }

    private String clean(String value) {
        if (value == null
                || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }

        return value.trim();
    }

    private int dpToPx(int dp) {
        return Math.round(
                dp * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
