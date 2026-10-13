package com.example.ikhaya_accommodation_seeking_app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private LinearLayout searchResultsContainer;
    private ProgressBar progressUsers;
    private EditText editSearchUser;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentUserId;
    private String currentUserName;
    private String currentUserRole;
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_selection);

        usersListContainer = findViewById(R.id.usersListContainer);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        progressUsers = findViewById(R.id.progressUsers);
        editSearchUser = findViewById(R.id.editSearchUser);
        
        CardView cardCommunityChat = findViewById(R.id.cardCommunityChat);
        Button btnViewAlerts = findViewById(R.id.btnViewAlerts);
        Button btnSearchUser = findViewById(R.id.btnSearchUser);
        TextView txtRoleBadge = findViewById(R.id.txtRoleBadge);

        // Retrieve logged-in user details via SupabaseHelper and iKhayaSession
        accessToken = SupabaseHelper.getAccessToken(this);
        currentUserId = SupabaseHelper.getUserId(this);
        
        // Fallback for Developer Bypass if no one logged in
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = "guest_" + (System.currentTimeMillis() % 1000);
        }

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        currentUserName = prefs.getString("USER_NAME", "iKhaya User");
        currentUserRole = prefs.getString("USER_ROLE", "Member");
        
        txtRoleBadge.setText(currentUserRole.toUpperCase() + " \u25CF");

        // 1. Open Community Chat when clicked
        cardCommunityChat.setOnClickListener(v -> openChat("COMMUNITY", "🌍 iKhaya Community Group", true));

        // 2. Open Notifications/Alerts
        btnViewAlerts.setOnClickListener(v -> {
            startActivity(new Intent(UserSelectionActivity.this, NotificationsActivity.class));
        });
        
        // 3. Search Users
        btnSearchUser.setOnClickListener(v -> searchUsers(editSearchUser.getText().toString().trim()));

        // 4. Load Active Chat Requests
        loadActiveChats();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh active chats when returning from Notifications
        loadActiveChats();
    }

    private void loadActiveChats() {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Fetch accepted requests for this user
                String encId = SupabaseHelper.encode(currentUserId);
                String endpoint = "/rest/v1/chat_requests?or=(sender_id.eq." + encId + ",receiver_id.eq." + encId + ")&status=eq.accepted&order=created_at.desc";
                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);

                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray requestsArray = new JSONArray(response);
                    runOnUiThread(() -> {
                        progressUsers.setVisibility(View.GONE);
                        populateActiveChats(requestsArray);
                    });
                } else {
                    runOnUiThread(() -> {
                        progressUsers.setVisibility(View.GONE);
                        if (code != 404) {
                            Toast.makeText(this, "Could not load chats: " + response, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressUsers.setVisibility(View.GONE);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void populateActiveChats(JSONArray requestsArray) {
        usersListContainer.removeAllViews();
        try {
            if (requestsArray.length() == 0) {
                TextView emptyText = new TextView(this);
                emptyText.setText("No active direct chats yet. Search for a user above to send a chat request.");
                emptyText.setTextColor(Color.parseColor("#6C757D"));
                emptyText.setTextSize(14f);
                emptyText.setPadding(0, 20, 0, 20);
                usersListContainer.addView(emptyText);
                return;
            }
            
            for (int i = 0; i < requestsArray.length(); i++) {
                JSONObject reqObj = requestsArray.getJSONObject(i);
                
                String senderId = reqObj.optString("sender_id");
                String receiverId = reqObj.optString("receiver_id");
                
                String targetUserId, targetName, targetRole;
                if (currentUserId.equals(senderId)) {
                    targetUserId = receiverId;
                    targetName = reqObj.optString("receiver_name", "User");
                    targetRole = reqObj.optString("receiver_role", "Member");
                } else {
                    targetUserId = senderId;
                    targetName = reqObj.optString("sender_name", "User");
                    targetRole = reqObj.optString("sender_role", "Member");
                }

                addUserCard(targetUserId, targetName, targetRole, usersListContainer, "Tap to message");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void searchUsers(String query) {
        if (query.isEmpty()) return;
        
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();
        searchResultsContainer.removeAllViews();
        
        executor.execute(() -> {
            HttpURLConnection connProfiles = null;
            HttpURLConnection connUsers = null;
            try {
                // Query Profiles
                connProfiles = SupabaseHelper.createConnection("/rest/v1/profiles?select=*", "GET", accessToken);
                int codeProfiles = connProfiles.getResponseCode();
                String responseProfiles = SupabaseHelper.readResponse(connProfiles);
                JSONArray profilesArray = (codeProfiles >= 200 && codeProfiles < 300) ? new JSONArray(responseProfiles) : new JSONArray();

                // Query Users
                connUsers = SupabaseHelper.createConnection("/rest/v1/Users?select=*", "GET", accessToken);
                int codeUsers = connUsers.getResponseCode();
                String responseUsers = SupabaseHelper.readResponse(connUsers);
                JSONArray usersArray = (codeUsers >= 200 && codeUsers < 300) ? new JSONArray(responseUsers) : new JSONArray();

                runOnUiThread(() -> renderDualSearchResults(profilesArray, usersArray, query.toLowerCase()));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error during search: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (connProfiles != null) connProfiles.disconnect();
                if (connUsers != null) connUsers.disconnect();
            }
        });
    }
    
    private void renderDualSearchResults(JSONArray profilesArray, JSONArray usersArray, String queryLower) {
        try {
            boolean found = false;
            java.util.HashSet<String> seenIds = new java.util.HashSet<>();

            // 1. Process profiles table
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject obj = profilesArray.getJSONObject(i);
                
                String userId = obj.optString("firebase_uid", "");
                if (userId.isEmpty()) userId = obj.optString("id", "");
                
                if (userId.isEmpty() || userId.equals(currentUserId) || seenIds.contains(userId)) continue;

                String fullName = obj.optString("full_name", "");
                if (fullName.isEmpty()) fullName = obj.optString("firstName", "");
                if (fullName.isEmpty()) fullName = obj.optString("email", "User");
                
                String role = obj.optString("role", "Member");
                
                if (fullName.toLowerCase().contains(queryLower)) {
                    seenIds.add(userId);
                    found = true;
                    addUserCard(userId, fullName, role, searchResultsContainer, "Request Chat");
                }
            }
            
            // 2. Process Users table (fallback/merged)
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject obj = usersArray.getJSONObject(i);
                
                String userId = obj.optString("id", "");
                if (userId.isEmpty() || userId.equals(currentUserId) || seenIds.contains(userId)) continue;

                String firstName = obj.optString("firstName", "");
                String surname = obj.optString("surname", "");
                String fullName = (firstName + " " + surname).trim();
                if (fullName.isEmpty()) fullName = obj.optString("email", "User");
                
                String role = obj.optString("user_role", "Member");
                
                if (fullName.toLowerCase().contains(queryLower)) {
                    seenIds.add(userId);
                    found = true;
                    addUserCard(userId, fullName, role, searchResultsContainer, "Request Chat");
                }
            }

            if (!found) {
                Toast.makeText(this, "No matching users found.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
        }
    }

    private void addUserCard(String targetUserId, String targetName, String targetRole, LinearLayout parent, String actionText) {
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
        txtName.setText(targetName);
        txtName.setTextSize(17f);
        txtName.setTypeface(null, Typeface.BOLD);
        txtName.setTextColor(Color.parseColor("#212529"));

        TextView txtRole = new TextView(this);
        txtRole.setText(targetRole.toUpperCase() + " • " + actionText);
        txtRole.setTextSize(12f);
        txtRole.setTextColor(Color.parseColor("#6C757D"));

        innerLayout.addView(txtName);
        innerLayout.addView(txtRole);
        card.addView(innerLayout);

        card.setOnClickListener(v -> {
            if ("Request Chat".equals(actionText)) {
                checkAndRequestChat(targetUserId, targetName, targetRole);
            } else {
                openChat(targetUserId, targetName, false);
            }
        });
        
        parent.addView(card);
    }
    
    private void checkAndRequestChat(String targetUserId, String targetName, String targetRole) {
        Toast.makeText(this, "Checking request status...", Toast.LENGTH_SHORT).show();
        
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String encCurrent = SupabaseHelper.encode(currentUserId);
                String encTarget = SupabaseHelper.encode(targetUserId);
                String endpoint = "/rest/v1/chat_requests?or=(and(sender_id.eq." + encCurrent + ",receiver_id.eq." + encTarget + "),and(sender_id.eq." + encTarget + ",receiver_id.eq." + encCurrent + "))";
                
                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray reqs = new JSONArray(response);
                    if (reqs.length() > 0) {
                        JSONObject req = reqs.getJSONObject(0);
                        String status = req.optString("status");
                        if ("accepted".equals(status)) {
                            runOnUiThread(() -> openChat(targetUserId, targetName, false));
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "A chat request with this user is already pending.", Toast.LENGTH_LONG).show());
                        }
                    } else {
                        // Show dialog to send request
                        runOnUiThread(() -> showRequestDialog(targetUserId, targetName, targetRole));
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Network error: " + response, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error checking status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
    
    private void showRequestDialog(String targetUserId, String targetName, String targetRole) {
        new AlertDialog.Builder(this)
            .setTitle("Send Chat Request?")
            .setMessage("Send a chat request to " + targetName + " (" + targetRole + ")? They must accept your request in Alerts before you can message each other.")
            .setPositiveButton("Send Request", (dialog, which) -> sendChatRequest(targetUserId, targetName, targetRole))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void sendChatRequest(String targetUserId, String targetName, String targetRole) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/chat_requests", "POST", accessToken);
                conn.setRequestProperty("Prefer", "return=minimal");

                JSONObject body = new JSONObject();
                body.put("sender_id", currentUserId);
                body.put("sender_name", currentUserName);
                body.put("sender_role", currentUserRole);
                body.put("receiver_id", targetUserId);
                body.put("receiver_name", targetName);
                body.put("receiver_role", targetRole);
                body.put("status", "pending");

                SupabaseHelper.writeJson(conn, body);
                int code = conn.getResponseCode();
                
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> Toast.makeText(this, "Chat request sent!", Toast.LENGTH_SHORT).show());
                } else {
                    String err = SupabaseHelper.readResponse(conn);
                    runOnUiThread(() -> Toast.makeText(this, "Failed to send request: " + err, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error sending request.", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void openChat(String receiverId, String receiverName, boolean isCommunity) {
        Intent intent = new Intent(UserSelectionActivity.this, ChatActivity.class);
        intent.putExtra("RECEIVER_ID", receiverId);
        intent.putExtra("RECEIVER_NAME", receiverName);
        intent.putExtra("IS_COMMUNITY", isCommunity);
        intent.putExtra("SENDER_ID", currentUserId);
        intent.putExtra("SENDER_NAME", currentUserName);
        intent.putExtra("SENDER_ROLE", currentUserRole);
        startActivity(intent);
    }
}