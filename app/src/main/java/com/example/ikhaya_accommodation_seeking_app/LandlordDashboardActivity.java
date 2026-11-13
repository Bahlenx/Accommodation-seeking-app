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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LandlordDashboardActivity extends AppCompatActivity {

    private static final String TAG = "LandlordDashboard";

    // 2x2 Grid Metrics
    private TextView txtTotalListings;
    private TextView txtAvailableListings;
    private TextView txtPendingListings;
    private TextView txtUnreadMessagesCard;

    private TextView txtNoListings;
    private TextView txtWelcome;

    // Notification Badges & Dots
    private View txtNotificationBadge;
    private View landlordMessageDot;
    private View enquiriesCardDot;
    private View rentalApplicationsCardDot;
    private View viewingRequestsCardDot;

    private LinearLayout listingsContainer;

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
        NotificationHelper.startBackgroundPoller(this);

        setContentView(R.layout.activity_landlord_dashboard);

        // 1. Safe Insets handling for Floating Pill Nav
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
                        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                        int baseMarginPx = Math.round(18f * getResources().getDisplayMetrics().density);
                        lp.bottomMargin = navBar.bottom + baseMarginPx;
                        floatingBottomNav.setLayoutParams(lp);
                        floatingBottomNav.setPadding(0, 0, 0, 0);
                    }
                    return WindowInsetsCompat.CONSUMED;
                });
            }
        }

        // 2. Personalize Greeting
        txtWelcome = findViewById(R.id.txtWelcome);
        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        String userName = prefs.getString("USER_NAME", getString(R.string.role_landlord));
        if (txtWelcome != null) {
            String firstName = userName.trim().split("\\s+")[0];
            txtWelcome.setText(String.format(Locale.getDefault(), "%s, %s", getString(R.string.greeting_welcome), firstName));
        }

        // 3. Bind UI references
        txtTotalListings = findViewById(R.id.txtTotalListings);
        txtAvailableListings = findViewById(R.id.txtAvailableListings);
        txtPendingListings = findViewById(R.id.txtPendingListings);
        txtUnreadMessagesCard = findViewById(R.id.txtUnreadMessagesCard);
        txtNoListings = findViewById(R.id.txtNoListings);
        listingsContainer = findViewById(R.id.listingsContainer);

        txtNotificationBadge = findViewById(R.id.txtNotificationBadge);
        landlordMessageDot = findViewById(R.id.landlordMessageDot);
        enquiriesCardDot = findViewById(R.id.enquiriesCardDot);
        rentalApplicationsCardDot = findViewById(R.id.rentalApplicationsCardDot);
        viewingRequestsCardDot = findViewById(R.id.viewingRequestsCardDot);

        setupRootBackHandler();
        setupActionClickListeners();
        setupBottomNavigation();
    }

    private void setupRootBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            private long backPressedTime = 0;

            @Override
            public void handleOnBackPressed() {
                if (isTaskRoot()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - backPressedTime > 2000) {
                        Toast.makeText(LandlordDashboardActivity.this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
                        backPressedTime = currentTime;
                    } else {
                        moveTaskToBack(true);
                    }
                } else {
                    finish();
                }
            }
        });
    }

    private void setupActionClickListeners() {
        View btnNotificationsLandlord = findViewById(R.id.btnNotificationsLandlord);
        if (btnNotificationsLandlord != null) {
            btnNotificationsLandlord.setOnClickListener(v -> showLandlordNotifications());
        }

        View btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> startActivity(new Intent(this, LandlordProfileActivity.class)));
        }

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

        View cardViewingRequests = findViewById(R.id.cardViewingRequests);
        if (cardViewingRequests != null) {
            cardViewingRequests.setOnClickListener(v -> {
                Intent intent = new Intent(this, LandlordViewingsActivity.class);
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
    }

    private void setupBottomNavigation() {
        View navHome = findViewById(R.id.navHome);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                View scroll = findViewById(R.id.dashboardScroll);
                if (scroll != null) scroll.scrollTo(0, 0);
                refreshDashboardData();
                refreshEnquiryBadge();
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
        SupabaseHelper.verifyActiveSessionOrKick(this);
        dashboardVisible = true;

        View scroll = findViewById(R.id.dashboardScroll);
        if (scroll != null) scroll.post(scroll::requestLayout);

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
                        "/rest/v1/listings?select=*&landlord_uid=eq." + SupabaseHelper.encode(userId) + "&order=created_at.desc",
                        token
                );

                int total = 0;
                int available = 0;
                int pending = 0;

                for (int i = 0; i < listings.length(); i++) {
                    JSONObject listing = listings.optJSONObject(i);
                    if (listing == null) continue;

                    boolean isArchived = listing.optBoolean("is_archived", false);
                    String status = listing.optString("status", "").toUpperCase().trim();

                    // Exclude deleted or archived listings from active dashboard stats
                    if (isArchived || "DELETED".equals(status) || "REMOVED_BY_ADMIN".equals(status)) {
                        continue;
                    }

                    total++;

                    if ("APPROVED".equals(status) || "VERIFIED".equals(status)) {
                        available++;
                    } else if ("PENDING".equals(status) || "PENDING_VERIFICATION".equals(status)
                            || "SUSPENDED_FOR_REVERIFICATION".equals(status) || "CHANGES_PENDING_REVIEW".equals(status)) {
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

                    if (listingsContainer == null) return;
                    listingsContainer.removeAllViews();

                    if (fTotal == 0) {
                        if (txtNoListings != null) {
                            txtNoListings.setText(R.string.no_listings_yet);
                            txtNoListings.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    if (txtNoListings != null) {
                        txtNoListings.setVisibility(View.GONE);
                    }

                    for (int i = 0; i < listings.length(); i++) {
                        JSONObject listing = listings.optJSONObject(i);
                        if (listing != null) {
                            addListingCard(listing, token);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Could not load properties metrics", e);
            }
        });
    }

    private void addListingCard(JSONObject listing, String token) {
        if (listingsContainer == null) return;

        View card = LayoutInflater.from(this).inflate(R.layout.item_listing, listingsContainer, false);

        ImageView image = card.findViewById(R.id.imgListing);
        TextView name = card.findViewById(R.id.txtListingName);
        TextView location = card.findViewById(R.id.txtListingLocation);
        TextView price = card.findViewById(R.id.txtListingPrice);
        TextView rooms = card.findViewById(R.id.txtListingRooms);
        View editButton = card.findViewById(R.id.btnEditListing);
        View deleteButton = card.findViewById(R.id.btnDeleteListing);

        TextView tvBadge = card.findViewById(R.id.tvLandlordStatusBadge);
        LinearLayout layoutAlert = card.findViewById(R.id.layoutReverificationAlert);
        TextView tvReason = card.findViewById(R.id.tvReverificationReason);

        String listingId = listing.optString("id", "");
        String propertyName = firstValue(listing, "property_name", "title", "name");
        String propertyLocation = firstValue(listing, "location", "address", "area");
        String propertyPrice = firstValue(listing, "price", "monthly_price");
        String propertyRooms = firstValue(listing, "rooms", "bedrooms", "room_count");
        String status = listing.optString("status", "PENDING_VERIFICATION");
        String reverificationReason = listing.optString("reverification_reason", "");
        String rejectionReason = listing.optString("rejection_reason", "");

        if (tvBadge != null) {
            switch (status.toUpperCase()) {
                case "SUSPENDED_FOR_REVERIFICATION":
                    tvBadge.setText("SUSPENDED");
                    tvBadge.setBackgroundColor(Color.parseColor("#DC3545"));
                    tvBadge.setTextColor(Color.WHITE);
                    if (layoutAlert != null && tvReason != null) {
                        layoutAlert.setVisibility(View.VISIBLE);
                        tvReason.setText("Action Required: " + (!reverificationReason.isEmpty() ? reverificationReason : "Address or authority proof updated. Re-verification required."));
                    }
                    break;

                case "CHANGES_PENDING_REVIEW":
                    tvBadge.setText("EDITS PENDING");
                    tvBadge.setBackgroundColor(Color.parseColor("#2CA8FF"));
                    tvBadge.setTextColor(Color.WHITE);
                    if (layoutAlert != null) layoutAlert.setVisibility(View.GONE);
                    break;

                case "PENDING_VERIFICATION":
                case "PENDING":
                    tvBadge.setText("PENDING APPROVAL");
                    tvBadge.setBackgroundColor(Color.parseColor("#FFC107"));
                    tvBadge.setTextColor(Color.BLACK);
                    if (layoutAlert != null) layoutAlert.setVisibility(View.GONE);
                    break;

                case "APPROVED":
                case "VERIFIED":
                    tvBadge.setText("ACTIVE");
                    tvBadge.setBackgroundColor(Color.parseColor("#28A745"));
                    tvBadge.setTextColor(Color.WHITE);
                    if (layoutAlert != null) layoutAlert.setVisibility(View.GONE);
                    break;

                case "REJECTED":
                default:
                    tvBadge.setText("REJECTED");
                    tvBadge.setBackgroundColor(Color.parseColor("#6C757D"));
                    tvBadge.setTextColor(Color.WHITE);
                    if (layoutAlert != null && tvReason != null) {
                        layoutAlert.setVisibility(View.VISIBLE);
                        tvReason.setText("Rejection Reason: " + (!rejectionReason.isEmpty() ? rejectionReason : "Listing details require corrections."));
                    }
                    break;
            }
        }

        if (name != null) {
            name.setText(propertyName.isEmpty() ? getString(R.string.accommodation) : propertyName);
        }

        if (location != null) {
            location.setText(propertyLocation.isEmpty() ? getString(R.string.default_unknown_location) : propertyLocation);
        }

        if (price != null) {
            price.setText(propertyPrice.isEmpty() ? getString(R.string.label_price_not_provided) : PriceUtils.formatDisplayPrice(listing));
        }

        if (rooms != null) {
            rooms.setText(propertyRooms.isEmpty() ? getString(R.string.label_rooms_not_specified) : propertyRooms + " " + getString(R.string.label_rooms_count));
        }

        if (image != null) {
            image.setImageResource(android.R.drawable.ic_menu_gallery);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        if (editButton != null) editButton.setVisibility(View.GONE);
        if (deleteButton != null) deleteButton.setVisibility(View.GONE);

        if (!listingId.isEmpty() && image != null) {
            loadListingImage(listingId, token, image);
        }

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyListingsActivity.class);
            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);
            startActivity(intent);
        });

        listingsContainer.addView(card);
    }

    private void loadListingImage(String listingId, String token, ImageView imageView) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            HttpURLConnection imageConnection = null;
            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/listing_images?select=image_url&listing_id=eq." + SupabaseHelper.encode(listingId) + "&limit=1",
                        "GET",
                        token
                );

                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) return;

                JSONArray images = new JSONArray(response);
                if (images.length() == 0) return;

                String imageUrl = images.getJSONObject(0).optString("image_url", "");
                if (imageUrl.isEmpty()) return;

                imageConnection = (HttpURLConnection) new URL(imageUrl).openConnection();
                imageConnection.setConnectTimeout(10000);
                imageConnection.setReadTimeout(15000);

                try (InputStream stream = imageConnection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not load listing image", e);
            } finally {
                if (connection != null) connection.disconnect();
                if (imageConnection != null) imageConnection.disconnect();
            }
        });
    }

    private void showLandlordNotifications() {
        String landlordId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (landlordId == null || landlordId.isEmpty() || token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.please_log_in_again, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            try {
                JSONArray messages = fetchRows(
                        "/rest/v1/messages?select=id,sender_id,sender_name,sender_role,message_text&receiver_id=eq."
                                + SupabaseHelper.encode(landlordId) + "&order=created_at.desc&limit=1000",
                        token
                );

                SharedPreferences seenPrefs = getSharedPreferences("ikhaya_seen_messages", MODE_PRIVATE);
                String seenIds = seenPrefs.getString("seen_ids_" + landlordId, "");
                Set<String> knownIds = new HashSet<>();
                for (String id : seenIds.split("\\|")) {
                    if (!id.isEmpty()) knownIds.add(id);
                }

                JSONObject newestUnread = null;
                int unreadCount = 0;

                for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message == null) continue;
                    String id = message.optString("id", "");
                    if (id.isEmpty() || knownIds.contains(id)) continue;
                    unreadCount++;
                    if (newestUnread == null) newestUnread = message;
                }

                JSONObject latest = newestUnread;
                int count = unreadCount;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                            .setTitle(R.string.notifications_title)
                            .setNegativeButton(R.string.btn_close, null)
                            .setNeutralButton(R.string.btn_view_all_notifications, (d, w) ->
                                    startActivity(new Intent(this, NotificationsActivity.class))
                            );

                    if (latest != null) {
                        String senderName = latest.optString("sender_name", getString(R.string.label_resident)).trim();
                        if (senderName.isEmpty()) senderName = getString(R.string.label_resident);

                        String message = String.format(Locale.getDefault(), "%s %s.", getString(R.string.notif_new_message_from_resident_prefix), senderName);
                        if (count > 1) {
                            message += String.format(Locale.getDefault(), "\n\n%s %d %s", getString(R.string.notif_you_have_prefix), count, getString(R.string.notif_new_messages_total_suffix));
                        }
                        dialog.setMessage(message);
                        dialog.setPositiveButton(R.string.btn_open_messages, (d, w) ->
                                startActivity(new Intent(this, UserSelectionActivity.class))
                        );
                    } else {
                        dialog.setMessage(R.string.notif_no_new_direct_messages);
                    }
                    dialog.show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Could not load message notifications", e);
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
            setDot(viewingRequestsCardDot, false);
            return;
        }

        badgeLoading = true;

        executor.execute(() -> {
            int pendingEnquiries = 0;
            int pendingChatRequests = 0;
            int unreadDirectMessages = 0;
            boolean hasUnseenNotifications = false;
            boolean hasUnseenRentals = false;
            boolean hasUnseenViewings = false;

            String encodedId = SupabaseHelper.encode(userId);

            // A. Tenant Enquiries awaiting reply
            try {
                JSONArray rows = fetchRows("/rest/v1/enquiries?select=id,status&landlord_uid=eq." + encodedId + "&status=ilike.Pending", token);
                pendingEnquiries = rows.length();
            } catch (Exception ignored) {}

            // B. Pending Chat Requests from Residents
            try {
                JSONArray rows = fetchRows("/rest/v1/chat_requests?select=id,status&receiver_id=eq." + encodedId + "&status=eq.pending", token);
                pendingChatRequests = rows.length();
            } catch (Exception ignored) {}

            // C. Direct Messages received
            try {
                JSONArray rows = fetchRows("/rest/v1/messages?select=id&receiver_id=eq." + encodedId, token);
                SharedPreferences prefs = getSharedPreferences("ikhaya_seen_messages", MODE_PRIVATE);
                String seenStr = prefs.getString("seen_ids_" + userId, "");
                for (int i = 0; i < rows.length(); i++) {
                    String msgId = rows.getJSONObject(i).optString("id", "");
                    if (!msgId.isEmpty() && !seenStr.contains(msgId)) {
                        unreadDirectMessages++;
                    }
                }
            } catch (Exception ignored) {}

            // D. General System Notifications
            try {
                JSONArray rows = fetchRows("/rest/v1/notifications?select=id&user_uid=eq." + encodedId, token);
                hasUnseenNotifications = rows.length() > 0;
            } catch (Exception ignored) {}

            // E. Rental Applications pending approval
            try {
                JSONArray rows = fetchRows("/rest/v1/rental_applications?select=id&landlord_uid=eq." + encodedId + "&status=eq.PENDING", token);
                hasUnseenRentals = rows.length() > 0;
            } catch (Exception ignored) {}

            // F. Viewing requests
            try {
                JSONArray rows = fetchRows("/rest/v1/viewings?select=id&landlord_uid=eq." + encodedId + "&status=eq." + SupabaseHelper.encode("Pending Host"), token);
                hasUnseenViewings = rows.length() > 0;
            } catch (Exception ignored) {}

            // Total items needing response = unread direct messages
            boolean showBellDot = hasUnseenNotifications || (unreadDirectMessages > 0) || (pendingChatRequests > 0) || (pendingEnquiries > 0) || hasUnseenRentals || hasUnseenViewings;

            final int finalChatCount = unreadDirectMessages;
            final boolean fShowBell = showBellDot;
            final boolean fShowEnquiries = pendingEnquiries > 0;
            final boolean fShowRentals = hasUnseenRentals;
            final boolean fShowViewings = hasUnseenViewings;
            final boolean fShowMessages = finalChatCount > 0;

            badgeLoading = false;

            runOnUiThread(() -> {
                if (!dashboardVisible || isFinishing() || isDestroyed()) return;

                setDot(txtNotificationBadge, fShowBell);
                setDot(landlordMessageDot, fShowMessages);
                setDot(enquiriesCardDot, fShowEnquiries);
                setDot(rentalApplicationsCardDot, fShowRentals);
                setDot(viewingRequestsCardDot, fShowViewings);

                if (txtUnreadMessagesCard != null) {
                    txtUnreadMessagesCard.setText(String.valueOf(finalChatCount));
                }
            });
        });
    }

    private void setDot(View dot, boolean visible) {
        if (dot != null) dot.setVisibility(visible ? View.VISIBLE : View.GONE);
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
            return new JSONArray();
        } catch (Exception e) {
            return new JSONArray();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String firstValue(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        dashboardVisible = false;
        badgeHandler.removeCallbacks(badgePoll);
        executor.shutdownNow();
        super.onDestroy();
    }
}