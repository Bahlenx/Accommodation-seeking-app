package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
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

    private LoadingOverlayHelper loadingOverlayHelper;

    private static final String TAG = "ViewingsActivity";

    private LinearLayout viewingsContainer;
    private View layoutEmptyViewings;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean loading = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewings);
        loadingOverlayHelper = new LoadingOverlayHelper(findViewById(R.id.layoutLoadingOverlay), 3000L);

        viewingsContainer = findViewById(R.id.viewingsContainer);
        layoutEmptyViewings = findViewById(R.id.layoutEmptyViewings);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btnEmpty = findViewById(R.id.btnEmptyFindPlace);
        if (btnEmpty != null) {
            btnEmpty.setOnClickListener(v -> {
                Intent intent = new Intent(ViewingsActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        setupBottomNavigation();

        if (viewingsContainer == null || layoutEmptyViewings == null) {
            Toast.makeText(this, R.string.error_viewings_init_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewingsContainer != null && layoutEmptyViewings != null) {
            loadScheduledViewings();
        }
    }

    private void setupBottomNavigation() {
        View viewingsRoot = findViewById(R.id.viewingsRoot);
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null && viewingsRoot != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);

            ViewCompat.setOnApplyWindowInsetsListener(viewingsRoot, (v, insets) -> {
                Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                v.setPadding(0, statusBar.top, 0, 0);

                if (floatingBottomNav.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                    CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                    int baseMarginPx = dpToPx(16);
                    lp.bottomMargin = navBar.bottom + baseMarginPx;
                    floatingBottomNav.setLayoutParams(lp);
                    floatingBottomNav.setPadding(0, 0, 0, 0);
                }

                return WindowInsetsCompat.CONSUMED;
            });
        }

        View navHome = findViewById(R.id.navHome);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(ViewingsActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        View navFavorites = findViewById(R.id.navFavorites);
        if (navFavorites != null) {
            navFavorites.setOnClickListener(v -> {
                startActivity(new Intent(ViewingsActivity.this, SavedListingsActivity.class));
                finish();
            });
        }

        View navMessages = findViewById(R.id.navMessages);
        if (navMessages != null) {
            navMessages.setOnClickListener(v -> {
                startActivity(new Intent(ViewingsActivity.this, UserSelectionActivity.class));
                finish();
            });
        }
    }

    private void loadScheduledViewings() {
        if (loadingOverlayHelper != null) loadingOverlayHelper.beginTask();
        if (loading || executor.isShutdown()) {
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.trim().isEmpty() || accessToken == null || accessToken.trim().isEmpty()) {
            Toast.makeText(this, R.string.please_log_in_again, Toast.LENGTH_SHORT).show();
            showEmptyState();
            return;
        }

        loading = true;

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/viewings?select=*"
                        + "&tenant_uid=eq." + SupabaseHelper.encode(userId)
                        + "&order=viewing_date.asc";

                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, getString(R.string.error_could_not_load_viewings_http) + " " + responseCode, Toast.LENGTH_LONG).show();
                        showEmptyState();
                    });
                    return;
                }

                JSONArray viewings = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    viewingsContainer.removeAllViews();
                    if (viewings.length() == 0) {
                        showEmptyState();
                        return;
                    }

                    layoutEmptyViewings.setVisibility(View.GONE);

                    for (int i = 0; i < viewings.length(); i++) {
                        JSONObject viewing = viewings.optJSONObject(i);
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
                    Toast.makeText(this, R.string.error_network_loading_viewings, Toast.LENGTH_LONG).show();
                    showEmptyState();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                loading = false;
                if (loadingOverlayHelper != null) {
                    runOnUiThread(() -> loadingOverlayHelper.endTask());
                }
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

    private String getLocalizedStatus(String status) {
        if ("Confirmed".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status)) {
            return getString(R.string.status_confirmed);
        } else if ("Declined".equalsIgnoreCase(status) || "Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            return getString(R.string.status_declined);
        } else {
            return getString(R.string.status_pending);
        }
    }

    private String getLocalizedViewingDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return getString(R.string.label_date_not_provided);
        String result = rawDate;
        if (result.contains("Morning")) {
            result = result.replace("Morning", getString(R.string.slot_morning));
        }
        if (result.contains("Midday")) {
            result = result.replace("Midday", getString(R.string.slot_midday));
        }
        if (result.contains("Afternoon")) {
            result = result.replace("Afternoon", getString(R.string.slot_afternoon));
        }
        return result;
    }

    private void addViewingCard(JSONObject item) {
        String listingId = clean(item.optString("listing_id", ""));
        String title = clean(item.optString("property_name", ""));
        String location = clean(item.optString("location", ""));
        String date = clean(item.optString("viewing_date", ""));
        String status = clean(item.optString("status", ""));
        String landlordPhone = clean(item.optString("landlord_phone", ""));

        String landlordUid = item.optString("landlord_uid", "");
        if (landlordUid.isEmpty()) landlordUid = item.optString("landlord_id", "");
        if (landlordUid.isEmpty()) landlordUid = item.optString("host_uid", "");

        String landlordName = clean(item.optString("landlord_name", ""));
        if (landlordName.isEmpty()) landlordName = getString(R.string.role_landlord);

        if (title.isEmpty()) title = getString(R.string.default_viewing_property_title);
        if (location.isEmpty()) location = getString(R.string.default_unknown_location);
        if (date.isEmpty()) date = getString(R.string.label_date_not_provided);
        if (status.isEmpty()) status = getString(R.string.status_pending_host);

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(16));
        card.setLayoutParams(cardParams);
        card.setCardElevation(dpToPx(2));
        card.setRadius(dpToPx(18));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        content.setPadding(padding, padding, padding, padding);

        // Header: Title + Status Pill
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
        tvStatus.setText(getLocalizedStatus(status));
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

        if ("Confirmed".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(Color.parseColor("#007749"));
            tvStatus.setBackgroundColor(Color.parseColor("#E6F4EA"));
        } else if ("Declined".equalsIgnoreCase(status) || "Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(Color.parseColor("#A12626"));
            tvStatus.setBackgroundColor(Color.parseColor("#FCE8E6"));
        } else {
            tvStatus.setTextColor(Color.parseColor("#BA5536"));
            tvStatus.setBackgroundColor(Color.parseColor("#FDEEE9"));
        }

        header.addView(tvStatus);
        content.addView(header);

        // Appointment Date
        TextView tvDate = new TextView(this);
        tvDate.setText(getLocalizedViewingDate(date));
        tvDate.setTextSize(13);
        tvDate.setTextColor(Color.parseColor("#BA5536"));
        tvDate.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dateParams.setMargins(0, dpToPx(8), 0, 0);
        tvDate.setLayoutParams(dateParams);
        content.addView(tvDate);

        // Address
        TextView tvLocation = new TextView(this);
        tvLocation.setText(location);
        tvLocation.setTextSize(13);
        tvLocation.setTextColor(Color.parseColor("#8C7A70"));
        LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        locationParams.setMargins(0, dpToPx(5), 0, dpToPx(14));
        tvLocation.setLayoutParams(locationParams);
        content.addView(tvLocation);

        // Actions: Directions + Contact Host
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button btnDirections = new Button(this);
        btnDirections.setText(R.string.btn_viewing_directions);
        btnDirections.setTextSize(12);
        btnDirections.setTextColor(Color.parseColor("#1F1614"));
        btnDirections.setBackgroundColor(Color.parseColor("#FAF7F5"));
        LinearLayout.LayoutParams directionsParams = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        directionsParams.setMarginEnd(dpToPx(8));
        btnDirections.setLayoutParams(directionsParams);

        String finalLocation = location;
        btnDirections.setOnClickListener(v -> {
            if (finalLocation.equals(getString(R.string.default_unknown_location))) {
                Toast.makeText(this, R.string.error_location_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(finalLocation));
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                Toast.makeText(this, R.string.could_not_open_maps, Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(btnDirections);

        Button btnContact = new Button(this);
        btnContact.setText(R.string.btn_contact_host);
        btnContact.setTextSize(12);
        btnContact.setTextColor(Color.WHITE);
        btnContact.setBackgroundColor(Color.parseColor("#BA5536"));
        LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        btnContact.setLayoutParams(contactParams);

        final String finalLandlordUid = landlordUid;
        final String finalLandlordName = landlordName;

        btnContact.setOnClickListener(v -> {
            if (!finalLandlordUid.isEmpty()) {
                openChatWithHost(finalLandlordUid, finalLandlordName);
            } else if (!landlordPhone.isEmpty()) {
                try {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + landlordPhone));
                    startActivity(dialIntent);
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_cannot_open_dialer, Toast.LENGTH_SHORT).show();
                }
            } else {
                startActivity(new Intent(ViewingsActivity.this, UserSelectionActivity.class));
            }
        });

        actions.addView(btnContact);
        
        boolean isPast = WorkflowAutomationHelper.isViewingDateInPast(date);
        boolean isActive = !isPast && ("Pending".equalsIgnoreCase(status)
                || "Pending Host".equalsIgnoreCase(status)
                || "Confirmed".equalsIgnoreCase(status));

        if (isActive) {
            Button btnCancel = new Button(this);
            btnCancel.setText("Cancel Viewing");
            btnCancel.setAllCaps(false);
            btnCancel.setTextColor(Color.parseColor("#B91C1C"));
            btnCancel.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
            btnCancel.setLayoutParams(cancelParams);
            final String finalTitleParam = title;
            btnCancel.setOnClickListener(v -> confirmCancelViewing(item.optString("id", ""), listingId, finalLandlordUid, finalTitleParam));
            actions.addView(btnCancel);
        } else if (isPast) {
            card.setAlpha(0.65f); // Visually separate expired past viewings
        }

        content.addView(actions);
        card.addView(content);

        if (!listingId.isEmpty()) {
            final String finalTitleParam = title;
            final String finalDateParam = date;
            final String finalStatusParam = status;
            card.setOnClickListener(v -> {
                showViewingDetailsDialog(item, finalLandlordUid, finalLandlordName, finalLocation, finalTitleParam, finalDateParam, finalStatusParam, listingId, isActive, isPast);
            });
        }

        viewingsContainer.addView(card);
    }
    
    private void showViewingDetailsDialog(JSONObject item, String landlordUid, String landlordName, String location, String title, String date, String status, String listingId, boolean isActive, boolean isPast) {
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_ban_appeal_details, null);
        TextView tvDetails = dialogView.findViewById(R.id.tvDetails);
        Button btnViewListing = dialogView.findViewById(R.id.btnAdminEvidence);
        Button btnAddToCal = dialogView.findViewById(R.id.btnUserEvidence);
        Button btnContactHost = dialogView.findViewById(R.id.btnLiftRestriction);

        String details = "Property: " + title + "\nStatus: " + status + (isPast ? " • Past" : "") + "\nDate: " + date + "\nAddress: " + location + "\nHost: " + landlordName;
        tvDetails.setText(details);
        tvDetails.setTextColor(Color.parseColor("#1F1614"));

        btnViewListing.setVisibility(View.VISIBLE);
        btnViewListing.setText("View Listing");
        btnViewListing.setOnClickListener(v -> {
            Intent intent = new Intent(ViewingsActivity.this, PropertyDetailsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            startActivity(intent);
        });

        btnAddToCal.setVisibility(View.VISIBLE);
        btnAddToCal.setText("Add to Calendar");
        btnAddToCal.setOnClickListener(v -> {
            WorkflowAutomationHelper.openAddToCalendarIntent(this, title, date, location, 0.0, 0.0);
        });

        btnContactHost.setText("Contact Host");
        btnContactHost.setOnClickListener(v -> openChatWithHost(landlordUid, landlordName));

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("Close", null);
                
        if (isActive) {
            builder.setNeutralButton("Cancel Viewing", (dialog, which) -> confirmCancelViewing(item.optString("id", ""), listingId, landlordUid, title));
        }

        androidx.appcompat.app.AlertDialog dialog = builder.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#F8F9FA")));
        }
    }
    
    private void confirmCancelViewing(String viewingId, String listingId, String landlordUid, String propertyName) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cancel Viewing")
                .setMessage("Are you sure you want to cancel this scheduled viewing?")
                .setPositiveButton("Cancel Viewing", (dialog, which) -> cancelViewing(viewingId, listingId, landlordUid, propertyName))
                .setNegativeButton("Keep", null)
                .show();
    }
    
    private void cancelViewing(String viewingId, String listingId, String landlordUid, String propertyName) {
        executor.execute(() -> {
            String token = SupabaseHelper.getAccessToken(this);
            String endpoint = "/rest/v1/viewings?id=eq." + SupabaseHelper.encode(viewingId)
                    + "&tenant_uid=eq." + SupabaseHelper.encode(SupabaseHelper.getUserId(this));

            boolean ok = WorkflowAutomationHelper.patchStatusWithFallback(
                    this, endpoint, "Cancelled", "Declined", token);

            if (ok) {
                WorkflowAutomationHelper.syncListingViewingAvailability(this, listingId, token);
                try {
                    if (landlordUid != null && !landlordUid.isEmpty()) {
                        SupabaseHelper.createNotification(
                                landlordUid,
                                "Viewing Cancelled",
                                "A tenant cancelled their scheduled viewing for " + propertyName + ".",
                                token
                        );
                    }
                } catch (Exception ignored) {}
            }
            runOnUiThread(() -> {
                Toast.makeText(this, ok ? "Viewing cancelled." : "Could not cancel viewing.", Toast.LENGTH_SHORT).show();
                if (ok) loadScheduledViewings();
            });
        });
    }

    private void openChatWithHost(String hostUid, String hostName) {
        String myUid = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (myUid == null || hostUid == null || myUid.equals(hostUid)) {
            Toast.makeText(this, "Cannot message host", Toast.LENGTH_SHORT).show();
            return;
        }

        // Launch chat immediately so UI feels instant
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("RECEIVER_ID", hostUid);
        intent.putExtra("RECEIVER_NAME", hostName);
        intent.putExtra("IS_COMMUNITY", false);
        startActivity(intent);

        // Register an accepted chat_request in the background so it shows on Messages & Community
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
                String myName = prefs.getString("USER_NAME", "Resident");
                String myRole = prefs.getString("USER_ROLE", "tenant");

                JSONObject body = new JSONObject();
                body.put("sender_id", myUid);
                body.put("sender_name", myName);
                body.put("sender_role", myRole);
                body.put("receiver_id", hostUid);
                body.put("receiver_name", hostName != null && !hostName.isEmpty() ? hostName : "Landlord");
                body.put("receiver_role", "landlord");
                body.put("status", "accepted");

                conn = SupabaseHelper.createConnection("/rest/v1/chat_requests", "POST", token);
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
                SupabaseHelper.writeJson(conn, body);
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private String clean(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        if (loadingOverlayHelper != null) {
            loadingOverlayHelper.cleanup();
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}