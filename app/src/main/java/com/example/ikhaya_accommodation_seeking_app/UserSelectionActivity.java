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
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.HashSet;
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

    private volatile boolean messagesScreenVisible = false;
    private volatile boolean markingMessages = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_selection);

        android.view.View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        usersListContainer = findViewById(R.id.usersListContainer);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        progressUsers = findViewById(R.id.progressUsers);
        editSearchUser = findViewById(R.id.editSearchUser);

        CardView cardCommunityChat = findViewById(R.id.cardCommunityChat);
        Button btnViewAlerts = findViewById(R.id.btnViewAlerts);
        Button btnSearchUser = findViewById(R.id.btnSearchUser);
        TextView txtRoleBadge = findViewById(R.id.txtRoleBadge);

        accessToken = SupabaseHelper.getAccessToken(this);
        currentUserId = SupabaseHelper.getUserId(this);

        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = "guest_" + (System.currentTimeMillis() % 1000);
        }

        SharedPreferences prefs = getSharedPreferences("iKhayaSession", MODE_PRIVATE);
        currentUserName = prefs.getString("USER_NAME", "iKhaya User");
        currentUserRole = prefs.getString("USER_ROLE", "Member");

        if (txtRoleBadge != null) {
            txtRoleBadge.setText(currentUserRole.toUpperCase() + " ●");
        }

        if (cardCommunityChat != null) {
            cardCommunityChat.setOnClickListener(v ->
                    openChat("COMMUNITY", "🌍 iKhaya Community Group", true)
            );
        }

        if (btnViewAlerts != null) {
            btnViewAlerts.setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class))
            );
        }

        if (btnSearchUser != null && editSearchUser != null) {
            btnSearchUser.setOnClickListener(v ->
                    searchUsers(editSearchUser.getText().toString().trim())
            );
        }

        setupBottomNavigation();
        loadActiveChats();
    }

    private void setupBottomNavigation() {
        View userSelectionRoot = findViewById(R.id.userSelectionRoot);
        View floatingBottomNav = findViewById(R.id.floatingBottomNav);

        if (floatingBottomNav != null) {
            floatingBottomNav.setPadding(0, 0, 0, 0);

            if (userSelectionRoot != null) {
                ViewCompat.setOnApplyWindowInsetsListener(userSelectionRoot, (v, insets) -> {
                    Insets statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    Insets navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                    v.setPadding(0, statusBar.top, 0, 0);

                    if (floatingBottomNav.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) floatingBottomNav.getLayoutParams();
                        int baseMarginPx = (int) (16 * getResources().getDisplayMetrics().density);
                        lp.bottomMargin = navBar.bottom + baseMarginPx;
                        floatingBottomNav.setLayoutParams(lp);
                        floatingBottomNav.setPadding(0, 0, 0, 0);
                    }

                    return WindowInsetsCompat.CONSUMED;
                });
            }

            View navHome = findViewById(R.id.navHome);
            if (navHome != null) {
                navHome.setOnClickListener(v -> {
                    Intent intent = new Intent(UserSelectionActivity.this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }

            View navFavorites = findViewById(R.id.navFavorites);
            if (navFavorites != null) {
                navFavorites.setOnClickListener(v -> {
                    startActivity(new Intent(UserSelectionActivity.this, SavedListingsActivity.class));
                    finish();
                });
            }

            View navViewings = findViewById(R.id.navViewings);
            if (navViewings != null) {
                navViewings.setOnClickListener(v -> {
                    startActivity(new Intent(UserSelectionActivity.this, ViewingsActivity.class));
                    finish();
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        messagesScreenVisible = true;

        accessToken = SupabaseHelper.getAccessToken(this);
        String userId = SupabaseHelper.getUserId(this);
        if (userId != null && !userId.isEmpty()) {
            currentUserId = userId;
        }

        loadActiveChats();
        markIncomingMessagesSeen();
    }

    @Override
    protected void onPause() {
        messagesScreenVisible = false;
        super.onPause();
    }

    private void markIncomingMessagesSeen() {
        if (markingMessages) {
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            return;
        }

        markingMessages = true;

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/messages?select=id&receiver_id=eq."
                        + SupabaseHelper.encode(userId)
                        + "&order=created_at.desc&limit=1000";

                connection = SupabaseHelper.createConnection(endpoint, "GET", token);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    JSONArray rows = new JSONArray(response);

                    LandlordNotificationState.markSeen(getApplicationContext(), "messages", userId, rows);
                    TenantNotificationState.markSeen(getApplicationContext(), userId, "messages", rows);
                }
            } catch (Exception e) {
                android.util.Log.w("UserSelection", "Could not mark messages as seen", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                markingMessages = false;
            }
        });
    }

    private void loadActiveChats() {
        if (accessToken == null || accessToken.isEmpty()) {
            if (progressUsers != null) {
                progressUsers.setVisibility(View.GONE);
            }
            return;
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String encId = SupabaseHelper.encode(currentUserId);
                String endpoint = "/rest/v1/chat_requests?or=(sender_id.eq."
                        + encId
                        + ",receiver_id.eq."
                        + encId
                        + ")&status=eq.accepted&order=created_at.desc";

                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray requestsArray = new JSONArray(response);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (progressUsers != null) progressUsers.setVisibility(View.GONE);
                        populateActiveChats(requestsArray);
                    });
                } else {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (progressUsers != null) progressUsers.setVisibility(View.GONE);
                        if (code != 404) {
                            Toast.makeText(this, "Could not load chats: " + response, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (progressUsers != null) progressUsers.setVisibility(View.GONE);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void populateActiveChats(JSONArray requestsArray) {
        if (usersListContainer == null) return;
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
            android.util.Log.e("UserSelection", "Could not display active chats", e);
        }
    }

    private void searchUsers(String query) {
        if (query.isEmpty()) return;

        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();
        if (searchResultsContainer != null) {
            searchResultsContainer.removeAllViews();
        }

        executor.execute(() -> {
            HttpURLConnection connProfiles = null;
            HttpURLConnection connUsers = null;

            try {
                connProfiles = SupabaseHelper.createConnection("/rest/v1/profiles?select=*", "GET", accessToken);
                int codeProfiles = connProfiles.getResponseCode();
                String responseProfiles = SupabaseHelper.readResponse(connProfiles);
                JSONArray profilesArray = codeProfiles >= 200 && codeProfiles < 300
                        ? new JSONArray(responseProfiles)
                        : new JSONArray();

                connUsers = SupabaseHelper.createConnection("/rest/v1/Users?select=*", "GET", accessToken);
                int codeUsers = connUsers.getResponseCode();
                String responseUsers = SupabaseHelper.readResponse(connUsers);
                JSONArray usersArray = codeUsers >= 200 && codeUsers < 300
                        ? new JSONArray(responseUsers)
                        : new JSONArray();

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        renderDualSearchResults(profilesArray, usersArray, query.toLowerCase());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, "Error during search: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                if (connProfiles != null) connProfiles.disconnect();
                if (connUsers != null) connUsers.disconnect();
            }
        });
    }

    private void renderDualSearchResults(JSONArray profilesArray, JSONArray usersArray, String queryLower) {
        try {
            boolean found = false;
            HashSet<String> seenIds = new HashSet<>();

            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject obj = profilesArray.getJSONObject(i);

                String userId = obj.optString("firebase_uid", "");
                if (userId.isEmpty()) {
                    userId = obj.optString("id", "");
                }

                if (userId.isEmpty() || userId.equals(currentUserId) || seenIds.contains(userId)) {
                    continue;
                }

                String fullName = obj.optString("full_name", "");
                if (fullName.isEmpty()) {
                    fullName = obj.optString("firstName", "");
                }
                if (fullName.isEmpty()) {
                    fullName = obj.optString("email", "User");
                }

                String role = obj.optString("role", "Member");

                if (fullName.toLowerCase().contains(queryLower)) {
                    seenIds.add(userId);
                    found = true;
                    addUserCard(userId, fullName, role, searchResultsContainer, "Request Chat");
                }
            }

            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject obj = usersArray.getJSONObject(i);

                String userId = obj.optString("id", "");
                if (userId.isEmpty() || userId.equals(currentUserId) || seenIds.contains(userId)) {
                    continue;
                }

                String firstName = obj.optString("firstName", "");
                String surname = obj.optString("surname", "");
                String fullName = (firstName + " " + surname).trim();
                if (fullName.isEmpty()) {
                    fullName = obj.optString("email", "User");
                }

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
            android.util.Log.e("UserSelection", "Could not display search results", e);
        }
    }

    private void showContactDeleteMenu(android.view.View anchor, String targetUid) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add("Delete Chat & Contact");
        popup.setOnMenuItemClickListener(item -> {
            if ("Delete Chat & Contact".equals(item.getTitle())) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete Contact")
                        .setMessage("Delete this contact and all chat history?")
                        .setPositiveButton("Delete", (dialog, which) -> deleteContactAndChat(targetUid))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return true;
        });
        popup.show();
    }

    private void deleteContactAndChat(String targetUid) {
        String myUid = SupabaseHelper.getUserId(this);
        String token = SupabaseHelper.getAccessToken(this);
        if (myUid == null || token == null) return;
        
        executor.execute(() -> {
            try {
                String encodedMy = SupabaseHelper.encode(myUid);
                String encodedTarget = SupabaseHelper.encode(targetUid);
                
                // Delete messages
                String endpointMsg = "/rest/v1/messages?or=(and(sender_id.eq." + encodedMy + ",receiver_id.eq." + encodedTarget + "),and(sender_id.eq." + encodedTarget + ",receiver_id.eq." + encodedMy + "))";
                HttpURLConnection connMsg = SupabaseHelper.createConnection(endpointMsg, "DELETE", token);
                connMsg.getResponseCode();
                connMsg.disconnect();
                
                // Delete chat request
                String endpointReq = "/rest/v1/chat_requests?or=(and(sender_id.eq." + encodedMy + ",receiver_id.eq." + encodedTarget + "),and(sender_id.eq." + encodedTarget + ",receiver_id.eq." + encodedMy + "))";
                HttpURLConnection connReq = SupabaseHelper.createConnection(endpointReq, "DELETE", token);
                connReq.getResponseCode();
                connReq.disconnect();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                    recreate();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to delete contact", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void addUserCard(String targetUserId, String targetName, String targetRole, LinearLayout parent, String actionText) {
        if (parent == null) return;

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
        innerLayout.setOrientation(LinearLayout.HORIZONTAL);
        innerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        innerLayout.setPadding(40, 36, 40, 36);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textLayout.setLayoutParams(textParams);

        TextView txtName = new TextView(this);
        txtName.setText(targetName);
        txtName.setTextSize(17f);
        txtName.setTypeface(null, Typeface.BOLD);
        txtName.setTextColor(Color.parseColor("#212529"));

        TextView txtRole = new TextView(this);
        txtRole.setText(targetRole.toUpperCase() + " • " + actionText);
        txtRole.setTextSize(12f);
        txtRole.setTextColor(Color.parseColor("#6C757D"));

        textLayout.addView(txtName);
        textLayout.addView(txtRole);
        innerLayout.addView(textLayout);
        
        if (!"Request Chat".equals(actionText)) {
            TextView btnOptions = new TextView(this);
            btnOptions.setText("⋮");
            btnOptions.setTextSize(20f);
            btnOptions.setTypeface(null, Typeface.BOLD);
            btnOptions.setTextColor(Color.parseColor("#6C757D"));
            btnOptions.setPadding(24, 12, 12, 12);
            btnOptions.setBackgroundResource(R.drawable.selector_regular_button);
            btnOptions.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
            
            btnOptions.setOnClickListener(v -> showContactDeleteMenu(btnOptions, targetUserId));
            innerLayout.addView(btnOptions);
        }

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
                String endpoint = "/rest/v1/chat_requests?or=(and(sender_id.eq."
                        + encCurrent
                        + ",receiver_id.eq."
                        + encTarget
                        + "),and(sender_id.eq."
                        + encTarget
                        + ",receiver_id.eq."
                        + encCurrent
                        + "))";

                conn = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    JSONArray reqs = new JSONArray(response);
                    if (reqs.length() > 0) {
                        JSONObject req = reqs.getJSONObject(0);
                        String status = req.optString("status");
                        if ("accepted".equalsIgnoreCase(status)) {
                            runOnUiThread(() -> openChat(targetUserId, targetName, false));
                        } else {
                            runOnUiThread(() ->
                                    Toast.makeText(this, "A chat request with this user is already pending.", Toast.LENGTH_LONG).show()
                            );
                        }
                    } else {
                        runOnUiThread(() -> showRequestDialog(targetUserId, targetName, targetRole));
                    }
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Network error: " + response, Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error checking status: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void showRequestDialog(String targetUserId, String targetName, String targetRole) {
        new AlertDialog.Builder(this)
                .setTitle("Send Chat Request?")
                .setMessage("Send a chat request to " + targetName + " (" + targetRole
                        + ")? They must accept your request in Alerts before you can message each other.")
                .setPositiveButton("Send Request", (dialog, which) ->
                        sendChatRequest(targetUserId, targetName, targetRole)
                )
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
                    runOnUiThread(() ->
                            Toast.makeText(this, "Chat request sent!", Toast.LENGTH_SHORT).show()
                    );
                } else {
                    String error = SupabaseHelper.readResponse(conn);
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed to send request: " + error, Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error sending request.", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void openChat(String receiverId, String receiverName, boolean isCommunity) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("RECEIVER_ID", receiverId);
        intent.putExtra("RECEIVER_NAME", receiverName);
        intent.putExtra("IS_COMMUNITY", isCommunity);
        intent.putExtra("SENDER_ID", currentUserId);
        intent.putExtra("SENDER_NAME", currentUserName);
        intent.putExtra("SENDER_ROLE", currentUserRole);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}