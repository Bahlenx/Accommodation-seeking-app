
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LandlordDashboardActivity extends AppCompatActivity {

    private static final String TAG = "LandlordDashboard";

    private TextView txtTotalListings;
    private TextView txtAvailableListings;
    private TextView txtPendingListings;
    private TextView txtNoListings;
    private TextView txtWelcome;

    private View txtNotificationBadge;
    private View landlordMessageDot;
    private View enquiriesCardDot;
    private View rentalApplicationsCardDot;

    private LinearLayout listingsContainer;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    private final Handler badgeHandler =
            new Handler(Looper.getMainLooper());

    private volatile boolean badgeLoading = false;
    private boolean dashboardVisible = false;

    private final Runnable badgePoll = new Runnable() {
        @Override
        public void run() {
            if (!dashboardVisible) {
                return;
            }

            refreshEnquiryBadge();
            badgeHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_dashboard);

        txtTotalListings = findViewById(R.id.txtTotalListings);
        txtAvailableListings = findViewById(R.id.txtAvailableListings);
        txtPendingListings = findViewById(R.id.txtPendingListings);
        txtNoListings = findViewById(R.id.txtNoListings);
        txtWelcome = findViewById(R.id.txtWelcome);

        txtNotificationBadge =
                findViewById(R.id.txtNotificationBadge);

        landlordMessageDot =
                findViewById(R.id.landlordMessageDot);

        enquiriesCardDot =
                findViewById(R.id.enquiriesCardDot);

        rentalApplicationsCardDot =
                findViewById(R.id.rentalApplicationsCardDot);

        listingsContainer =
                findViewById(R.id.listingsContainer);

        SharedPreferences prefs =
                getSharedPreferences("iKhayaSession", MODE_PRIVATE);

        String userName = prefs.getString(
                "USER_NAME",
                "Landlord"
        );

        if (txtWelcome != null) {
            txtWelcome.setText("Welcome, " + userName);
        }

        View btnBack = findViewById(R.id.btnBack);
        View btnProfile = findViewById(R.id.btnProfile);
        View btnAddListing = findViewById(R.id.btnAddListing);
        View btnLandlordChat = findViewById(R.id.btnLandlordChat);
        View btnNotificationsLandlord =
                findViewById(R.id.btnNotificationsLandlord);

        View cardPendingApplications =
                findViewById(R.id.cardPendingApplications);

        View cardRentalApplications =
                findViewById(R.id.cardRentalApplications);

        View navHome = findViewById(R.id.navHome);
        View navListings = findViewById(R.id.navListings);
        View navAdd = findViewById(R.id.navAdd);
        View navProfile = findViewById(R.id.navProfile);

        if (btnBack != null) {
            if (isTaskRoot()) {
                btnBack.setVisibility(View.GONE);
            } else {
                btnBack.setOnClickListener(v -> finish());
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            private long backPressedTime = 0;
            @Override
            public void handleOnBackPressed() {
                if (isTaskRoot()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - backPressedTime > 2000) {
                        Toast.makeText(LandlordDashboardActivity.this, "Press back again to exit iKhaya", Toast.LENGTH_SHORT).show();
                        backPressedTime = currentTime;
                    } else {
                        moveTaskToBack(true);
                    }
                } else {
                    finish();
                }
            }
        });

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    startActivity(new Intent(
                            this,
                            LandlordProfileActivity.class
                    ))
            );
        }

        if (btnAddListing != null) {
            btnAddListing.setOnClickListener(v ->
                    startActivity(new Intent(
                            this,
                            AddListingActivity.class
                    ))
            );
        }

        if (btnLandlordChat != null) {
            btnLandlordChat.setOnClickListener(v ->
                    startActivity(new Intent(
                            this,
                            UserSelectionActivity.class
                    ))
            );
        }

        if (btnNotificationsLandlord != null) {
            btnNotificationsLandlord.setOnClickListener(v ->
                    showLandlordNotifications()
            );
        }

        if (cardPendingApplications != null) {
            cardPendingApplications.setOnClickListener(v -> {
                Intent intent = new Intent(
                        this,
                        EnquiriesActivity.class
                );

                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }

        if (cardRentalApplications != null) {
            cardRentalApplications.setOnClickListener(v -> {
                Intent intent = new Intent(
                        this,
                        ApplicationsActivity.class
                );

                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                View scroll = findViewById(R.id.dashboardScroll);

                if (scroll != null) {
                    scroll.scrollTo(0, 0);
                }

                refreshDashboardData();
            });
        }

        if (navListings != null) {
            navListings.setOnClickListener(v ->
                    startActivity(new Intent(
                            this,
                            MyListingsActivity.class
                    ))
            );
        }

        if (navAdd != null) {
            navAdd.setOnClickListener(v ->
                    startActivity(new Intent(
                            this,
                            AddListingActivity.class
                    ))
            );
        }

        if (navProfile != null) {
            navProfile.setOnClickListener(v ->
                    startActivity(new Intent(
                            this,
                            LandlordProfileActivity.class
                    ))
            );
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

        if (userId == null || userId.isEmpty()
                || token == null || token.isEmpty()) {
            showMessage("Please sign in again.");
            return;
        }

        if (txtNoListings != null) {
            txtNoListings.setText("Loading your listings...");
            txtNoListings.setVisibility(View.VISIBLE);
        }

        executor.execute(() -> {
            try {
                JSONArray listings = fetchRows(
                        "/rest/v1/listings?select=*"
                                + "&landlord_uid=eq."
                                + SupabaseHelper.encode(userId)
                                + "&order=created_at.desc",
                        token
                );

                int total = listings.length();
                int available = 0;
                int pending = 0;

                for (int i = 0; i < listings.length(); i++) {
                    JSONObject listing = listings.optJSONObject(i);

                    if (listing == null) {
                        continue;
                    }

                    String availability =
                            listing.optString("availability", "");

                    String status =
                            listing.optString("status", "");

                    if ("available".equalsIgnoreCase(availability)
                            || "available".equalsIgnoreCase(status)) {
                        available++;
                    }

                    if ("pending".equalsIgnoreCase(availability)
                            || "pending".equalsIgnoreCase(status)) {
                        pending++;
                    }
                }

                final int totalCount = total;
                final int availableCount = available;
                final int pendingCount = pending;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (txtTotalListings != null) {
                        txtTotalListings.setText(
                                String.valueOf(totalCount)
                        );
                    }

                    if (txtAvailableListings != null) {
                        txtAvailableListings.setText(
                                String.valueOf(availableCount)
                        );
                    }

                    if (txtPendingListings != null) {
                        txtPendingListings.setText(
                                String.valueOf(pendingCount)
                        );
                    }

                    if (listingsContainer == null) {
                        return;
                    }

                    listingsContainer.removeAllViews();

                    if (totalCount == 0) {
                        if (txtNoListings != null) {
                            txtNoListings.setText("No listings yet.");
                            txtNoListings.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    if (txtNoListings != null) {
                        txtNoListings.setVisibility(View.GONE);
                    }

                    for (int i = 0; i < listings.length(); i++) {
                        JSONObject listing =
                                listings.optJSONObject(i);

                        if (listing != null) {
                            addListingCard(listing, token);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Could not load listings", e);
                showMessage("Could not load your listings.");
            }
        });
    }

    private void addListingCard(
            JSONObject listing,
            String token
    ) {
        if (listingsContainer == null) {
            return;
        }

        View card = LayoutInflater.from(this).inflate(
                R.layout.item_listing,
                listingsContainer,
                false
        );

        ImageView image =
                card.findViewById(R.id.imgListing);

        TextView name =
                card.findViewById(R.id.txtListingName);

        TextView location =
                card.findViewById(R.id.txtListingLocation);

        TextView price =
                card.findViewById(R.id.txtListingPrice);

        TextView rooms =
                card.findViewById(R.id.txtListingRooms);

        Button editButton =
                card.findViewById(R.id.btnEditListing);

        Button deleteButton =
                card.findViewById(R.id.btnDeleteListing);

        String listingId = listing.optString("id", "");

        String propertyName = firstValue(
                listing,
                "property_name",
                "title",
                "name"
        );

        String propertyLocation = firstValue(
                listing,
                "location",
                "address",
                "area"
        );

        String propertyPrice = firstValue(
                listing,
                "price",
                "monthly_price"
        );

        String propertyRooms = firstValue(
                listing,
                "rooms",
                "bedrooms",
                "room_count"
        );

        if (name != null) {
            name.setText(
                    propertyName.isEmpty()
                            ? "Property"
                            : propertyName
            );
        }

        if (location != null) {
            location.setText(
                    propertyLocation.isEmpty()
                            ? "Location not provided"
                            : propertyLocation
            );
        }

        if (price != null) {
            price.setText(
                    propertyPrice.isEmpty()
                            ? "Price not provided"
                            : "R " + propertyPrice + " / month"
            );
        }

        if (rooms != null) {
            rooms.setText(
                    propertyRooms.isEmpty()
                            ? "Rooms not specified"
                            : propertyRooms + " rooms"
            );
        }

        if (image != null) {
            image.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );
        }

        if (editButton != null) {
            editButton.setVisibility(View.GONE);
        }

        if (deleteButton != null) {
            deleteButton.setVisibility(View.GONE);
        }

        if (!listingId.isEmpty() && image != null) {
            loadListingImage(listingId, token, image);
        }

        card.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this,
                    MyListingsActivity.class
            );

            intent.putExtra("LISTING_ID", listingId);
            intent.putExtra("listingId", listingId);

            startActivity(intent);
        });

        listingsContainer.addView(card);
    }

    private void loadListingImage(
            String listingId,
            String token,
            ImageView imageView
    ) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            HttpURLConnection imageConnection = null;

            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/listing_images?select=image_url"
                                + "&listing_id=eq."
                                + SupabaseHelper.encode(listingId)
                                + "&limit=1",
                        "GET",
                        token
                );

                int code = connection.getResponseCode();
                String response =
                        SupabaseHelper.readResponse(connection);

                if (code < 200 || code >= 300) {
                    return;
                }

                JSONArray images = new JSONArray(response);

                if (images.length() == 0) {
                    return;
                }

                String imageUrl = images.getJSONObject(0)
                        .optString("image_url", "");

                if (imageUrl.isEmpty()) {
                    return;
                }

                imageConnection =
                        (HttpURLConnection)
                                new URL(imageUrl).openConnection();

                imageConnection.setConnectTimeout(10000);
                imageConnection.setReadTimeout(15000);

                try (InputStream stream =
                             imageConnection.getInputStream()) {

                    Bitmap bitmap =
                            BitmapFactory.decodeStream(stream);

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
                if (connection != null) {
                    connection.disconnect();
                }

                if (imageConnection != null) {
                    imageConnection.disconnect();
                }
            }
        });
    }

    private void showLandlordNotifications() {
        String landlordId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (landlordId == null || landlordId.isEmpty()
                || token == null || token.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        executor.execute(() -> {
            try {
                JSONArray messages = fetchRows(
                        "/rest/v1/messages"
                                + "?select=id,sender_id,sender_name,sender_role,message_text"
                                + "&receiver_id=eq."
                                + SupabaseHelper.encode(landlordId)
                                + "&order=created_at.desc"
                                + "&limit=1000",
                        token
                );

                SharedPreferences seenPrefs =
                        getSharedPreferences(
                                "ikhaya_seen_messages",
                                MODE_PRIVATE
                        );

                String seenIds = seenPrefs.getString(
                        "seen_ids_" + landlordId,
                        ""
                );

                Set<String> knownIds = new HashSet<>();

                for (String id : seenIds.split("\\|")) {
                    if (!id.isEmpty()) {
                        knownIds.add(id);
                    }
                }

                JSONObject newestUnread = null;
                int unreadCount = 0;

                for (int i = 0; i < messages.length(); i++) {
                    JSONObject message =
                            messages.optJSONObject(i);

                    if (message == null) {
                        continue;
                    }

                    String id = message.optString("id", "");

                    if (id.isEmpty() || knownIds.contains(id)) {
                        continue;
                    }

                    unreadCount++;

                    if (newestUnread == null) {
                        newestUnread = message;
                    }
                }

                JSONObject latest = newestUnread;
                int count = unreadCount;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    AlertDialog.Builder dialog =
                            new AlertDialog.Builder(this)
                                    .setTitle("Notifications")
                                    .setNegativeButton("Close", null)
                                    .setNeutralButton(
                                            "View all notifications",
                                            (d, w) ->
                                                    startActivity(new Intent(
                                                            this,
                                                            NotificationsActivity.class
                                                    ))
                                    );

                    if (latest != null) {
                        String senderName = latest.optString(
                                "sender_name",
                                "Resident"
                        ).trim();

                        if (senderName.isEmpty()) {
                            senderName = "Resident";
                        }

                        String message =
                                "You have a new message from resident "
                                        + senderName + ".";

                        if (count > 1) {
                            message += "\n\nYou have "
                                    + count
                                    + " new messages in total.";
                        }

                        dialog.setMessage(message);

                        dialog.setPositiveButton(
                                "Open Messages",
                                (d, w) ->
                                        startActivity(new Intent(
                                                this,
                                                UserSelectionActivity.class
                                        ))
                        );

                    } else {
                        dialog.setMessage(
                                "You have no new direct messages."
                        );
                    }

                    dialog.show();
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Could not load message notifications",
                        e
                );

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(
                                this,
                                "Could not load message notifications.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            }
        });
    }

    private void refreshEnquiryBadge() {
        if (badgeLoading || executor.isShutdown()) {
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || token == null || token.isEmpty()) {

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

            String encodedId = SupabaseHelper.encode(userId);

            try {
                JSONArray rows = fetchRows(
                        "/rest/v1/enquiries?select=id"
                                + "&landlord_uid=eq."
                                + encodedId,
                        token
                );

                unseenEnquiries =
                        LandlordNotificationState.hasUnseen(
                                getApplicationContext(),
                                "enquiries",
                                userId,
                                rows
                        );

            } catch (Exception e) {
                Log.w(TAG, "Enquiries check failed", e);
            }

            try {
                JSONArray rows = fetchRows(
                        "/rest/v1/chat_requests?select=id"
                                + "&receiver_id=eq."
                                + encodedId
                                + "&status=eq.pending",
                        token
                );

                unseenChatRequests =
                        LandlordNotificationState.hasUnseen(
                                getApplicationContext(),
                                "chat",
                                userId,
                                rows
                        );

            } catch (Exception e) {
                Log.w(TAG, "Chat request check failed", e);
            }

            try {
                JSONArray rows = fetchRows(
                        "/rest/v1/notifications?select=id"
                                + "&user_uid=eq."
                                + encodedId,
                        token
                );

                unseenNotifications =
                        LandlordNotificationState.hasUnseen(
                                getApplicationContext(),
                                "notifications",
                                userId,
                                rows
                        );

            } catch (Exception e) {
                Log.w(TAG, "Notifications check failed", e);
            }

            try {
                JSONArray rows = fetchRows(
                        "/rest/v1/rental_applications?select=id"
                                + "&landlord_uid=eq."
                                + encodedId
                                + "&status=eq.PENDING",
                        token
                );

                unseenRentalApplications =
                        LandlordNotificationState.hasUnseen(
                                getApplicationContext(),
                                "rental_applications",
                                userId,
                                rows
                        );

            } catch (Exception e) {
                Log.w(TAG, "Applications check failed", e);
            }

            try {
                JSONArray rows = fetchRows(
                        "/rest/v1/messages?select=id"
                                + "&receiver_id=eq."
                                + encodedId
                                + "&order=created_at.desc"
                                + "&limit=1000",
                        token
                );

                unseenMessages =
                        LandlordNotificationState.hasUnseen(
                                getApplicationContext(),
                                "messages",
                                userId,
                                rows
                        );

                Log.d(
                        TAG,
                        "Incoming messages found: " + rows.length()
                );

            } catch (Exception e) {
                Log.e(TAG, "Messages check failed", e);
            }

            boolean showEnquiries = unseenEnquiries;
            boolean showRentals = unseenRentalApplications;
            boolean showMessages = unseenMessages;

            boolean showMainNotification =
                    unseenEnquiries
                            || unseenChatRequests
                            || unseenNotifications
                            || unseenRentalApplications
                            || unseenMessages;

            badgeLoading = false;

            runOnUiThread(() -> {
                if (!dashboardVisible
                        || isFinishing()
                        || isDestroyed()) {
                    return;
                }

                setDot(
                        txtNotificationBadge,
                        showMainNotification
                );

                setDot(
                        landlordMessageDot,
                        showMessages
                );

                setDot(
                        enquiriesCardDot,
                        showEnquiries
                );

                setDot(
                        rentalApplicationsCardDot,
                        showRentals
                );
            });
        });
    }

    private void setDot(View dot, boolean visible) {
        if (dot != null) {
            dot.setVisibility(
                    visible ? View.VISIBLE : View.GONE
            );
        }
    }

    private JSONArray fetchRows(
            String endpoint,
            String token
    ) throws Exception {
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

            if (code < 200 || code >= 300) {
                throw new java.io.IOException(
                        "HTTP " + code + ": " + response
                );
            }

            return new JSONArray(response);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String firstValue(
            JSONObject object,
            String... keys
    ) {
        for (String key : keys) {
            String value = object.optString(key, "");

            if (!value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }

        return "";
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        dashboardVisible = false;
        badgeHandler.removeCallbacks(badgePoll);
        executor.shutdownNow();
        super.onDestroy();
    }
}
