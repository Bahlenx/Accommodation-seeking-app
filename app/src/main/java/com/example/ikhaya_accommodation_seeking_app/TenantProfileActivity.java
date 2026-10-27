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

public class TenantProfileActivity extends AppCompatActivity {

    private static final String TAG = "TenantProfileActivity";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView txtUserName, txtUserType, tvAvatarInitials;
    private TextView tvHeaderLeaseCount, tvSavedCountBadge, tvApplicationsBadge, tvCurrentLanguage;
    private View viewNotificationDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenant_profile);

        View back = findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }

        txtUserName = findViewById(R.id.txtUserName);
        txtUserType = findViewById(R.id.txtUserType);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvHeaderLeaseCount = findViewById(R.id.tvHeaderLeaseCount);
        tvSavedCountBadge = findViewById(R.id.tvSavedCountBadge);
        tvApplicationsBadge = findViewById(R.id.tvApplicationsBadge);
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);
        viewNotificationDot = findViewById(R.id.viewNotificationDot);

        open(R.id.btnPersonalInfo, PersonalInformationActivity.class);
        open(R.id.btnChangeLanguage, LanguageSettingsActivity.class);
        open(R.id.btnSavedListings, SavedListingsActivity.class);
        open(R.id.btnApplications, ApplicationsActivity.class);
        open(R.id.btnNotifications, NotificationsActivity.class);
        open(R.id.btnChangePassword, ChangePasswordActivity.class);
        open(R.id.btnReportProblem, ReportProblemActivity.class);
        open(R.id.btnPaymentHistory, PaymentHistoryActivity.class);

        open(R.id.navHome, HomeActivity.class);
        open(R.id.navFavorites, SavedListingsActivity.class);
        open(R.id.navViewings, ViewingsActivity.class);
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
        if (txtUserName != null) {
            txtUserName.setText(savedName == null || savedName.trim().isEmpty() ? getString(R.string.tenant_name) : savedName.trim());
        }
        if (tvAvatarInitials != null) {
            tvAvatarInitials.setText(extractInitials(savedName));
        }

        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            return;
        }

        // 1. Saved Count (From SharedPreferences or Network)
        int savedCount = getSharedPreferences("SavedListings", MODE_PRIVATE).getAll().size();
        if (tvSavedCountBadge != null) {
            tvSavedCountBadge.setText(savedCount + " Saved");
        }

        executor.execute(() -> {
            try {
                // 2. Location & Verification
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
                    if (txtUserType != null) txtUserType.setText(finalLoc);
                });

                // 3. Applications Count
                String appsEndpoint = "/rest/v1/rental_applications?select=status&tenant_uid=eq." + userId;
                conn = SupabaseHelper.createConnection(appsEndpoint, "GET", token);
                code = conn.getResponseCode();
                res = SupabaseHelper.readResponse(conn);
                conn.disconnect();
                int pendingCount = 0;
                int approvedCount = 0;
                int totalCount = 0;
                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(res);
                    totalCount = arr.length();
                    for (int i = 0; i < arr.length(); i++) {
                        String status = arr.getJSONObject(i).optString("status", "").toUpperCase();
                        if ("PENDING".equals(status)) pendingCount++;
                        else if ("APPROVED".equals(status) || "ACTIVE".equals(status)) approvedCount++;
                    }
                }
                final int fPending = pendingCount;
                final int fApproved = approvedCount;
                final int fTotal = totalCount;
                runOnUiThread(() -> {
                    if (tvApplicationsBadge != null) {
                        tvApplicationsBadge.setText(fPending > 0 ? fPending + " Pending" : (fApproved > 0 ? fApproved + " Approved" : fTotal + " Total"));
                    }
                    if (tvHeaderLeaseCount != null) {
                        tvHeaderLeaseCount.setText(fApproved == 1 ? "1 Active Lease" : fApproved + " Active Leases");
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

                String chatReqEndpoint = "/rest/v1/chat_requests?select=id,status&receiver_id=eq." + userId + "&status=eq.pending";
                conn = SupabaseHelper.createConnection(chatReqEndpoint, "GET", token);
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    if (new JSONArray(SupabaseHelper.readResponse(conn)).length() > 0) hasNotif = true;
                }
                conn.disconnect();

                final boolean finalHasNotif = hasNotif;
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