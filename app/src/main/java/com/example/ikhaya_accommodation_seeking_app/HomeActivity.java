package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private RecyclerView rvPopularPlaces;
    private RecyclerView rvExploreNewArea;

    private EditText searchAccommodation;

    private View notificationDot;
    private View messageDot;

    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(3);

    private final ExecutorService notificationExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler notificationHandler =
            new Handler(Looper.getMainLooper());

    private volatile boolean notificationLoading = false;

    private JSONArray latestMessages;
    private JSONArray latestApplications;
    private JSONArray latestChatRequests;

    private String notificationUserId;

    private boolean homeVisible = false;

    private final Runnable notificationPoll = new Runnable() {
        @Override
        public void run() {
            if (!homeVisible) {
                return;
            }

            refreshNotifications();

            notificationHandler.postDelayed(
                    this,
                    10000
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            private long backPressedTime = 0;

            @Override
            public void handleOnBackPressed() {
                if (isTaskRoot()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - backPressedTime > 2000) {
                        Toast.makeText(HomeActivity.this, "Press back again to exit iKhaya", Toast.LENGTH_SHORT).show();
                        backPressedTime = currentTime;
                    } else {
                        moveTaskToBack(true);
                    }
                } else {
                    finish();
                }
            }
        });

        setupWindowInsets();
        setupRecyclerViews();
        setupNotificationViews();
        setupPersonalizedGreeting();
        setupHeaderButtons();
        setupSearch();
        setupBottomNavigation();

        loadHomeFeedFromSupabase();
    }

    private void setupWindowInsets() {
        View homeRoot =
                findViewById(R.id.homeRoot);

        View floatingBottomNav =
                findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);
        }

        if (homeRoot == null) {
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                homeRoot,
                (v, insets) -> {
                    Insets statusBar = insets.getInsets(
                            WindowInsetsCompat.Type.statusBars()
                    );

                    Insets navBar = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars()
                    );

                    v.setPadding(
                            0,
                            statusBar.top,
                            0,
                            0
                    );

                    if (floatingBottomNav != null
                            && floatingBottomNav.getLayoutParams()
                            instanceof CoordinatorLayout.LayoutParams) {

                        CoordinatorLayout.LayoutParams lp =
                                (CoordinatorLayout.LayoutParams)
                                        floatingBottomNav.getLayoutParams();

                        int baseMarginPx = dp(16);

                        lp.bottomMargin =
                                navBar.bottom + baseMarginPx;

                        floatingBottomNav.setLayoutParams(lp);

                        floatingBottomNav.setPadding(
                                0,
                                0,
                                0,
                                0
                        );
                    }

                    return WindowInsetsCompat.CONSUMED;
                }
        );
    }

    private void setupRecyclerViews() {
        rvPopularPlaces =
                findViewById(R.id.rvPopularPlaces);

        rvExploreNewArea =
                findViewById(R.id.rvExploreNewArea);

        if (rvPopularPlaces != null) {
            rvPopularPlaces.setLayoutManager(
                    new LinearLayoutManager(
                            this,
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );
        }

        if (rvExploreNewArea != null) {
            rvExploreNewArea.setLayoutManager(
                    new LinearLayoutManager(
                            this,
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );
        }
    }

    private void setupNotificationViews() {
        notificationDot =
                findViewById(R.id.notificationDot);

        messageDot =
                findViewById(R.id.messageDot);

        setDot(notificationDot, false);
        setDot(messageDot, false);
    }

    private void setupHeaderButtons() {
        View btnNotificationsHome =
                findViewById(R.id.btnNotificationsHome);

        if (btnNotificationsHome != null) {
            btnNotificationsHome.setOnClickListener(v -> {
                markAlertsChecked();

                startActivity(
                        new Intent(
                                HomeActivity.this,
                                NotificationsActivity.class
                        )
                );
            });
        }

        View btnProfile =
                findViewById(R.id.btnProfile);

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
    }

    private void setupSearch() {
        searchAccommodation =
                findViewById(R.id.etSearchPlace);

        View btnFilter =
                findViewById(R.id.btnSearchSubmit);

        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> {
                FilterBottomSheetDialog filterSheet =
                        new FilterBottomSheetDialog();

                filterSheet.show(
                        getSupportFragmentManager(),
                        "FilterBottomSheetDialog"
                );
            });
        }

        if (searchAccommodation == null) {
            return;
        }

        searchAccommodation.setSingleLine(true);

        searchAccommodation.setImeOptions(
                EditorInfo.IME_ACTION_SEARCH
        );

        searchAccommodation.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH
                            || actionId == EditorInfo.IME_ACTION_DONE) {

                        performSearch();
                        return true;
                    }

                    return false;
                }
        );

        searchAccommodation.setOnTouchListener(
                (v, event) -> {
                    if (event.getAction() != MotionEvent.ACTION_UP) {
                        return false;
                    }

                    android.graphics.drawable.Drawable[] drawables =
                            searchAccommodation.getCompoundDrawables();

                    android.graphics.drawable.Drawable rightDrawable =
                            drawables[2];

                    if (rightDrawable == null) {
                        return false;
                    }

                    int touchAreaStart =
                            searchAccommodation.getWidth()
                                    - searchAccommodation.getPaddingEnd()
                                    - rightDrawable.getBounds().width()
                                    - dp(12);

                    if (event.getX() >= touchAreaStart) {
                        performSearch();
                        return true;
                    }

                    return false;
                }
        );
    }

    private void performSearch() {
        String query = "";

        if (searchAccommodation != null) {
            query = searchAccommodation
                    .getText()
                    .toString()
                    .trim();
        }

        openSearch(query, null);
    }

    private void openSearch(
            String query,
            String propertyType
    ) {
        Intent intent = new Intent(
                HomeActivity.this,
                SearchResultsActivity.class
        );

        intent.putExtra(
                "SEARCH_QUERY",
                query == null ? "" : query.trim()
        );

        if (propertyType != null
                && !propertyType.trim().isEmpty()) {

            intent.putExtra(
                    "PROPERTY_TYPE",
                    propertyType.trim()
            );
        }

        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        homeVisible = true;

        setupPersonalizedGreeting();

        notificationHandler.removeCallbacks(
                notificationPoll
        );

        notificationHandler.post(
                notificationPoll
        );
    }

    @Override
    protected void onPause() {
        homeVisible = false;

        notificationHandler.removeCallbacks(
                notificationPoll
        );

        super.onPause();
    }

    private void refreshNotifications() {
        if (notificationLoading
                || notificationExecutor.isShutdown()) {
            return;
        }

        String uid =
                SupabaseHelper.getUserId(this);

        String token =
                SupabaseHelper.getAccessToken(this);

        if (uid == null || uid.isEmpty()
                || token == null || token.isEmpty()) {

            setDot(notificationDot, false);
            setDot(messageDot, false);

            return;
        }

        notificationLoading = true;

        notificationExecutor.execute(() -> {
            try {
                String encodedUid =
                        SupabaseHelper.encode(uid);

                JSONArray messages = fetchRows(
                        "/rest/v1/messages?select=id"
                                + "&receiver_id=eq."
                                + encodedUid
                                + "&order=created_at.desc"
                                + "&limit=1000",
                        token
                );

                JSONArray applications = fetchRows(
                        "/rest/v1/rental_applications?select=id,status"
                                + "&tenant_uid=eq."
                                + encodedUid
                                + "&status=in.(APPROVED,DECLINED,approved,declined)"
                                + "&limit=1000",
                        token
                );

                JSONArray chatRequests = fetchRows(
                        "/rest/v1/chat_requests?select=id,status"
                                + "&receiver_id=eq."
                                + encodedUid
                                + "&status=eq.pending"
                                + "&limit=1000",
                        token
                );

                boolean unreadMessages =
                        messages != null
                                && TenantNotificationState.hasNew(
                                getApplicationContext(),
                                uid,
                                "messages",
                                messages
                        );

                boolean unreadApplications =
                        applications != null
                                && TenantNotificationState.hasNew(
                                getApplicationContext(),
                                uid,
                                "applications",
                                applications
                        );

                boolean unreadChatRequests =
                        chatRequests != null
                                && TenantNotificationState.hasNew(
                                getApplicationContext(),
                                uid,
                                "chat_requests",
                                chatRequests
                        );

                boolean unreadAlerts =
                        unreadApplications
                                || unreadChatRequests;

                runOnUiThread(() -> {
                    if (!homeVisible
                            || isFinishing()
                            || isDestroyed()) {
                        return;
                    }

                    notificationUserId = uid;

                    if (messages != null) {
                        latestMessages = messages;
                        setDot(
                                messageDot,
                                unreadMessages
                        );
                    }

                    if (applications != null) {
                        latestApplications = applications;
                    }

                    if (chatRequests != null) {
                        latestChatRequests = chatRequests;
                    }

                    if (applications != null
                            && chatRequests != null) {

                        setDot(
                                notificationDot,
                                unreadAlerts
                        );
                    }
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Notification refresh failed",
                        e
                );

            } finally {
                notificationLoading = false;
            }
        });
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

        setDot(notificationDot, false);
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

        setDot(messageDot, false);
    }

    private void setDot(
            View dot,
            boolean visible
    ) {
        if (dot != null) {
            dot.setVisibility(
                    visible
                            ? View.VISIBLE
                            : View.GONE
            );
        }
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

            Log.e(
                    TAG,
                    "Supabase HTTP " + code + ": " + response
            );

        } catch (Exception e) {
            Log.e(TAG, "Supabase request failed", e);

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
            fullName =
                    prefs.getString("user_name", "");
        }

        if (!fullName.trim().isEmpty()) {
            String firstName =
                    fullName.trim().split("\\s+")[0];

            tvHomeGreeting.setText(
                    "Hey, " + firstName
                            + "!\nTell us where you want to stay"
            );

        } else {
            tvHomeGreeting.setText(
                    R.string.home_greeting_default
            );
        }
    }

    private void loadHomeFeedFromSupabase() {
        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (accessToken == null
                || accessToken.isEmpty()) {
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/listings?select=*"
                                + "&order=created_at.desc"
                                + "&limit=20",
                        "GET",
                        accessToken
                );

                int code = conn.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray rawListings =
                            new JSONArray(response);

                    List<JSONObject> popularList =
                            new ArrayList<>();

                    List<JSONObject> exploreList =
                            new ArrayList<>();

                    for (int i = 0; i < rawListings.length(); i++) {
                        JSONObject item =
                                rawListings.optJSONObject(i);

                        if (item == null) {
                            continue;
                        }

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
                    Log.e(
                            TAG,
                            "Home feed HTTP "
                                    + code
                                    + ": "
                                    + response
                    );
                }

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Could not load home feed",
                        e
                );

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void setupBottomNavigation() {
        View navHome =
                findViewById(R.id.navHome);

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
            navViewings.setOnClickListener(v ->
                    startActivity(
                            new Intent(
                                    HomeActivity.this,
                                    ViewingsActivity.class
                            )
                    )
            );
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

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Override
    protected void onDestroy() {
        homeVisible = false;

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

        ListingCarouselAdapter(
                List<JSONObject> items
        ) {
            this.items = items;
        }

        @NonNull
        @Override
        public ListingViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            CardView card =
                    new CardView(HomeActivity.this);

            RecyclerView.LayoutParams cardParams =
                    new RecyclerView.LayoutParams(
                            dp(230),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            cardParams.setMarginEnd(dp(14));

            card.setLayoutParams(cardParams);
            card.setRadius(dp(18));
            card.setCardElevation(dp(2));
            card.setUseCompatPadding(true);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout root =
                    new LinearLayout(HomeActivity.this);

            root.setOrientation(LinearLayout.VERTICAL);

            root.setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(14)
            );

            ImageView image =
                    new ImageView(HomeActivity.this);

            image.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(130)
                    )
            );

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
            title.setTextColor(
                    Color.parseColor("#1F1614")
            );

            title.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );

            title.setSingleLine(true);

            LinearLayout.LayoutParams titleParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            titleParams.topMargin = dp(8);

            title.setLayoutParams(titleParams);
            root.addView(title);

            TextView location =
                    new TextView(HomeActivity.this);

            location.setTextSize(12);
            location.setTextColor(
                    Color.parseColor("#8C7A70")
            );

            location.setSingleLine(true);
            root.addView(location);

            TextView price =
                    new TextView(HomeActivity.this);

            price.setTextSize(14);
            price.setTextColor(
                    Color.parseColor("#BA5536")
            );

            price.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );

            LinearLayout.LayoutParams priceParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            priceParams.topMargin = dp(6);

            price.setLayoutParams(priceParams);
            root.addView(price);

            card.addView(root);

            return new ListingViewHolder(
                    card,
                    image,
                    title,
                    location,
                    price
            );
        }

        @Override
        public void onBindViewHolder(
                @NonNull ListingViewHolder holder,
                int position
        ) {
            JSONObject item = items.get(position);

            String id =
                    item.optString("id", "");

            String name =
                    item.optString(
                            "property_name",
                            "Accommodation"
                    );

            String location =
                    item.optString(
                            "location",
                            "South Africa"
                    );

            String price =
                    item.optString("price", "0");

            String imageUrl =
                    item.optString("image_url", "");

            holder.tvTitle.setText(name);

            holder.tvLocation.setText(
                    "📍 " + location
            );

            holder.tvPrice.setText(
                    "R " + price + " / mo"
            );

            holder.ivImage.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );

            holder.ivImage.setTag(imageUrl);

            if (!imageUrl.isEmpty()) {
                loadImageAsync(
                        imageUrl,
                        holder.ivImage
                );
            }

            holder.itemView.setOnClickListener(v -> {
                if (id.isEmpty()) {
                    Toast.makeText(
                            HomeActivity.this,
                            "Property unavailable.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                Intent intent = new Intent(
                        HomeActivity.this,
                        PropertyDetailsActivity.class
                );

                intent.putExtra(
                        "LISTING_ID",
                        id
                );

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
            if (imageExecutor.isShutdown()) {
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
                        try (InputStream in =
                                     conn.getInputStream()) {

                            Bitmap bmp =
                                    BitmapFactory.decodeStream(in);

                            if (bmp != null) {
                                runOnUiThread(() -> {
                                    if (isFinishing()
                                            || isDestroyed()) {
                                        return;
                                    }

                                    Object tag =
                                            imageView.getTag();

                                    if (urlStr.equals(tag)) {
                                        imageView.setImageBitmap(bmp);

                                        imageView.setScaleType(
                                                ImageView.ScaleType.CENTER_CROP
                                        );
                                    }
                                });
                            }
                        }
                    }

                } catch (Exception e) {
                    Log.w(
                            TAG,
                            "Could not load accommodation image",
                            e
                    );

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

            ListingViewHolder(
                    View itemView,
                    ImageView ivImage,
                    TextView tvTitle,
                    TextView tvLocation,
                    TextView tvPrice
            ) {
                super(itemView);

                this.ivImage = ivImage;
                this.tvTitle = tvTitle;
                this.tvLocation = tvLocation;
                this.tvPrice = tvPrice;
            }
        }
    }
}
