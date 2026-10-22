package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LandlordDashboardActivity extends AppCompatActivity {

    private static final String TAG = "LandlordDashboard";

    // 2x2 Grid TextViews
    private TextView txtTotalListings;
    private TextView txtAvailableListings;
    private TextView txtPendingListings;
    private TextView txtUnreadMessagesCard;

    // Notification Dots
    private View txtNotificationBadge;
    private View landlordMessageDot;
    private View enquiriesCardDot;
    private View rentalApplicationsCardDot;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler badgeHandler = new Handler(Looper.getMainLooper());

    private volatile boolean badgeLoading = false;
    private boolean dashboardVisible = false;

    private final Runnable badgePoll = new Runnable() {
        @Override
        public void run() {
            if (!dashboardVisible) return;
            refreshEnquiryBadge();
            badgeHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_dashboard);

        // 1. Setup Safe Window Insets for Floating Nav
        View landlordRoot = findViewById(R.id.landlordRoot);
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);
            if (landlordRoot != null) {
                ViewCompat.setOnApplyWindowInsetsListener(landlordRoot, (v, insets) -> {
                    Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                    v.setPadding(0, statusBar.top, 0, 0);

                    if (floatingBottomNav.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                        CoordinatorLayout.LayoutParams lp =
                                (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                        int baseMarginPx = (int) (18 * getResources().getDisplayMetrics().density);
                        lp.bottomMargin = navBar.bottom + baseMarginPx;
                        floatingBottomNav.setLayoutParams(lp);
                        floatingBottomNav.setPadding(0, 0, 0, 0);
                    }
                    return WindowInsetsCompat.CONSUMED;
                });
            }
        }

        // 2. Personalize Greeting
        TextView txtWelcome = findViewById(R.id.txtWelcome);
        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String userName = prefs.getString("USER_NAME", "Landlord");
        if (txtWelcome != null) {
            String firstName = userName.trim().split("\\s+")[0];
            txtWelcome.setText("Welcome, " + firstName);
        }

        // 3. Bind Grid Metrics
        txtTotalListings = findViewById(R.id.txtTotalListings);
        txtAvailableListings = findViewById(R.id.txtAvailableListings);
        txtPendingListings = findViewById(R.id.txtPendingListings);
        txtUnreadMessagesCard = findViewById(R.id.txtUnreadMessagesCard);

        // 4. Bind Notification Dots
        txtNotificationBadge = findViewById(R.id.txtNotificationBadge);
        landlordMessageDot = findViewById(R.id.landlordMessageDot);
        enquiriesCardDot = findViewById(R.id.enquiriesCardDot);
        rentalApplicationsCardDot = findViewById(R.id.rentalApplicationsCardDot);

        // 5. Set Top Bar Click Listeners
        View btnNotificationsLandlord = findViewById(R.id.btnNotificationsLandlord);
        if (btnNotificationsLandlord != null) {
            btnNotificationsLandlord.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        }

        View btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> startActivity(new Intent(this, LandlordProfileActivity.class)));
        }

        // 6. Set Content Card Click Listeners
        View cardTotalListings = findViewById(R.id.cardTotalListings);
        if (cardTotalListings != null) {
            cardTotalListings.setOnClickListener(v -> {
                Intent intent = new Intent(this, MyListingsActivity.class);
                intent.putExtra("FILTER_STATUS", "ALL");
                startActivity(intent);
            });
        }

        View cardAvailableListings = findViewById(R.id.cardAvailableListings);
        if (cardAvailableListings != null) {
            cardAvailableListings.setOnClickListener(v -> {
                Intent intent = new Intent(this, MyListingsActivity.class);
                intent.putExtra("FILTER_STATUS", "APPROVED");
                startActivity(intent);
            });
        }

        View cardPendingListings = findViewById(R.id.cardPendingListings);
        if (cardPendingListings != null) {
            cardPendingListings.setOnClickListener(v -> {
                Intent intent = new Intent(this, MyListingsActivity.class);
                intent.putExtra("FILTER_STATUS", "PENDING");
                startActivity(intent);
            });
        }

        View cardPendingApplications = findViewById(R.id.cardPendingApplications);
        if (cardPendingApplications != null) {
            cardPendingApplications.setOnClickListener(v -> {
                Intent intent = new Intent(this, EnquiriesActivity.class);
                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }

        View cardRentalApplications = findViewById(R.id.cardRentalApplications);
        if (cardRentalApplications != null) {
            cardRentalApplications.setOnClickListener(v -> {
                Intent intent = new Intent(this, ApplicationsActivity.class);
                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }

        View cardMessagesGrid = findViewById(R.id.cardMessagesGrid);
        if (cardMessagesGrid != null) {
            cardMessagesGrid.setOnClickListener(v -> {
                Intent intent = new Intent(this, UserSelectionActivity.class);
                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }

        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        View navHome = findViewById(R.id.navHome);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                View scroll = findViewById(R.id.dashboardScroll);
                if (scroll != null) scroll.scrollTo(0, 0);
                refreshDashboardData();
            });
        }

        View navListings = findViewById(R.id.navListings);
        if (navListings != null) {
            navListings.setOnClickListener(v -> startActivity(new Intent(this, MyListingsActivity.class)));
        }

        View navAdd = findViewById(R.id.navAdd);
        if (navAdd != null) {
            navAdd.setOnClickListener(v -> startActivity(new Intent(this, AddListingActivity.class)));
        }

        View navMessages = findViewById(R.id.navMessages);
        if (navMessages != null) {
            navMessages.setOnClickListener(v -> {
                Intent intent = new Intent(this, UserSelectionActivity.class);
                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        dashboardVisible = true;
        refreshDashboardData();

        badgeHandler.removeCallbacks(badgePoll);
        badgeHandler.post(badgePoll);
    }

    @Override
    protected void onPause() {
        dashboardVisible = false;
        badgeHandler.removeCallbacks(badgePoll);
        super.onPause();
    }

    private void refreshDashboardData() {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                JSONArray listings = fetchRows(
                        "/rest/v1/listings?select=status,availability&landlord_uid=eq." + SupabaseHelper.encode(userId),
                        token
                );

                int total = listings.length();
                int available = 0;
                int pending = 0;

                for (int i = 0; i < listings.length(); i++) {
                    JSONObject listing = listings.optJSONObject(i);
                    if (listing == null) continue;

                    String availability = listing.optString("availability", "");
                    String status = listing.optString("status", "");

                    if ("available".equalsIgnoreCase(availability) || "available".equalsIgnoreCase(status)) {
                        available++;
                    }
                    if ("pending".equalsIgnoreCase(availability) || "pending".equalsIgnoreCase(status)) {
                        pending++;
                    }
                }

                final int fTotal = total;
                final int fAvailable = available;
                final int fPending = pending;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (txtTotalListings != null) txtTotalListings.setText(String.valueOf(fTotal));
                    if (txtAvailableListings != null) txtAvailableListings.setText(String.valueOf(fAvailable));
                    if (txtPendingListings != null) txtPendingListings.setText(String.valueOf(fPending));
                });
            } catch (Exception e) {
                Log.e(TAG, "Could not load properties metrics", e);
            }
        });
    }

    private void refreshEnquiryBadge() {
        if (badgeLoading || executor.isShutdown()) return;

        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            setDot(txtNotificationBadge, false);
            setDot(landlordMessageDot, false);
            setDot(enquiriesCardDot, false);
            setDot(rentalApplicationsCardDot, false);
            return;
        }

        badgeLoading = true;

        executor.execute(() -> {
            boolean unseenEnquiries = false;
            boolean unseenChatRequests = false;
            boolean unseenNotifications = false;
            boolean unseenRentalApplications = false;
            boolean unseenMessages = false;
            int exactUnreadMessageCount = 0;

            String encodedId = SupabaseHelper.encode(userId);

            try {
                JSONArray rows = fetchRows("/rest/v1/enquiries?select=id&landlord_uid=eq." + encodedId, token);
                unseenEnquiries = LandlordNotificationState.hasUnseen(getApplicationContext(), "enquiries", userId, rows);
            } catch (Exception ignored) {}

            try {
                JSONArray rows = fetchRows("/rest/v1/chat_requests?select=id&status=eq.pending&receiver_id=eq." + encodedId, token);
                unseenChatRequests = LandlordNotificationState.hasUnseen(getApplicationContext(), "chat", userId, rows);
            } catch (Exception ignored) {}

            try {
                JSONArray rows = fetchRows("/rest/v1/notifications?select=id&user_uid=eq." + encodedId, token);
                unseenNotifications = LandlordNotificationState.hasUnseen(getApplicationContext(), "notifications", userId, rows);
            } catch (Exception ignored) {}

            try {
                JSONArray rows = fetchRows("/rest/v1/rental_applications?select=id&status=eq.PENDING&landlord_uid=eq." + encodedId, token);
                unseenRentalApplications = LandlordNotificationState.hasUnseen(getApplicationContext(), "rental_applications", userId, rows);
            } catch (Exception ignored) {}

            try {
                JSONArray rows = fetchRows("/rest/v1/messages?select=id&receiver_id=eq." + encodedId, token);
                Set<String> seenIds = LandlordNotificationState.seen(getApplicationContext(), "messages", userId);

                for (int i = 0; i < rows.length(); i++) {
                    if (!seenIds.contains(rows.getJSONObject(i).optString("id"))) {
                        exactUnreadMessageCount++;
                    }
                }
                unseenMessages = exactUnreadMessageCount > 0;
            } catch (Exception ignored) {}

            boolean showEnquiries = unseenEnquiries;
            boolean showRentals = unseenRentalApplications;
            boolean showMessages = unseenMessages;
            boolean showMainNotification = unseenEnquiries || unseenChatRequests || unseenNotifications || unseenRentalApplications || unseenMessages;
            int finalMsgCount = exactUnreadMessageCount;

            badgeLoading = false;

            runOnUiThread(() -> {
                if (!dashboardVisible || isFinishing() || isDestroyed()) return;

                setDot(txtNotificationBadge, showMainNotification);
                setDot(landlordMessageDot, showMessages);
                setDot(enquiriesCardDot, showEnquiries);
                setDot(rentalApplicationsCardDot, showRentals);

                if (txtUnreadMessagesCard != null) {
                    txtUnreadMessagesCard.setText(String.valueOf(finalMsgCount));
                }
            });
        });
    }

    private void setDot(View dot, boolean visible) {
        if (dot != null) dot.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private JSONArray fetchRows(String endpoint, String token) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = SupabaseHelper.createConnection(endpoint, "GET", token);
            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);
            if (code >= 200 && code < 300) {
                return new JSONArray(response);
            }
            return new JSONArray();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        dashboardVisible = false;
        badgeHandler.removeCallbacks(badgePoll);
        executor.shutdownNow();
        super.onDestroy();
    }
}