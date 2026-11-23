package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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

public class HomeActivity extends AppCompatActivity {

    private RecyclerView rvPopularPlaces;
    private RecyclerView rvExploreNewArea;
    private SwipeRefreshLayout swipeRefreshHome;

    private View notificationDot;
    private View messageDot;

    private LoadingOverlayHelper loadingOverlayHelper;
    private NetworkStatusMonitor networkStatusMonitor;

    private final java.util.Map<String, String> tenantViewingBadges = new java.util.concurrent.ConcurrentHashMap<>();

    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(3);

    private final ExecutorService notificationExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler notificationHandler =
            new Handler(Looper.getMainLooper());

    private volatile boolean notificationLoading = false;
    private boolean homeVisible = false;

    private JSONArray latestMessages;
    private JSONArray latestApplications;
    private JSONArray latestChatRequests;
    private JSONArray latestViewings;
    private JSONArray latestViewingNotificationKeys;

    private String notificationUserId;

    private boolean hasUnreadLandlordMessages = false;
    private boolean hasUnreadViewingDecisions = false;
    private boolean hasUnreadChatRequests = false;
    private boolean hasUnreadApplications = false;

    private JSONObject newestLandlordMessage;

    private final Runnable notificationPoll = new Runnable() {
        @Override
        public void run() {
            if (!homeVisible) {
                return;
            }

            SupabaseHelper.verifyActiveSessionOrKick(HomeActivity.this);
            refreshNotifications();
            notificationHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
        NotificationHelper.startBackgroundPoller(this);
        
        refreshCachedUserLocation();
        refreshReturningUserBookingHistory();

        setContentView(R.layout.activity_home);

        loadingOverlayHelper = new LoadingOverlayHelper(findViewById(R.id.layoutLoadingOverlay), 3000L);
        networkStatusMonitor = new NetworkStatusMonitor(this, findViewById(R.id.viewHomeConnectionDot), findViewById(R.id.tvHomeConnectionStatus));

        View homeRoot = findViewById(R.id.homeRoot);
        View floatingBottomNav =
                findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);
        }

        if (homeRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(
                    homeRoot,
                    (v, insets) -> {

                        Insets statusBar = insets.getInsets(
                                WindowInsetsCompat.Type.statusBars()
                        );

                        Insets navBar = insets.getInsets(
                                WindowInsetsCompat.Type.navigationBars()
                        );

                        v.setPadding(0, statusBar.top, 0, 0);

                        if (floatingBottomNav != null
                                && floatingBottomNav.getLayoutParams()
                                instanceof CoordinatorLayout.LayoutParams) {

                            CoordinatorLayout.LayoutParams lp =
                                    (CoordinatorLayout.LayoutParams)
                                            floatingBottomNav.getLayoutParams();

                            int baseMarginPx = Math.round(
                                    16f * getResources()
                                            .getDisplayMetrics().density
                            );

                            lp.bottomMargin =
                                    navBar.bottom + baseMarginPx;

                            floatingBottomNav.setLayoutParams(lp);
                            floatingBottomNav.setPadding(0, 0, 0, 0);
                        }

                        return WindowInsetsCompat.CONSUMED;
                    }
            );
        }

        rvPopularPlaces = findViewById(R.id.rvPopularPlaces);
        rvExploreNewArea = findViewById(R.id.rvExploreNewArea);
        swipeRefreshHome = findViewById(R.id.swipeRefreshHome);

        if (swipeRefreshHome != null) {
            swipeRefreshHome.setColorSchemeColors(Color.parseColor("#BA5536"));
            swipeRefreshHome.setOnRefreshListener(() -> {
                SessionCacheManager.invalidateJson("home_feed_listings");
                loadHomeFeedFromSupabase();
                refreshNotifications();
            });
        }

        if (rvPopularPlaces != null) {
            rvPopularPlaces.setLayoutManager(
                    new LinearLayoutManager(
                            this,
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );

            rvPopularPlaces.setNestedScrollingEnabled(false);
        }

        if (rvExploreNewArea != null) {
            rvExploreNewArea.setLayoutManager(
                    new LinearLayoutManager(
                            this,
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );

            rvExploreNewArea.setNestedScrollingEnabled(false);
        }

        notificationDot = findViewById(R.id.notificationDot);
        messageDot = findViewById(R.id.messageDot);

        if (notificationDot != null) {
            notificationDot.setVisibility(View.GONE);
        }

        if (messageDot != null) {
            messageDot.setVisibility(View.GONE);
        }

        setupPersonalizedGreeting();

        View btnNotificationsHome =
                findViewById(R.id.btnNotificationsHome);

        if (btnNotificationsHome != null) {
            btnNotificationsHome.setOnClickListener(
                    v -> showHomeNotifications()
            );
        }

        View btnProfile = findViewById(R.id.btnProfile);

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    startActivity(
                            new Intent(
                                    HomeActivity.this,
                                    TenantProfileActivity.class
                            )
                    )
            );
        }

        EditText searchAccommodation =
                findViewById(R.id.etSearchPlace);

        if (searchAccommodation != null) {
            searchAccommodation.setOnEditorActionListener(
                    (v, actionId, event) -> {

                        if (actionId == EditorInfo.IME_ACTION_SEARCH
                                || actionId == EditorInfo.IME_ACTION_DONE) {

                            String query = searchAccommodation
                                    .getText()
                                    .toString()
                                    .trim();

                            openSearch(query, null);
                            return true;
                        }

                        return false;
                    }
            );
        }

        ImageButton btnHomeFilter = findViewById(R.id.btnHomeFilter);
        if (btnHomeFilter != null) {
            btnHomeFilter.setOnClickListener(v -> {
                FilterBottomSheetDialog filterSheet = new FilterBottomSheetDialog();
                filterSheet.setFilterListener((offerType, min, max, propType, arrangement, utilities) -> {
                    EditText homeSearch = findViewById(R.id.etSearchPlace);
                    String query = homeSearch == null ? "" : homeSearch.getText().toString().trim();
                    Intent intent = new Intent(HomeActivity.this, SearchResultsActivity.class);
                    intent.putExtra("SEARCH_QUERY", query);
                    intent.putExtra("PROPERTY_TYPE", propType);
                    intent.putExtra("FILTER_OFFER_TYPE", offerType);
                    intent.putExtra("FILTER_MIN_PRICE", min);
                    intent.putExtra("FILTER_MAX_PRICE", max);
                    intent.putExtra("FILTER_ARRANGEMENT", arrangement);
                    if (utilities != null) {
                        intent.putStringArrayListExtra("FILTER_UTILITIES", new java.util.ArrayList<>(utilities));
                    }
                    startActivity(intent);
                });
                filterSheet.show(getSupportFragmentManager(), "HomeFilterBottomSheet");
            });
        }

        setupBottomNavigation();
        loadHomeFeedFromSupabase();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkStatusMonitor != null) {
            networkStatusMonitor.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkStatusMonitor != null) {
            networkStatusMonitor.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SupabaseHelper.verifyActiveSessionOrKick(this);

        homeVisible = true;

        View search = findViewById(R.id.etSearchPlace);

        if (search != null) {
            search.clearFocus();
        }

        View scroll = findViewById(R.id.homeScrollView);

        if (scroll != null) {
            scroll.post(scroll::requestLayout);
        }

        setupPersonalizedGreeting();

        notificationHandler.removeCallbacks(notificationPoll);
        notificationHandler.post(notificationPoll);
    }

    @Override
    protected void onPause() {
        homeVisible = false;

        notificationHandler.removeCallbacks(notificationPoll);

        super.onPause();
    }

    private void refreshNotifications() {

        if (notificationLoading
                || notificationExecutor.isShutdown()) {
            return;
        }

        String uid = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (uid == null
                || uid.isEmpty()
                || token == null
                || token.isEmpty()) {

            if (notificationDot != null) {
                notificationDot.setVisibility(View.GONE);
            }

            if (messageDot != null) {
                messageDot.setVisibility(View.GONE);
            }

            return;
        }

        notificationLoading = true;

        notificationExecutor.execute(() -> {

            try {
                String encodedUid = SupabaseHelper.encode(uid);

                JSONArray messages = fetchRows(
                        "/rest/v1/messages?select=id,sender_id,sender_name,sender_role,message_text&receiver_id=eq."
                                + encodedUid
                                + "&order=created_at.desc&limit=1000",
                        token
                );

                JSONArray applications = fetchRows(
                        "/rest/v1/rental_applications?select=id,status&tenant_uid=eq."
                                + encodedUid
                                + "&status=in.(APPROVED,DECLINED,approved,declined)&limit=1000",
                        token
                );

                JSONArray chatRequests = fetchRows(
                        "/rest/v1/chat_requests?select=id,status&receiver_id=eq."
                                + encodedUid
                                + "&status=eq.pending&limit=1000",
                        token
                );

                JSONArray viewings = fetchRows(
                        "/rest/v1/viewings?select=id,property_name,viewing_date,status&tenant_uid=eq."
                                + encodedUid
                                + "&status=in.(Confirmed,Declined)&limit=1000",
                        token
                );

                JSONArray viewingKeys =
                        viewings == null
                                ? null
                                : createViewingNotificationKeys(viewings);

                boolean unreadMessages =
                        messages != null
                                && TenantNotificationState.hasNew(
                                this,
                                uid,
                                "messages",
                                messages
                        );

                boolean unreadApplications =
                        applications != null
                                && TenantNotificationState.hasNew(
                                this,
                                uid,
                                "applications",
                                applications
                        );

                boolean unreadChatRequests =
                        chatRequests != null
                                && TenantNotificationState.hasNew(
                                this,
                                uid,
                                "chat_requests",
                                chatRequests
                        );

                boolean unreadViewings =
                        viewingKeys != null
                                && TenantNotificationState.hasNew(
                                this,
                                uid,
                                "viewing_decisions",
                                viewingKeys
                        );

                boolean unreadAlerts =
                        unreadApplications
                                || unreadChatRequests
                                || unreadViewings;

                runOnUiThread(() -> {

                    if (isFinishing()
                            || isDestroyed()
                            || !homeVisible) {
                        return;
                    }

                    notificationUserId = uid;

                    if (messages != null) {
                        latestMessages = messages;
                        hasUnreadLandlordMessages = unreadMessages;

                        newestLandlordMessage =
                                messages.length() > 0
                                        ? messages.optJSONObject(0)
                                        : null;
                    }

                    if (applications != null) {
                        latestApplications = applications;
                        hasUnreadApplications = unreadApplications;
                    }

                    if (chatRequests != null) {
                        latestChatRequests = chatRequests;
                        hasUnreadChatRequests = unreadChatRequests;
                    }

                    if (viewings != null) {
                        latestViewings = viewings;
                    }

                    if (viewingKeys != null) {
                        latestViewingNotificationKeys = viewingKeys;
                        hasUnreadViewingDecisions = unreadViewings;
                    }

                    if (messageDot != null) {
                        messageDot.setVisibility(
                                unreadMessages
                                        ? View.VISIBLE
                                        : View.GONE
                        );
                    }

                    if (notificationDot != null) {
                        notificationDot.setVisibility(
                                (unreadAlerts
                                        || hasUnreadLandlordMessages)
                                        ? View.VISIBLE
                                        : View.GONE
                        );
                    }
                });

            } catch (Exception ignored) {

            } finally {
                notificationLoading = false;
            }
        });
    }

    private JSONArray createViewingNotificationKeys(
            JSONArray viewings
    ) {
        JSONArray keys = new JSONArray();

        for (int i = 0; i < viewings.length(); i++) {

            JSONObject viewing = viewings.optJSONObject(i);

            if (viewing == null) {
                continue;
            }

            String id = viewing.optString("id", "");
            String status = viewing.optString("status", "");

            if (id.isEmpty() || status.isEmpty()) {
                continue;
            }

            try {
                JSONObject key = new JSONObject();

                key.put(
                        "id",
                        id + "_" + status.toLowerCase(Locale.getDefault())
                );

                keys.put(key);

            } catch (Exception ignored) {
            }
        }

        return keys;
    }

    private void markAlertsChecked() {

        String uid = notificationUserId;

        if (uid == null || uid.isEmpty()) {
            return;
        }

        if (latestApplications != null) {
            TenantNotificationState.markSeen(
                    this,
                    uid,
                    "applications",
                    latestApplications
            );
        }

        if (latestChatRequests != null) {
            TenantNotificationState.markSeen(
                    this,
                    uid,
                    "chat_requests",
                    latestChatRequests
            );
        }

        markViewingDecisionsSeen();

        hasUnreadChatRequests = false;
        hasUnreadApplications = false;

        updateNotificationDots();
    }

    private void markViewingDecisionsSeen() {

        String uid = notificationUserId;

        if (uid == null
                || uid.isEmpty()
                || latestViewingNotificationKeys == null) {
            return;
        }

        TenantNotificationState.markSeen(
                this,
                uid,
                "viewing_decisions",
                latestViewingNotificationKeys
        );

        hasUnreadViewingDecisions = false;

        updateNotificationDots();
    }

    private void markMessagesChecked() {

        String uid = notificationUserId;

        if (uid == null || uid.isEmpty()) {
            return;
        }

        if (latestMessages != null) {
            TenantNotificationState.markSeen(
                    this,
                    uid,
                    "messages",
                    latestMessages
            );
        }

        hasUnreadLandlordMessages = false;

        if (messageDot != null) {
            messageDot.setVisibility(View.GONE);
        }

        updateNotificationDots();
    }

    private void updateNotificationDots() {

        String uid = notificationUserId;

        if (uid == null || uid.isEmpty()) {
            return;
        }

        boolean otherAlerts = false;

        if (latestApplications != null) {
            otherAlerts = TenantNotificationState.hasNew(
                    this,
                    uid,
                    "applications",
                    latestApplications
            );
        }

        if (latestChatRequests != null) {
            otherAlerts = otherAlerts
                    || TenantNotificationState.hasNew(
                    this,
                    uid,
                    "chat_requests",
                    latestChatRequests
            );
        }

        if (latestViewingNotificationKeys != null) {
            otherAlerts = otherAlerts
                    || TenantNotificationState.hasNew(
                    this,
                    uid,
                    "viewing_decisions",
                    latestViewingNotificationKeys
            );
        }

        if (notificationDot != null) {
            notificationDot.setVisibility(
                    (otherAlerts || hasUnreadLandlordMessages)
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (messageDot != null) {
            messageDot.setVisibility(
                    hasUnreadLandlordMessages
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private void showHomeNotifications() {

        StringBuilder text = new StringBuilder();

        if (hasUnreadChatRequests
                && latestChatRequests != null
                && latestChatRequests.length() > 0) {

            text.append("You have ")
                    .append(latestChatRequests.length())
                    .append(" pending chat request(s) from a landlord. ")
                    .append("Open Messages to respond.\n\n");
        }

        if (hasUnreadLandlordMessages) {

            String sender = getString(R.string.role_landlord);

            if (newestLandlordMessage != null) {

                String name = newestLandlordMessage
                        .optString("sender_name", "")
                        .trim();

                if (!name.isEmpty()
                        && !"null".equalsIgnoreCase(name)) {
                    sender = name;
                }
            }

            text.append("You have a new message from ")
                    .append(sender)
                    .append(".\n\n");
        }

        if (hasUnreadViewingDecisions
                && latestViewings != null) {

            for (int i = 0; i < latestViewings.length(); i++) {

                JSONObject viewing =
                        latestViewings.optJSONObject(i);

                if (viewing == null) {
                    continue;
                }

                String status = viewing.optString("status", "");

                if (!"Confirmed".equalsIgnoreCase(status)
                        && !"Declined".equalsIgnoreCase(status)) {
                    continue;
                }

                text.append("Viewing ")
                        .append(status.toLowerCase(Locale.ROOT))
                        .append(": ")
                        .append(
                                viewing.optString(
                                        "property_name",
                                        "Property"
                                )
                        )
                        .append("\n\n");
            }
        }

        if (hasUnreadApplications) {
            text.append(
                    "There is an update to your rental application.\n\n"
            );
        }

        boolean messagesPending =
                hasUnreadChatRequests || hasUnreadLandlordMessages;

        boolean viewingPending =
                hasUnreadViewingDecisions;

        if (text.length() == 0) {
            text.append("No new notifications.");
        }

        AlertDialog.Builder dialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.notifications_title)
                        .setMessage(text.toString().trim())
                        .setNegativeButton(
                                R.string.btn_close,
                                (v, which) -> {
                                    markAlertsChecked();
                                    markMessagesChecked();
                                }
                        );

        if (messagesPending) {

            dialog.setPositiveButton(
                    "Open Messages",
                    (v, which) -> {

                        markAlertsChecked();
                        markMessagesChecked();

                        startActivity(
                                new Intent(
                                        HomeActivity.this,
                                        UserSelectionActivity.class
                                )
                        );
                    }
            );

        } else if (viewingPending) {

            dialog.setPositiveButton(
                    R.string.btn_my_viewings,
                    (v, which) -> {

                        markAlertsChecked();

                        startActivity(
                                new Intent(
                                        HomeActivity.this,
                                        ViewingsActivity.class
                                )
                        );
                    }
            );
        }

        if (viewingPending && messagesPending) {

            dialog.setNeutralButton(
                    R.string.btn_my_viewings,
                    (v, which) -> {

                        markViewingDecisionsSeen();

                        startActivity(
                                new Intent(
                                        HomeActivity.this,
                                        ViewingsActivity.class
                                )
                        );
                    }
            );

        } else if (hasUnreadApplications) {

            dialog.setNeutralButton(
                    R.string.btn_other_notifications,
                    (v, which) -> {

                        markAlertsChecked();

                        startActivity(
                                new Intent(
                                        HomeActivity.this,
                                        NotificationsActivity.class
                                )
                        );
                    }
            );
        }

        dialog.show();
    }

    private JSONArray fetchRows(
            String endpoint,
            String token
    ) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    endpoint,
                    "GET",
                    token
            );

            int code = connection.getResponseCode();

            String response =
                    SupabaseHelper.readResponse(connection);

            if (code >= 200 && code < 300) {
                return new JSONArray(response);
            }

        } catch (Exception ignored) {

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    private void setupPersonalizedGreeting() {

        TextView tvHomeGreeting =
                findViewById(R.id.tvHomeGreeting);

        if (tvHomeGreeting == null) {
            return;
        }

        SharedPreferences prefs =
                getSharedPreferences(
                        "iKhayaSession",
                        MODE_PRIVATE
                );

        String fullName =
                prefs.getString("USER_NAME", "");

        if (fullName.isEmpty()) {
            fullName = prefs.getString("user_name", "");
        }

        if (!fullName.trim().isEmpty()) {

            String firstName =
                    fullName.trim().split("\\s+")[0];

            tvHomeGreeting.setText(
                    getString(
                            R.string.home_greeting_personalized,
                            firstName
                    )
            );

        } else {
            tvHomeGreeting.setText(
                    R.string.home_greeting_default
            );
        }
    }

    private void refreshCachedUserLocation() {
        boolean fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) return;

        try {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return;
            android.location.Location best = null;
            for (String provider : lm.getProviders(true)) {
                android.location.Location loc = lm.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getAccuracy() < best.getAccuracy())) {
                    best = loc;
                }
            }
            if (best != null) {
                getSharedPreferences("iKhayaUserPrefs", MODE_PRIVATE).edit()
                        .putFloat("USER_LAST_LAT", (float) best.getLatitude())
                        .putFloat("USER_LAST_LON", (float) best.getLongitude())
                        .apply();
            }
        } catch (SecurityException ignored) {}
    }

    private void refreshReturningUserBookingHistory() {
        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);
        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) return;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String encUid = SupabaseHelper.encode(userId);
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/rental_applications?select=listing_id,listings(property_type,location,price)&tenant_uid=eq."
                                + encUid + "&order=created_at.desc&limit=1",
                        "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (arr.length() > 0) {
                        JSONObject listing = arr.getJSONObject(0).optJSONObject("listings");
                        if (listing != null) {
                            getSharedPreferences("iKhayaUserPrefs", MODE_PRIVATE).edit()
                                    .putString("LAST_BOOKED_TYPE", listing.optString("property_type", ""))
                                    .putString("LAST_BOOKED_LOC", listing.optString("location", ""))
                                    .putFloat("LAST_BOOKED_PRICE", (float) listing.optDouble("price", 0.0))
                                    .apply();
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        if ((lat1 == 0.0 && lon1 == 0.0) || (lat2 == 0.0 && lon2 == 0.0)) return -1.0;
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double a = (sinLat * sinLat)
                + (Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLon * sinLon);
        a = Math.max(0.0, Math.min(1.0, a));
        return 2.0 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private double computeListingScore(JSONObject item, double userLat, double userLon,
                                       String lastSearch, String lastBookedType,
                                       String lastBookedLoc, double lastBookedPrice,
                                       double minFilter, double maxFilter) {
        double score = 0.0;
        double lat = item.optDouble("latitude", 0.0);
        double lon = item.optDouble("longitude", 0.0);
        double distKm = calculateDistanceKm(userLat, userLon, lat, lon);

        // 1. Proximity boost + distance decay (moves distant properties to the bottom)
        if (distKm >= 0) {
            if (distKm <= 5.0) score += 6.0;
            else if (distKm <= 15.0) score += 3.5;
            else if (distKm <= 30.0) score += 1.5;
            score -= Math.min(distKm * 0.08, 12.0);
            try { item.put("computed_distance_km", distKm); } catch (Exception ignored) {}
        } else {
            score -= 2.0; // Unpinned listings rank below verified nearby ones
        }

        // 2. Returning-user purchase / application history match
        String loc = item.optString("location", "").toLowerCase(java.util.Locale.ROOT);
        String type = item.optString("property_type", "").toLowerCase(java.util.Locale.ROOT);
        double price = item.optDouble("price", 0.0);

        if (!lastBookedType.isEmpty() && type.contains(lastBookedType)) score += 2.5;
        if (!lastBookedLoc.isEmpty() && loc.contains(lastBookedLoc)) score += 2.5;
        if (lastBookedPrice > 0 && price > 0 && Math.abs(price - lastBookedPrice) <= lastBookedPrice * 0.25) {
            score += 2.0;
        }

        // 3. Recent search & price filter match
        if (!lastSearch.isEmpty() && loc.contains(lastSearch)) score += 2.0;
        if (maxFilter > 0 && price >= minFilter && price <= maxFilter) score += 1.0;

        return score;
    }

    private void sortListingsByRelevance(List<JSONObject> listings) {
        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        SharedPreferences userPrefs = getSharedPreferences("iKhayaUserPrefs", MODE_PRIVATE);
        
        String userLocation = prefs.getString("USER_LOCATION", prefs.getString("user_location", "")).toLowerCase(java.util.Locale.ROOT);
        double minBudget = prefs.getFloat("USER_MIN_BUDGET", 0f);
        double maxBudget = prefs.getFloat("USER_MAX_BUDGET", prefs.getFloat("USER_BUDGET", prefs.getFloat("user_budget", 999999f)));
        
        double uLat = userPrefs.getFloat("USER_LAST_LAT", 0f);
        double uLon = userPrefs.getFloat("USER_LAST_LON", 0f);
        
        String lastBookedType = userPrefs.getString("LAST_BOOKED_TYPE", "").toLowerCase(java.util.Locale.ROOT);
        String lastBookedLoc = userPrefs.getString("LAST_BOOKED_LOC", "").toLowerCase(java.util.Locale.ROOT);
        double lastBookedPrice = userPrefs.getFloat("LAST_BOOKED_PRICE", 0f);

        listings.sort((a, b) -> {
            double scoreA = computeListingScore(a, uLat, uLon, userLocation, lastBookedType, lastBookedLoc, lastBookedPrice, minBudget, maxBudget);
            double scoreB = computeListingScore(b, uLat, uLon, userLocation, lastBookedType, lastBookedLoc, lastBookedPrice, minBudget, maxBudget);

            if (scoreA != scoreB) {
                return Double.compare(scoreB, scoreA);
            }
            
            double dKA = a.optDouble("computed_distance_km", -1.0);
            double dKB = b.optDouble("computed_distance_km", -1.0);
            if (dKA >= 0 && dKB >= 0 && dKA != dKB) {
                return Double.compare(dKA, dKB);
            }

            String dateA = a.optString("created_at", "");
            String dateB = b.optString("created_at", "");
            return dateB.compareTo(dateA);
        });
    }

    private void loadHomeFeedFromSupabase() {
        if (loadingOverlayHelper != null) loadingOverlayHelper.beginTask();
        
        JSONArray cachedArray = SessionCacheManager.getJsonArraySafe("home_feed_listings");
        if (cachedArray != null) {
            try {
                List<JSONObject> cachedList = new ArrayList<>();
                for (int i = 0; i < cachedArray.length(); i++) {
                    JSONObject item = cachedArray.optJSONObject(i);
                    if (item != null) cachedList.add(item);
                }
                sortListingsByRelevance(cachedList);
                List<JSONObject> popList = new ArrayList<>();
                List<JSONObject> expList = new ArrayList<>();
                for (int i = 0; i < cachedList.size(); i++) {
                    if (i % 2 == 0) popList.add(cachedList.get(i));
                    else expList.add(cachedList.get(i));
                }
                if (expList.isEmpty()) expList.addAll(popList);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (rvPopularPlaces != null) rvPopularPlaces.setAdapter(new ListingCarouselAdapter(popList));
                    if (rvExploreNewArea != null) rvExploreNewArea.setAdapter(new ListingCarouselAdapter(expList));
                });
            } catch (Exception ignored) {}
        }

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (accessToken == null
                || accessToken.isEmpty()) {
            if (loadingOverlayHelper != null) loadingOverlayHelper.endTask();
            runOnUiThread(() -> {
                if (swipeRefreshHome != null && swipeRefreshHome.isRefreshing()) {
                    swipeRefreshHome.setRefreshing(false);
                }
            });
            return;
        }

        new Thread(() -> {

            HttpURLConnection conn = null;

            try {
                String userId = SupabaseHelper.getUserId(this);
                if (userId != null && !userId.isEmpty()) {
                    try {
                        HttpURLConnection vConn = SupabaseHelper.createConnection(
                                "/rest/v1/viewings?select=listing_id,status,viewing_date&tenant_uid=eq." + SupabaseHelper.encode(userId),
                                "GET", accessToken);
                        if (vConn.getResponseCode() >= 200 && vConn.getResponseCode() < 300) {
                            JSONArray vArr = new JSONArray(SupabaseHelper.readResponse(vConn));
                            tenantViewingBadges.clear();
                            for (int i = 0; i < vArr.length(); i++) {
                                JSONObject v = vArr.getJSONObject(i);
                                String lid = v.optString("listing_id", "");
                                String vst = v.optString("status", "");
                                String vDate = v.optString("viewing_date", "");
                                if (!lid.isEmpty() && !WorkflowAutomationHelper.isViewingDateInPast(vDate)) {
                                    if ("Confirmed".equalsIgnoreCase(vst)) {
                                        tenantViewingBadges.put(lid, "Under Viewing");
                                    } else if (("Pending".equalsIgnoreCase(vst) || "Pending Host".equalsIgnoreCase(vst)) && !"Under Viewing".equals(tenantViewingBadges.get(lid))) {
                                        tenantViewingBadges.put(lid, "Pending Viewing");
                                    }
                                }
                            }
                        }
                        vConn.disconnect();
                    } catch (Exception ignored) {}
                }

                conn = SupabaseHelper.createConnection(
                        "/rest/v1/listings?select=*&status=eq.APPROVED&is_archived=eq.false&order=created_at.desc&limit=20",
                        "GET",
                        accessToken
                );

                int code = conn.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    SessionCacheManager.putJson("home_feed_listings", response, 120_000L);

                    JSONArray rawListings =
                            new JSONArray(response);

                    List<JSONObject> fullList =
                            new ArrayList<>();

                    for (int i = 0; i < rawListings.length(); i++) {
                        JSONObject item = rawListings.optJSONObject(i);
                        if (item != null) fullList.add(item);
                    }

                    sortListingsByRelevance(fullList);

                    List<JSONObject> popularList =
                            new ArrayList<>();

                    List<JSONObject> exploreList =
                            new ArrayList<>();

                    for (int i = 0; i < fullList.size(); i++) {

                        JSONObject item =
                                fullList.get(i);

                        if (i % 2 == 0) {
                            popularList.add(item);
                        } else {
                            exploreList.add(item);
                        }
                    }

                    if (exploreList.isEmpty()) {
                        exploreList.addAll(popularList);
                    }

                    runOnUiThread(() -> {

                        if (swipeRefreshHome != null && swipeRefreshHome.isRefreshing()) {
                            swipeRefreshHome.setRefreshing(false);
                        }

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (rvPopularPlaces != null) {
                            rvPopularPlaces.setAdapter(
                                    new ListingCarouselAdapter(
                                            popularList
                                    )
                            );
                        }

                        if (rvExploreNewArea != null) {
                            rvExploreNewArea.setAdapter(
                                    new ListingCarouselAdapter(
                                            exploreList
                                    )
                            );
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (swipeRefreshHome != null && swipeRefreshHome.isRefreshing()) {
                            swipeRefreshHome.setRefreshing(false);
                        }
                    });
                }

            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (swipeRefreshHome != null && swipeRefreshHome.isRefreshing()) {
                        swipeRefreshHome.setRefreshing(false);
                    }
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                if (loadingOverlayHelper != null) loadingOverlayHelper.endTask();
            }
        }).start();
    }

    private void setupBottomNavigation() {

        View navHome = findViewById(R.id.navHome);

        if (navHome != null) {
            navHome.setOnClickListener(v -> {

                View homeScroll =
                        findViewById(R.id.homeScrollView);

                if (homeScroll != null) {
                    homeScroll.scrollTo(0, 0);
                }
            });
        }

        View navFavorites =
                findViewById(R.id.navFavorites);

        if (navFavorites != null) {
            navFavorites.setOnClickListener(v ->
                    startActivity(
                            new Intent(
                                    HomeActivity.this,
                                    SavedListingsActivity.class
                            )
                    )
            );
        }

        View navViewings =
                findViewById(R.id.navViewings);

        if (navViewings != null) {
            navViewings.setOnClickListener(v -> {

                markViewingDecisionsSeen();

                startActivity(
                        new Intent(
                                HomeActivity.this,
                                ViewingsActivity.class
                        )
                );
            });
        }

        View navMessages =
                findViewById(R.id.navMessages);

        if (navMessages != null) {
            navMessages.setOnClickListener(v -> {

                markMessagesChecked();

                startActivity(
                        new Intent(
                                HomeActivity.this,
                                UserSelectionActivity.class
                        )
                );
            });
        }
    }

    private void openSearch(
            String query,
            String propertyType
    ) {
        Intent intent = new Intent(
                HomeActivity.this,
                SearchResultsActivity.class
        );

        intent.putExtra("SEARCH_QUERY", query);

        if (propertyType != null
                && !propertyType.isEmpty()) {

            intent.putExtra(
                    "PROPERTY_TYPE",
                    propertyType
            );
        }

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (loadingOverlayHelper != null) {
            loadingOverlayHelper.cleanup();
        }

        if (networkStatusMonitor != null) {
            networkStatusMonitor.cleanup();
        }

        notificationHandler.removeCallbacks(
                notificationPoll
        );

        notificationExecutor.shutdownNow();
        imageExecutor.shutdownNow();

        super.onDestroy();
    }

    private class ListingCarouselAdapter
            extends RecyclerView.Adapter<
            ListingCarouselAdapter.ListingViewHolder> {

        private final List<JSONObject> items;

        ListingCarouselAdapter(List<JSONObject> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ListingViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            float density =
                    getResources().getDisplayMetrics().density;

            com.google.android.material.card.MaterialCardView card =
                    UiCardHelper.createAppCard(HomeActivity.this, 16, 2);

            RecyclerView.LayoutParams cardParams =
                    new RecyclerView.LayoutParams(
                            Math.round(230f * density),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            cardParams.setMarginEnd(
                    Math.round(14f * density)
            );

            card.setLayoutParams(cardParams);
            card.setUseCompatPadding(true);

            LinearLayout root =
                    new LinearLayout(HomeActivity.this);

            root.setOrientation(LinearLayout.VERTICAL);

            root.setPadding(
                    Math.round(10f * density),
                    Math.round(10f * density),
                    Math.round(10f * density),
                    Math.round(14f * density)
            );

            ImageView image =
                    new ImageView(HomeActivity.this);

            LinearLayout.LayoutParams imgParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.round(130f * density)
                    );

            image.setLayoutParams(imgParams);

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            image.setBackgroundColor(
                    Color.parseColor("#EFEBE4")
            );

            root.addView(image);

            TextView title =
                    new TextView(HomeActivity.this);

            title.setTextSize(14.5f);
            title.setTextColor(Color.parseColor("#1F1614"));
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);

            LinearLayout.LayoutParams titleParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            titleParams.topMargin =
                    Math.round(8f * density);

            title.setLayoutParams(titleParams);
            root.addView(title);

            TextView location =
                    new TextView(HomeActivity.this);

            location.setTextSize(12f);
            location.setTextColor(
                    Color.parseColor("#8C7A70")
            );
            location.setSingleLine(true);

            root.addView(location);

            TextView price =
                    new TextView(HomeActivity.this);

            price.setTextSize(14f);
            price.setTextColor(
                    Color.parseColor("#BA5536")
            );
            price.setTypeface(null, Typeface.BOLD);

            LinearLayout.LayoutParams priceParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            priceParams.topMargin =
                    Math.round(6f * density);

            price.setLayoutParams(priceParams);
            root.addView(price);

            TextView availabilityBadge = new TextView(HomeActivity.this);
            availabilityBadge.setTextSize(10.5f);
            availabilityBadge.setTypeface(null, Typeface.BOLD);
            availabilityBadge.setPadding(
                    Math.round(8f * density),
                    Math.round(3f * density),
                    Math.round(8f * density),
                    Math.round(3f * density)
            );
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            badgeParams.topMargin = Math.round(6f * density);
            availabilityBadge.setLayoutParams(badgeParams);
            availabilityBadge.setVisibility(View.GONE);
            root.addView(availabilityBadge);

            card.addView(root);

            return new ListingViewHolder(
                    card,
                    image,
                    title,
                    location,
                    price,
                    availabilityBadge
            );
        }

        @Override
        public void onBindViewHolder(
                @NonNull ListingViewHolder holder,
                int position
        ) {
            JSONObject item = items.get(position);

            String id = item.optString("id", "");

            String name = item.optString(
                    "property_name",
                    getString(R.string.accommodation)
            );

            String location = item.optString(
                    "location",
                    getString(R.string.default_country_location)
            );

            String imageUrl = item.optString("image_url", "");

            holder.tvTitle.setText(name);

            holder.tvLocation.setText(
                    getString(
                            R.string.listing_location_pin,
                            location
                    )
            );

            holder.tvPrice.setText(PriceUtils.formatDisplayPrice(item));

            float density = getResources().getDisplayMetrics().density;
            String avail = item.optString("availability", "Available");
            String fallback = tenantViewingBadges.get(id);
            if (("Available".equalsIgnoreCase(avail) || avail.isEmpty()) && fallback != null) {
                avail = fallback;
            }

            if ("Under Viewing".equalsIgnoreCase(avail)) {
                holder.tvAvailabilityBadge.setText("Under Viewing");
                holder.tvAvailabilityBadge.setTextColor(Color.parseColor("#1D4ED8"));
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(Color.parseColor("#DBEAFE"));
                bg.setCornerRadius(Math.round(8f * density));
                holder.tvAvailabilityBadge.setBackground(bg);
                holder.tvAvailabilityBadge.setVisibility(View.VISIBLE);
            } else if ("Pending Viewing".equalsIgnoreCase(avail)) {
                holder.tvAvailabilityBadge.setText("Pending Viewing");
                holder.tvAvailabilityBadge.setTextColor(Color.parseColor("#B85032"));
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(Color.parseColor("#FDEEE9"));
                bg.setCornerRadius(Math.round(8f * density));
                holder.tvAvailabilityBadge.setBackground(bg);
                holder.tvAvailabilityBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvAvailabilityBadge.setVisibility(View.GONE);
            }

            holder.ivImage.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );

            if (!imageUrl.isEmpty()) {
                loadImageAsync(
                        imageUrl,
                        holder.ivImage
                );
            }

            holder.itemView.setOnClickListener(v -> {

                Intent intent = new Intent(
                        HomeActivity.this,
                        PropertyDetailsActivity.class
                );

                intent.putExtra("LISTING_ID", id);

                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void loadImageAsync(
                String urlStr,
                ImageView imageView
        ) {
            imageView.setTag(urlStr);
            Bitmap cached = SessionCacheManager.getBitmap(urlStr);
            if (cached != null) {
                imageView.setImageBitmap(cached);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return;
            }
            
            imageExecutor.execute(() -> {

                HttpURLConnection conn = null;

                try {
                    conn = (HttpURLConnection)
                            new URL(urlStr).openConnection();

                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);

                    int res = conn.getResponseCode();

                    if (res >= 200 && res < 300) {

                        try (InputStream in = conn.getInputStream()) {

                            Bitmap bmp = SessionCacheManager.decodeSampledBitmap(in, 600, 400);

                            if (bmp != null) {
                                SessionCacheManager.putBitmap(urlStr, bmp);
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    if (urlStr.equals(imageView.getTag())) {
                                        imageView.setImageBitmap(bmp);
                                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    }
                                });
                            }
                        }
                    }

                } catch (Exception ignored) {

                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            });
        }

        class ListingViewHolder
                extends RecyclerView.ViewHolder {

            final ImageView ivImage;
            final TextView tvTitle;
            final TextView tvLocation;
            final TextView tvPrice;
            final TextView tvAvailabilityBadge;

            ListingViewHolder(
                    View itemView,
                    ImageView ivImage,
                    TextView tvTitle,
                    TextView tvLocation,
                    TextView tvPrice,
                    TextView tvAvailabilityBadge
            ) {
                super(itemView);

                this.ivImage = ivImage;
                this.tvTitle = tvTitle;
                this.tvLocation = tvLocation;
                this.tvPrice = tvPrice;
                this.tvAvailabilityBadge = tvAvailabilityBadge;
            }
        }
    }
}
