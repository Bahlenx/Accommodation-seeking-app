package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
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
import java.net.URLEncoder;

public class ViewingsActivity extends AppCompatActivity {

    private LinearLayout viewingsContainer;
    private View layoutEmptyViewings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewings);

        viewingsContainer = findViewById(R.id.viewingsContainer);
        layoutEmptyViewings = findViewById(R.id.layoutEmptyViewings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View btnEmpty = findViewById(R.id.btnEmptyFindPlace);
        if (btnEmpty != null) {
            btnEmpty.setOnClickListener(v -> finish());
        }

        setupBottomNavigation();
        loadScheduledViewings();
    }

    private void setupBottomNavigation() {
        View viewingsRoot = findViewById(R.id.viewingsRoot);
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);

            if (viewingsRoot != null) {
                ViewCompat.setOnApplyWindowInsetsListener(viewingsRoot, (v, insets) -> {
                    Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                    v.setPadding(0, statusBar.top, 0, 0);

                    if (floatingBottomNav.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                        int baseMarginPx = (int) (16 * getResources().getDisplayMetrics().density);
                        lp.bottomMargin = navBar.bottom + baseMarginPx;
                        floatingBottomNav.setLayoutParams(lp);
                        floatingBottomNav.setPadding(0, 0, 0, 0);
                    }

                    return WindowInsetsCompat.CONSUMED;
                });
            }

            // Tab 1: Discover
            View navHome = findViewById(R.id.navHome);
            if (navHome != null) {
                navHome.setOnClickListener(v -> {
                    Intent intent = new Intent(ViewingsActivity.this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }

            // Tab 2: Favorites
            View navFavorites = findViewById(R.id.navFavorites);
            if (navFavorites != null) {
                navFavorites.setOnClickListener(v -> {
                    startActivity(new Intent(ViewingsActivity.this, SavedListingsActivity.class));
                    finish();
                });
            }

            // Tab 4: Messages
            View navMessages = findViewById(R.id.navMessages);
            if (navMessages != null) {
                navMessages.setOnClickListener(v -> {
                    startActivity(new Intent(ViewingsActivity.this, UserSelectionActivity.class));
                    finish();
                });
            }
        }
    }

    private void loadScheduledViewings() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String encodedUserId = URLEncoder.encode(userId, "UTF-8");
                String endpoint = "/rest/v1/viewings?select=*&tenant_uid=eq."
                        + encodedUserId
                        + "&order=viewing_date.asc";

                HttpURLConnection connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    runOnUiThread(this::showFallbackOrEmpty);
                    return;
                }

                JSONArray viewings = new JSONArray(response);

                runOnUiThread(() -> {
                    viewingsContainer.removeAllViews();
                    if (viewings.length() == 0) {
                        layoutEmptyViewings.setVisibility(View.VISIBLE);
                    } else {
                        layoutEmptyViewings.setVisibility(View.GONE);
                        for (int i = 0; i < viewings.length(); i++) {
                            try {
                                JSONObject item = viewings.getJSONObject(i);
                                addViewingCard(item);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(this::showFallbackOrEmpty);
            }
        }).start();
    }

    private void showFallbackOrEmpty() {
        if (viewingsContainer.getChildCount() == 0) {
            layoutEmptyViewings.setVisibility(View.VISIBLE);
        }
    }

    private void addViewingCard(JSONObject item) {
        try {
            String listingId = item.optString("listing_id", "");
            String title = item.optString("property_name", "Student Room / House");
            String location = item.optString("location", "Mthatha Central");
            String date = item.optString("viewing_date", "Saturday, 11:00 AM");
            String status = item.optString("status", "Confirmed");
            String landlordPhone = item.optString("landlord_phone", "");

            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dpToPx(16));
            card.setLayoutParams(params);
            card.setCardElevation(dpToPx(2));
            card.setRadius(dpToPx(18));
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            int pad = dpToPx(16);
            content.setPadding(pad, pad, pad, pad);

            // Header row: Title + Status Pill
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextSize(17);
            tvTitle.setTextColor(Color.parseColor("#1F1614"));
            tvTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tvTitle.setLayoutParams(titleParams);
            header.addView(tvTitle);

            TextView tvStatus = new TextView(this);
            tvStatus.setText(status);
            tvStatus.setTextSize(11);
            tvStatus.setTypeface(null, Typeface.BOLD);
            tvStatus.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

            if ("Confirmed".equalsIgnoreCase(status)) {
                tvStatus.setTextColor(Color.parseColor("#007749"));
                tvStatus.setBackgroundColor(Color.parseColor("#E6F4EA"));
            } else {
                tvStatus.setTextColor(Color.parseColor("#BA5536"));
                tvStatus.setBackgroundColor(Color.parseColor("#FDEEE9"));
            }
            header.addView(tvStatus);
            content.addView(header);

            // Appointment Date & Time Row
            TextView tvDate = new TextView(this);
            tvDate.setText("🗓 " + date);
            tvDate.setTextSize(13);
            tvDate.setTextColor(Color.parseColor("#BA5536"));
            tvDate.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dateParams.setMargins(0, dpToPx(6), 0, 0);
            tvDate.setLayoutParams(dateParams);
            content.addView(tvDate);

            // Address Row
            TextView tvLocation = new TextView(this);
            tvLocation.setText("📍 " + location);
            tvLocation.setTextSize(13);
            tvLocation.setTextColor(Color.parseColor("#8C7A70"));
            LinearLayout.LayoutParams locParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            locParams.setMargins(0, dpToPx(4), 0, dpToPx(14));
            tvLocation.setLayoutParams(locParams);
            content.addView(tvLocation);

            // Action Buttons: Directions + Landlord Call
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button btnDirections = new Button(this);
            btnDirections.setText("Directions");
            btnDirections.setTextSize(12);
            btnDirections.setTextColor(Color.parseColor("#1F1614"));
            btnDirections.setBackgroundColor(Color.parseColor("#FAF7F5"));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, dpToPx(40), 1.0f);
            btnParams.setMarginEnd(dpToPx(8));
            btnDirections.setLayoutParams(btnParams);
            btnDirections.setOnClickListener(v -> {
                Uri mapUri = Uri.parse("geo:0,0?q=" + Uri.encode(location));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                startActivity(mapIntent);
            });
            actions.addView(btnDirections);

            Button btnContact = new Button(this);
            btnContact.setText("Contact Host");
            btnContact.setTextSize(12);
            btnContact.setTextColor(Color.WHITE);
            btnContact.setBackgroundColor(Color.parseColor("#BA5536"));
            LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(0, dpToPx(40), 1.0f);
            btnContact.setLayoutParams(contactParams);
            btnContact.setOnClickListener(v -> {
                if (!landlordPhone.isEmpty()) {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + landlordPhone));
                    startActivity(callIntent);
                } else {
                    Toast.makeText(this, "Connecting to host in messages...", Toast.LENGTH_SHORT).show();
                }
            });
            actions.addView(btnContact);

            content.addView(actions);
            card.addView(content);

            card.setOnClickListener(v -> {
                if (!listingId.isEmpty()) {
                    Intent details = new Intent(ViewingsActivity.this, PropertyDetailsActivity.class);
                    details.putExtra("LISTING_ID", listingId);
                    startActivity(details);
                }
            });

            viewingsContainer.addView(card);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}