package com.example.ikhaya_accommodation_seeking_app;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminUsersActivity extends AppCompatActivity {

    private RecyclerView rvUserDirectory;
    private AdminUserAdapter userAdapter;

    private final List<AdminUser> masterUserList = new ArrayList<>();
    private final List<AdminUser> displayedUsers = new ArrayList<>();

    private EditText etSearchUsers;
    private Button btnFilterAll, btnFilterTenants, btnFilterLandlords, btnFilterSuspended;
    private String currentFilter = "ALL";
    private String currentAdminId = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);

        // Get the logged in admin's UID to prevent self-destruction
        currentAdminId = SupabaseHelper.getUserId(this);

        ImageButton btnBack = findViewById(R.id.btnBackToHub);
        btnBack.setOnClickListener(v -> finish());

        etSearchUsers = findViewById(R.id.etSearchUsers);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterTenants = findViewById(R.id.btnFilterTenants);
        btnFilterLandlords = findViewById(R.id.btnFilterLandlords);
        btnFilterSuspended = findViewById(R.id.btnFilterSuspended);

        rvUserDirectory = findViewById(R.id.rvUserDirectory);
        rvUserDirectory.setLayoutManager(new LinearLayoutManager(this));

        // Pass the current admin ID to the adapter
        userAdapter = new AdminUserAdapter(this, displayedUsers, currentAdminId);
        rvUserDirectory.setAdapter(userAdapter);

        setupFilters();
        setupSearch();
        loadAllUsers();
    }

    private void setupFilters() {
        btnFilterAll.setOnClickListener(v -> applyFilter("ALL", btnFilterAll));
        btnFilterTenants.setOnClickListener(v -> applyFilter("TENANT", btnFilterTenants));
        btnFilterLandlords.setOnClickListener(v -> applyFilter("LANDLORD", btnFilterLandlords));
        btnFilterSuspended.setOnClickListener(v -> applyFilter("SUSPENDED", btnFilterSuspended));
    }

    private void applyFilter(String filterType, Button selectedButton) {
        currentFilter = filterType;

        Button[] allButtons = {btnFilterAll, btnFilterTenants, btnFilterLandlords, btnFilterSuspended};
        for (Button btn : allButtons) {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9ECEF")));
            btn.setTextColor(Color.parseColor("#32325D"));
        }

        selectedButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5E72E4")));
        selectedButton.setTextColor(Color.WHITE);

        applySearchAndFilter(etSearchUsers.getText().toString());
    }

    private void setupSearch() {
        etSearchUsers.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearchAndFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applySearchAndFilter(String query) {
        String lowerQuery = query.toLowerCase().trim();
        displayedUsers.clear();

        AdminUser me = null;
        List<AdminUser> others = new ArrayList<>();

        for (AdminUser user : masterUserList) {
            boolean matchesSearch = user.getName().toLowerCase().contains(lowerQuery);
            boolean matchesFilter = false;

            switch (currentFilter) {
                case "ALL":
                    matchesFilter = true;
                    break;
                case "TENANT":
                    matchesFilter = "tenant".equalsIgnoreCase(user.getRole());
                    break;
                case "LANDLORD":
                    matchesFilter = "landlord".equalsIgnoreCase(user.getRole());
                    break;
                case "SUSPENDED":
                    matchesFilter = user.isSuspended();
                    break;
            }

            if (matchesSearch && matchesFilter) {
                // Partition the current admin from the rest of the list
                if (user.getUserId().equals(currentAdminId)) {
                    me = user;
                } else {
                    others.add(user);
                }
            }
        }

        // Always put the current admin at the very top if they match the filter criteria
        if (me != null) displayedUsers.add(me);
        displayedUsers.addAll(others);

        userAdapter.notifyDataSetChanged();
    }

    private void loadAllUsers() {
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/profiles?select=id,firebase_uid,full_name,role,is_suspended,identity_status,sa_id_number,id_document_url,verification_selfie_url,selfie_url&order=created_at.desc", "GET", token);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray profilesArr = new JSONArray(response);
                    List<AdminUser> fetchedUsers = new ArrayList<>();
                    Set<String> seenIds = new HashSet<>();

                    for (int i = 0; i < profilesArr.length(); i++) {
                        JSONObject obj = profilesArr.getJSONObject(i);
                        String profileId = obj.optString("id", "");
                        String uid = obj.optString("firebase_uid", "");
                        String name = obj.optString("full_name", "User");
                        String role = obj.optString("role", "tenant");
                        boolean suspended = obj.optBoolean("is_suspended", false);
                        String identityStatus = obj.optString("identity_status", "UNVERIFIED");
                        String saId = obj.optString("sa_id_number", "");
                        String idDocUrl = obj.optString("id_document_url", "");
                        String selfieUrl = obj.optString("verification_selfie_url", "");
                        if (selfieUrl.isEmpty() || "null".equalsIgnoreCase(selfieUrl)) {
                            selfieUrl = obj.optString("selfie_url", "");
                        }

                        if (!uid.isEmpty() && !seenIds.contains(uid)) {
                            seenIds.add(uid);
                            fetchedUsers.add(new AdminUser(profileId, uid, name, role, suspended, identityStatus, saId, idDocUrl, selfieUrl));
                        }
                    }

                    runOnUiThread(() -> {
                        masterUserList.clear();
                        masterUserList.addAll(fetchedUsers);
                        applySearchAndFilter(etSearchUsers.getText().toString());
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_loading_users_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void showLandlordIdReviewDialog(AdminUser user) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_admin_verify_landlord);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvName = dialog.findViewById(R.id.tvDialogLandlordName);
        TextView tvSaId = dialog.findViewById(R.id.tvDialogSaId);
        Button btnViewId = dialog.findViewById(R.id.btnDialogViewId);
        Button btnViewSelfie = dialog.findViewById(R.id.btnDialogViewSelfie);
        Button btnApprove = dialog.findViewById(R.id.btnDialogApproveLandlord);

        if (tvName != null) tvName.setText(getString(R.string.label_landlord_name_prefix, user.getName()));
        if (tvSaId != null) tvSaId.setText(getString(R.string.label_sa_id_prefix, user.getSaIdNumber().isEmpty() ? "Not Provided" : user.getSaIdNumber()));

        if (btnViewId != null) {
            btnViewId.setOnClickListener(v -> openDocumentViewer(user.getIdDocumentUrl()));
        }
        if (btnViewSelfie != null) {
            btnViewSelfie.setOnClickListener(v -> openDocumentViewer(user.getVerificationSelfieUrl()));
        }

        if (btnApprove != null) {
            btnApprove.setOnClickListener(v -> approveLandlordProfile(user, dialog));
        }

        dialog.show();
    }

    private void openDocumentViewer(String url) {
        if (url == null || url.trim().isEmpty() || "null".equalsIgnoreCase(url.trim())) {
            Toast.makeText(this, "No document or selfie uploaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        String cleanUrl = url.trim();

        // 1. Ensure it has the full Supabase public storage base URL if only the relative path was stored
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = SupabaseHelper.getPublicStorageUrl("property-images", cleanUrl);
        }

        // 2. Display directly in an in-app dialog (instant, doesn't rely on external phone apps)
        Dialog previewDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        layout.setGravity(Gravity.CENTER);

        // Close button
        Button btnClose = new Button(this);
        btnClose.setText("Close Preview");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setBackgroundColor(Color.parseColor("#BA5536"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnParams.bottomMargin = 16;
        btnClose.setLayoutParams(btnParams);
        btnClose.setOnClickListener(v -> previewDialog.dismiss());

        // Image view
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        layout.addView(btnClose);
        layout.addView(imageView);
        previewDialog.setContentView(layout);

        String finalUrl = cleanUrl;
        executor.execute(() -> {
            try {
                InputStream in = new URL(finalUrl).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)));
                        previewDialog.dismiss();
                    } catch (Exception ex) {
                        Toast.makeText(AdminUsersActivity.this, "Could not open preview.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        previewDialog.show();
    }

    private void approveLandlordProfile(AdminUser user, Dialog dialog) {
        String token = SupabaseHelper.getAccessToken(this);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("identity_status", "VERIFIED");

                String targetId = user.getProfileId().isEmpty() ? user.getUserId() : user.getProfileId();
                String endpoint = "/rest/v1/profiles?id=eq." + SupabaseHelper.encode(targetId);
                SupabaseHelper.patch(endpoint, body, token);

                if (user.getUserId() != null && !user.getUserId().isEmpty()) {
                    SupabaseHelper.createNotification(
                            user.getUserId(),
                            "Identity Verified!",
                            "Your Landlord identity verification has been approved. You can now post listings.",
                            token
                    );
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, "Landlord approved successfully!", Toast.LENGTH_SHORT).show();
                    user.setIdentityStatus("VERIFIED");
                    dialog.dismiss();
                    loadAllUsers();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to verify landlord: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}