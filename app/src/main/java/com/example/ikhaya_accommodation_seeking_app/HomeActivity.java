package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    private RecyclerView rvPopularPlaces;
    private RecyclerView rvExploreNewArea;

    private View notificationDot;
    private View messageDot;

    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());

    private volatile boolean notificationLoading = false;
    private JSONArray latestMessages;
    private JSONArray latestApplications;
    private JSONArray latestChatRequests;
    private String notificationUserId;

    private final Runnable notificationPoll = new Runnable() {
        @Override
        public void run() {
            refreshNotifications();
            notificationHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 1. Inset handling: Pad root for status bar, dynamically offset floating bottom nav
        View homeRoot = findViewById(R.id.homeRoot);
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);
        }

        if (homeRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(homeRoot, (v, insets) -> {
                Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                v.setPadding(0, statusBar.top, 0, 0);

                if (floatingBottomNav != null && floatingBottomNav.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                    CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                    int baseMarginPx = (int) (16 * getResources().getDisplayMetrics().density);
                    lp.bottomMargin = navBar.bottom + baseMarginPx;
                    floatingBottomNav.setLayoutParams(lp);
                    floatingBottomNav.setPadding(0, 0, 0, 0);
                }

                return WindowInsetsCompat.CONSUMED;
            });
        }

        // 2. Horizontal Carousels Initialization
        rvPopularPlaces = findViewById(R.id.rvPopularPlaces);
        rvExploreNewArea = findViewById(R.id.rvExploreNewArea);

        if (rvPopularPlaces != null) {
            rvPopularPlaces.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            );
        }

        if (rvExploreNewArea != null) {
            rvExploreNewArea.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            );
        }

        notificationDot = findViewById(R.id.notificationDot);
        messageDot = findViewById(R.id.messageDot);

        if (notificationDot != null) notificationDot.setVisibility(View.GONE);
        if (messageDot != null) messageDot.setVisibility(View.GONE);

        setupPersonalizedGreeting();

        // 3. Header Action Buttons
        View btnNotificationsHome = findViewById(R.id.btnNotificationsHome);
        if (btnNotificationsHome != null) {
            btnNotificationsHome.setOnClickListener(v -> {
                markAlertsChecked();
                startActivity(new Intent(HomeActivity.this, NotificationsActivity.class));
            });
        }

        View btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    startActivity(new Intent(HomeActivity.this, TenantProfileActivity.class))
            );
        }

        // 4. Search and Filter triggers
        EditText searchAccommodation = findViewById(R.id.etSearchPlace);

        if (searchAccommodation != null) {
            searchAccommodation.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    String query = searchAccommodation.getText().toString().trim();
                    openSearch(query, null);
                    return true;
                }
                return false;
            });
        }

        setupBottomNavigation();
        loadHomeFeedFromSupabase();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupPersonalizedGreeting();
        notificationHandler.removeCallbacks(notificationPoll);
        notificationHandler.post(notificationPoll);
    }

    @Override
    protected void onPause() {
        notificationHandler.removeCallbacks(notificationPoll);
        super.onPause();
    }

    private void refreshNotifications() {
        if (notificationLoading || notificationExecutor.isShutdown()) return;

        String uid = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (uid == null || uid.isEmpty() || token == null || token.isEmpty()) {
            if (notificationDot != null) notificationDot.setVisibility(View.GONE);
            if (messageDot != null) messageDot.setVisibility(View.GONE);
            return;
        }

        notificationLoading = true;

        notificationExecutor.execute(() -> {
            try {
                String encodedUid = SupabaseHelper.encode(uid);

                JSONArray messages = fetchRows(
                        "/rest/v1/messages?select=id&receiver_id=eq." + encodedUid + "&order=created_at.desc&limit=1000",
                        token
                );

                JSONArray applications = fetchRows(
                        "/rest/v1/rental_applications?select=id,status&tenant_uid=eq." + encodedUid + "&status=in.(APPROVED,DECLINED,approved,declined)&limit=1000",
                        token
                );

                JSONArray chatRequests = fetchRows(
                        "/rest/v1/chat_requests?select=id,status&receiver_id=eq." + encodedUid + "&status=eq.pending&limit=1000",
                        token
                );

                boolean unreadMessages = messages != null && TenantNotificationState.hasNew(this, uid, "messages", messages);
                boolean unreadApplications = applications != null && TenantNotificationState.hasNew(this, uid, "applications", applications);
                boolean unreadChatRequests = chatRequests != null && TenantNotificationState.hasNew(this, uid, "chat_requests", chatRequests);
                boolean unreadAlerts = unreadApplications || unreadChatRequests;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    notificationUserId = uid;
                    if (messages != null) latestMessages = messages;
                    if (applications != null) latestApplications = applications;
                    if (chatRequests != null) latestChatRequests = chatRequests;

                    if (messageDot != null) {
                        messageDot.setVisibility(unreadMessages ? View.VISIBLE : View.GONE);
                    }
                    if (notificationDot != null) {
                        notificationDot.setVisibility(unreadAlerts ? View.VISIBLE : View.GONE);
                    }
                });

            } catch (Exception ignored) {
            } finally {
                notificationLoading = false;
            }
        });
    }

    private void markAlertsChecked() {
        String uid = notificationUserId;
        if (uid == null || uid.isEmpty()) return;

        if (latestApplications != null) {
            TenantNotificationState.markSeen(this, uid, "applications", latestApplications);
        }
        if (latestChatRequests != null) {
            TenantNotificationState.markSeen(this, uid, "chat_requests", latestChatRequests);
        }
        if (notificationDot != null) {
            notificationDot.setVisibility(View.GONE);
        }
    }

    private void markMessagesChecked() {
        String uid = notificationUserId;
        if (uid == null || uid.isEmpty()) return;

        if (latestMessages != null) {
            TenantNotificationState.markSeen(this, uid, "messages", latestMessages);
        }
        if (messageDot != null) {
            messageDot.setVisibility(View.GONE);
        }
    }

    private JSONArray fetchRows(String endpoint, String token) {
        HttpURLConnection connection = null;
        try {
            connection = SupabaseHelper.createConnection(endpoint, "GET", token);
            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);
            if (code >= 200 && code < 300) {
                return new JSONArray(response);
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private void setupPersonalizedGreeting() {
        TextView tvHomeGreeting = findViewById(R.id.tvHomeGreeting);
        if (tvHomeGreeting == null) return;

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String fullName = prefs.getString("USER_NAME", "");
        if (fullName.isEmpty()) {
            fullName = prefs.getString("user_name", "");
        }

        if (!fullName.trim().isEmpty()) {
            String firstName = fullName.trim().split("\\s+")[0];
            tvHomeGreeting.setText(getString(R.string.home_greeting_personalized, firstName));
        } else {
            tvHomeGreeting.setText(R.string.home_greeting_default);
        }
    }

    private void loadHomeFeedFromSupabase() {
        String accessToken = SupabaseHelper.getAccessToken(this);
        if (accessToken == null || accessToken.isEmpty()) return;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                // Filter feed to only show APPROVED listings
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/listings?select=*&status=eq.APPROVED&order=created_at.desc&limit=20",
                        "GET",
                        accessToken
                );

                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray rawListings = new JSONArray(response);
                    List<JSONObject> popularList = new ArrayList<>();
                    List<JSONObject> exploreList = new ArrayList<>();

                    for (int i = 0; i < rawListings.length(); i++) {
                        JSONObject item = rawListings.optJSONObject(i);
                        if (item == null) continue;

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
                        if (isFinishing() || isDestroyed()) return;

                        if (rvPopularPlaces != null) {
                            rvPopularPlaces.setAdapter(new ListingCarouselAdapter(popularList));
                        }
                        if (rvExploreNewArea != null) {
                            rvExploreNewArea.setAdapter(new ListingCarouselAdapter(exploreList));
                        }
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void setupBottomNavigation() {
        View navHome = findViewById(R.id.navHome);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                View homeScroll = findViewById(R.id.homeScrollView);
                if (homeScroll != null) homeScroll.scrollTo(0, 0);
            });
        }

        View navFavorites = findViewById(R.id.navFavorites);
        if (navFavorites != null) {
            navFavorites.setOnClickListener(v ->
                    startActivity(new Intent(HomeActivity.this, SavedListingsActivity.class))
            );
        }

        View navViewings = findViewById(R.id.navViewings);
        if (navViewings != null) {
            navViewings.setOnClickListener(v ->
                    startActivity(new Intent(HomeActivity.this, ViewingsActivity.class))
            );
        }

        View navMessages = findViewById(R.id.navMessages);
        if (navMessages != null) {
            navMessages.setOnClickListener(v -> {
                markMessagesChecked();
                startActivity(new Intent(HomeActivity.this, UserSelectionActivity.class));
            });
        }
    }

    private void openSearch(String query, String propertyType) {
        Intent intent = new Intent(HomeActivity.this, SearchResultsActivity.class);
        intent.putExtra("SEARCH_QUERY", query);
        if (propertyType != null && !propertyType.isEmpty()) {
            intent.putExtra("PROPERTY_TYPE", propertyType);
        }
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        notificationHandler.removeCallbacks(notificationPoll);
        notificationExecutor.shutdownNow();
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private class ListingCarouselAdapter extends RecyclerView.Adapter<ListingCarouselAdapter.ListingViewHolder> {

        private final List<JSONObject> items;

        ListingCarouselAdapter(List<JSONObject> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ListingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float density = getResources().getDisplayMetrics().density;

            CardView card = new CardView(HomeActivity.this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    Math.round(230 * density),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMarginEnd(Math.round(14 * density));
            card.setLayoutParams(cardParams);
            card.setRadius(Math.round(18 * density));
            card.setCardElevation(Math.round(2 * density));
            card.setUseCompatPadding(true);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout root = new LinearLayout(HomeActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(
                    Math.round(10 * density),
                    Math.round(10 * density),
                    Math.round(10 * density),
                    Math.round(14 * density)
            );

            ImageView image = new ImageView(HomeActivity.this);
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(130 * density)
            );
            image.setLayoutParams(imgParams);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.parseColor("#EFEBE4"));
            root.addView(image);

            TextView title = new TextView(HomeActivity.this);
            title.setTextSize(14.5f);
            title.setTextColor(Color.parseColor("#1F1614"));
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setSingleLine(true);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.topMargin = Math.round(8 * density);
            title.setLayoutParams(titleParams);
            root.addView(title);

            TextView location = new TextView(HomeActivity.this);
            location.setTextSize(12);
            location.setTextColor(Color.parseColor("#8C7A70"));
            location.setSingleLine(true);
            root.addView(location);

            TextView price = new TextView(HomeActivity.this);
            price.setTextSize(14);
            price.setTextColor(Color.parseColor("#BA5536"));
            price.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            priceParams.topMargin = Math.round(6 * density);
            price.setLayoutParams(priceParams);
            root.addView(price);

            card.addView(root);
            return new ListingViewHolder(card, image, title, location, price);
        }

        @Override
        public void onBindViewHolder(@NonNull ListingViewHolder holder, int position) {
            JSONObject item = items.get(position);
            String id = item.optString("id", "");
            String name = item.optString("property_name", getString(R.string.accommodation));
            String location = item.optString("location", "South Africa");
            String price = item.optString("price", "0");
            String imageUrl = item.optString("image_url", "");

            holder.tvTitle.setText(name);
            holder.tvLocation.setText(getString(R.string.listing_location_pin, location));
            holder.tvPrice.setText(getString(R.string.listing_price_month, price));

            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
            if (!imageUrl.isEmpty()) {
                loadImageAsync(imageUrl, holder.ivImage);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, PropertyDetailsActivity.class);
                intent.putExtra("LISTING_ID", id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void loadImageAsync(String urlStr, ImageView imageView) {
            imageExecutor.execute(() -> {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    int res = conn.getResponseCode();
                    if (res >= 200 && res < 300) {
                        try (InputStream in = conn.getInputStream()) {
                            Bitmap bmp = BitmapFactory.decodeStream(in);
                            if (bmp != null) {
                                runOnUiThread(() -> {
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

        class ListingViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvTitle;
            final TextView tvLocation;
            final TextView tvPrice;

            ListingViewHolder(View itemView, ImageView ivImage, TextView tvTitle, TextView tvLocation, TextView tvPrice) {
                super(itemView);
                this.ivImage = ivImage;
                this.tvTitle = tvTitle;
                this.tvLocation = tvLocation;
                this.tvPrice = tvPrice;
            }
        }
    }
}