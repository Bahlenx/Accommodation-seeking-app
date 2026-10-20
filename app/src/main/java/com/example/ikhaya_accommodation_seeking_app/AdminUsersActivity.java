package com.example.ikhaya_accommodation_seeking_app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
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
                conn = SupabaseHelper.createConnection("/rest/v1/profiles?select=firebase_uid,full_name,role,is_suspended&order=created_at.desc", "GET", token);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray profilesArr = new JSONArray(response);
                    List<AdminUser> fetchedUsers = new ArrayList<>();
                    Set<String> seenIds = new HashSet<>();

                    for (int i = 0; i < profilesArr.length(); i++) {
                        JSONObject obj = profilesArr.getJSONObject(i);
                        String uid = obj.optString("firebase_uid", "");
                        String name = obj.optString("full_name", "User");
                        String role = obj.optString("role", "tenant");
                        boolean suspended = obj.optBoolean("is_suspended", false);

                        if (!uid.isEmpty() && !seenIds.contains(uid)) {
                            seenIds.add(uid);
                            fetchedUsers.add(new AdminUser(uid, name, role, suspended));
                        }
                    }

                    runOnUiThread(() -> {
                        masterUserList.clear();
                        masterUserList.addAll(fetchedUsers);
                        applySearchAndFilter(etSearchUsers.getText().toString());
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error loading users: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}