package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserSelectionActivity extends AppCompatActivity {

    private LinearLayout usersListContainer;
    private ProgressBar progressUsers;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentUserId;
    private String currentUserName;
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_selection);

        usersListContainer = findViewById(R.id.usersListContainer);
        progressUsers = findViewById(R.id.progressUsers);
        CardView cardCommunityChat = findViewById(R.id.cardCommunityChat);

        // Retrieve logged-in user details via SupabaseHelper and iKhayaSession
        accessToken = SupabaseHelper.getAccessToken(this);
        currentUserId = SupabaseHelper.getUserId(this);
        
        // Fallback for Developer Bypass if no one logged in
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = "guest_" + (System.currentTimeMillis() % 1000);
        }

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        currentUserName = prefs.getString("USER_NAME", "iKhaya User");

        // 1. Open Community Chat when clicked
        cardCommunityChat.setOnClickListener(v -> openChat("COMMUNITY", "🌍 iKhaya Community Group", true));

        // 2. Load all users from Supabase
        fetchAllUsers();
    }

    private void fetchAllUsers() {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Select all profiles - select=* is fault-tolerant for missing column names
                String endpoint = "/rest/v1/profiles?select=*";
                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray usersArray = new JSONArray(response);
                    runOnUiThread(() -> {
                        progressUsers.setVisibility(View.GONE);
                        populateUsersList(usersArray);
                    });
                } else {
                    runOnUiThread(() -> {
                        progressUsers.setVisibility(View.GONE);
                        Toast.makeText(this, "Could not load users (HTTP " + code + "): " + response, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressUsers.setVisibility(View.GONE);
                    Toast.makeText(this, "Network error loading users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void populateUsersList(JSONArray usersArray) {
        usersListContainer.removeAllViews();
        try {
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject userObj = usersArray.getJSONObject(i);
                
                // Fault-tolerant ID checking
                String userId = userObj.optString("firebase_uid", "");
                if (userId.isEmpty()) {
                    userId = userObj.optString("id", "");
                }
                
                // Skip showing the current user in their own contact list
                if (userId.isEmpty() || userId.equals(currentUserId)) {
                    continue;
                }

                // Fault-tolerant Name checking
                String fullName = userObj.optString("full_name", "");
                if (fullName.isEmpty()) fullName = userObj.optString("firstName", "");
                if (fullName.isEmpty()) fullName = userObj.optString("email", "User");
                
                String role = userObj.optString("role", "Member");

                addUserCard(userId, fullName, role);
            }
        } catch (Exception e) {
            // Error handling ignored
        }
    }

    private void addUserCard(String targetUserId, String fullName, String role) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);
        card.setRadius(32f);
        card.setCardElevation(6f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding(40, 36, 40, 36);

        TextView txtName = new TextView(this);
        txtName.setText(fullName);
        txtName.setTextSize(17f);
        txtName.setTypeface(null, Typeface.BOLD);
        txtName.setTextColor(Color.parseColor("#212529"));

        TextView txtRole = new TextView(this);
        txtRole.setText(role.toUpperCase() + " • Tap to message");
        txtRole.setTextSize(12f);
        txtRole.setTextColor(Color.parseColor("#6C757D"));

        innerLayout.addView(txtName);
        innerLayout.addView(txtRole);
        card.addView(innerLayout);

        card.setOnClickListener(v -> openChat(targetUserId, fullName, false));
        usersListContainer.addView(card);
    }

    private void openChat(String receiverId, String receiverName, boolean isCommunity) {
        Intent intent = new Intent(UserSelectionActivity.this, ChatActivity.class);
        intent.putExtra("RECEIVER_ID", receiverId);
        intent.putExtra("RECEIVER_NAME", receiverName);
        intent.putExtra("IS_COMMUNITY", isCommunity);
        intent.putExtra("SENDER_ID", currentUserId);
        intent.putExtra("SENDER_NAME", currentUserName);
        startActivity(intent);
    }
}