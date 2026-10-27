package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LandlordProfileActivity extends AppCompatActivity {

    private static final String TAG = "LandlordProfile";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView txtLandlordName, txtLandlordType, tvAvatarInitials;
    private TextView tvHeaderListingCount, tvMyListingsBadge, tvListingEnquiriesBadge, tvCurrentLanguage;
    private View viewNotificationDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_profile);

        View back = findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }

        txtLandlordName = findViewById(R.id.txtLandlordName);
        txtLandlordType = findViewById(R.id.txtLandlordType);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvHeaderListingCount = findViewById(R.id.tvHeaderListingCount);
        tvMyListingsBadge = findViewById(R.id.tvMyListingsBadge);
        tvListingEnquiriesBadge = findViewById(R.id.tvListingEnquiriesBadge);
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);
        viewNotificationDot = findViewById(R.id.viewNotificationDot);

        open(R.id.btnPersonalInfo, LandlordPersonalInformationActivity.class);
        open(R.id.btnChangeLanguage, LanguageSettingsActivity.class);
        open(R.id.btnMyListings, MyListingsActivity.class);
        open(R.id.btnEnquiries, UserSelectionActivity.class);
        open(R.id.btnNotifications, NotificationsActivity.class);
        open(R.id.btnChangePassword, ChangePasswordActivity.class);
        open(R.id.btnReportProblem, ReportProblemActivity.class);
        open(R.id.btnPaymentsReceived, PaymentsReceivedActivity.class);
        open(R.id.btnPaymentHistory, PaymentHistoryActivity.class);

        open(R.id.navDashboard, LandlordDashboardActivity.class);
        open(R.id.navListings, MyListingsActivity.class);
        open(R.id.navAdd, AddListingActivity.class);
        open(R.id.navMessages, UserSelectionActivity.class);

        Button logout = findViewById(R.id.btnLogout);
        if (logout != null) {
            logout.setOnClickListener(v -> logout());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDynamicData();
        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(getActiveLanguageDisplayName());
        }
    }

    private void loadDynamicData() {
        String savedName = getSharedPreferences("iKhayaSession", MODE_PRIVATE).getString("USER_NAME", "");
        if (txtLandlordName != null) {
            txtLandlordName.setText(savedName == null || savedName.trim().isEmpty() ? getString(R.string.landlord_name) : savedName.trim());
        }
        if (tvAvatarInitials != null) {
            tvAvatarInitials.setText(extractInitials(savedName));
        }

        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                // 1. Location & Verification
                String locationEndpoint = "/rest/v1/profiles?select=full_name,city,province,suburb&firebase_uid=eq." + userId + "&limit=1";
                HttpURLConnection conn = SupabaseHelper.createConnection(locationEndpoint, "GET", token);
                int code = conn.getResponseCode();
                String res = SupabaseHelper.readResponse(conn);
                conn.disconnect();

                String locationText = getString(R.string.default_verified_location);
                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(res);
                    if (arr.length() > 0) {
                        JSONObject profile = arr.getJSONObject(0);
                        String city = profile.optString("city", "");
                        if (city.isEmpty()) city = profile.optString("suburb", "");
                        String prov = profile.optString("province", "");
                        if (!city.isEmpty() && !prov.isEmpty()) {
                            locationText = city + ", " + prov + " • ID Verified";
                        } else if (!city.isEmpty()) {
                            locationText = city + " • ID Verified";
                        }
                    } else {
                        // Fallback to Users
                        String fallbackEndpoint = "/rest/v1/Users?select=firstName,surname,city,province,suburb&id=eq." + userId + "&limit=1";
                        conn = SupabaseHelper.createConnection(fallbackEndpoint, "GET", token);
                        code = conn.getResponseCode();
                        res = SupabaseHelper.readResponse(conn);
                        conn.disconnect();
                        if (code >= 200 && code < 300) {
                            JSONArray arr2 = new JSONArray(res);
                            if (arr2.length() > 0) {
                                JSONObject profile = arr2.getJSONObject(0);
                                String city = profile.optString("city", "");
                                if (city.isEmpty()) city = profile.optString("suburb", "");
                                String prov = profile.optString("province", "");
                                if (!city.isEmpty() && !prov.isEmpty()) {
                                    locationText = city + ", " + prov + " • ID Verified";
                                } else if (!city.isEmpty()) {
                                    locationText = city + " • ID Verified";
                                }
                            }
                        }
                    }
                }
                final String finalLoc = locationText;
                runOnUiThread(() -> {
                    if (txtLandlordType != null) txtLandlordType.setText(finalLoc);
                });

                // 2. Listing Count
                String listingsEndpoint = "/rest/v1/listings?select=id&landlord_uid=eq." + userId;
                conn = SupabaseHelper.createConnection(listingsEndpoint, "GET", token);
                code = conn.getResponseCode();
                res = SupabaseHelper.readResponse(conn);
                conn.disconnect();
                int listingCount = 0;
                if (code >= 200 && code < 300) {
                    listingCount = new JSONArray(res).length();
                }
                final int finalListingCount = listingCount;
                runOnUiThread(() -> {
                    if (tvHeaderListingCount != null) {
                        tvHeaderListingCount.setText(finalListingCount == 1 ? "1 Listing" : finalListingCount + " Listings");
                    }
                    if (tvMyListingsBadge != null) {
                        tvMyListingsBadge.setText(finalListingCount == 1 ? "1 Property" : finalListingCount + " Properties");
                    }
                });

                // 3. Enquiries Count
                int unreadEnquiries = 0;
                String enquiriesEndpoint = "/rest/v1/enquiries?select=id,status&landlord_uid=eq." + userId;
                conn = SupabaseHelper.createConnection(enquiriesEndpoint, "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    // Simple logic: if any exist we can count them, or filter by pending
                    unreadEnquiries += new JSONArray(SupabaseHelper.readResponse(conn)).length();
                }
                conn.disconnect();

                String chatReqEndpoint = "/rest/v1/chat_requests?select=id,status&receiver_id=eq." + userId + "&status=eq.pending";
                conn = SupabaseHelper.createConnection(chatReqEndpoint, "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    unreadEnquiries += new JSONArray(SupabaseHelper.readResponse(conn)).length();
                }
                conn.disconnect();
                
                final int finalUnread = unreadEnquiries;
                runOnUiThread(() -> {
                    if (tvListingEnquiriesBadge != null) {
                        tvListingEnquiriesBadge.setText(finalUnread > 0 ? finalUnread + " New" : getString(R.string.default_unread_count));
                    }
                });

                // 4. Notifications
                String notifEndpoint = "/rest/v1/notifications?select=id&user_id=eq." + userId + "&is_read=eq.false";
                conn = SupabaseHelper.createConnection(notifEndpoint, "GET", token);
                boolean hasNotif = false;
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    if (new JSONArray(SupabaseHelper.readResponse(conn)).length() > 0) hasNotif = true;
                }
                conn.disconnect();
                
                final boolean finalHasNotif = hasNotif || finalUnread > 0;
                runOnUiThread(() -> {
                    if (viewNotificationDot != null) {
                        viewNotificationDot.setVisibility(finalHasNotif ? View.VISIBLE : View.GONE);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading dynamic data", e);
            }
        });
    }

    private String extractInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "IK";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private String getActiveLanguageDisplayName() {
        String tag = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (tag == null || tag.isEmpty()) {
            tag = Locale.getDefault().getLanguage();
        }
        tag = tag.toLowerCase(Locale.ROOT);
        if (tag.startsWith("zu")) return "isiZulu";
        if (tag.startsWith("xh")) return "isiXhosa";
        if (tag.startsWith("st")) return "Sesotho";
        if (tag.startsWith("nso")) return "Sepedi";
        if (tag.startsWith("af")) return "Afrikaans";
        return getString(R.string.default_language_label);
    }

    private void open(int id, Class<?> destination) {
        View view = findViewById(id);
        if (view != null) {
            view.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, destination));
                } catch (Exception ignored) {}
            });
        }
    }

    private void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            Log.e(TAG, "Firebase sign-out failed", e);
        }

        try {
            SupabaseHelper.clearSession(this);
        } catch (Exception e) {
            Log.e(TAG, "Supabase sign-out failed", e);
        }

        getSharedPreferences("iKhayaSession", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Toast.makeText(
                this,
                R.string.logged_out_successfully,
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(this, OuterMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}