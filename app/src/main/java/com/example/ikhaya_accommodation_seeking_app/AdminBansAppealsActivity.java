package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminBansAppealsActivity extends AppCompatActivity {

    private RecyclerView rvBansAppeals;
    private BansAppealsAdapter adapter;
    private final List<JSONObject> allCases = new ArrayList<>();
    private final List<JSONObject> filteredCases = new ArrayList<>();
    private ExecutorService executor;

    private String currentQuery = "";
    private String activeFilter = "ALL"; 
    private String activeSort = "NEWEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_bans_appeals);

        View btnBack = findViewById(R.id.btnBackBansAppeals);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        rvBansAppeals = findViewById(R.id.rvBansAppeals);
        rvBansAppeals.setLayoutManager(new LinearLayoutManager(this));

        adapter = new BansAppealsAdapter(filteredCases);
        rvBansAppeals.setAdapter(adapter);

        executor = Executors.newSingleThreadExecutor();
        
        setupSearchAndFilters();

        loadCases();
    }

    private void setupSearchAndFilters() {
        android.widget.EditText etSearch = findViewById(R.id.etSearchBans);
        android.widget.ImageButton btnClearSearch = findViewById(R.id.btnClearBanSearch);
        android.widget.Button btnSearch = findViewById(R.id.btnSearchBans);
        android.widget.Button btnFilter = findViewById(R.id.btnFilterBans);
        android.widget.Button btnSort = findViewById(R.id.btnSortBans);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    // Real-time search
                    currentQuery = s.toString();
                    applyFilterAndSort();
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
            
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    currentQuery = etSearch.getText().toString();
                    applyFilterAndSort();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.setText("");
                    currentQuery = "";
                    applyFilterAndSort();
                }
            });
        }

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                if (etSearch != null) {
                    currentQuery = etSearch.getText().toString();
                    applyFilterAndSort();
                }
            });
        }

        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
                popup.getMenu().add("All");
                popup.getMenu().add("Pending Appeals");
                popup.getMenu().add("Permanent Bans");
                popup.getMenu().add("Timed Suspensions");
                popup.getMenu().add("Approved / Lifted");
                popup.setOnMenuItemClickListener(item -> {
                    String title = item.getTitle().toString();
                    if ("Pending Appeals".equals(title)) activeFilter = "PENDING";
                    else if ("Permanent Bans".equals(title)) activeFilter = "BANNED";
                    else if ("Timed Suspensions".equals(title)) activeFilter = "SUSPENDED";
                    else if ("Approved / Lifted".equals(title)) activeFilter = "APPROVED";
                    else activeFilter = "ALL";
                    
                    btnFilter.setText("Filter: " + title);
                    applyFilterAndSort();
                    return true;
                });
                popup.show();
            });
        }

        if (btnSort != null) {
            btnSort.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
                popup.getMenu().add("Newest First");
                popup.getMenu().add("Oldest First");
                popup.getMenu().add("Pending Appeals First");
                popup.setOnMenuItemClickListener(item -> {
                    String title = item.getTitle().toString();
                    if ("Oldest First".equals(title)) activeSort = "OLDEST";
                    else if ("Pending Appeals First".equals(title)) activeSort = "PENDING_FIRST";
                    else activeSort = "NEWEST";
                    
                    btnSort.setText("Sort: " + title);
                    applyFilterAndSort();
                    return true;
                });
                popup.show();
            });
        }
    }

    private void loadCases() {
        executor.execute(() -> {
            try {
                String token = SupabaseHelper.getAccessToken(this);
                java.net.HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/moderation_cases?order=created_at.desc", "GET", token);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    List<JSONObject> tempList = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        tempList.add(arr.getJSONObject(i));
                    }
                    runOnUiThread(() -> {
                        allCases.clear();
                        allCases.addAll(tempList);
                        applyFilterAndSort();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void applyFilterAndSort() {
        filteredCases.clear();
        String q = currentQuery.trim().toLowerCase(java.util.Locale.ROOT);

        for (JSONObject obj : allCases) {
            String actionType = obj.optString("action_type", "").toUpperCase(java.util.Locale.ROOT);
            String appealStatus = obj.optString("appeal_status", "NONE").toUpperCase(java.util.Locale.ROOT);

            boolean matchesFilter = "ALL".equals(activeFilter)
                    || ("PENDING".equals(activeFilter) && "PENDING".equals(appealStatus))
                    || ("BANNED".equals(activeFilter) && "BANNED".equals(actionType))
                    || ("SUSPENDED".equals(activeFilter) && "SUSPENDED".equals(actionType))
                    || ("APPROVED".equals(activeFilter) && "APPROVED".equals(appealStatus));

            if (!matchesFilter) continue;

            if (!q.isEmpty()) {
                String hay = (obj.optString("user_name", "") + " "
                        + obj.optString("user_id", "") + " "
                        + obj.optString("admin_reason", "") + " "
                        + obj.optString("user_appeal_text", "") + " "
                        + actionType + " " + appealStatus).toLowerCase(java.util.Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            filteredCases.add(obj);
        }

        java.util.Collections.sort(filteredCases, (a, b) -> {
            String dateA = a.optString("created_at", "");
            String dateB = b.optString("created_at", "");
            if ("OLDEST".equals(activeSort)) {
                return dateA.compareTo(dateB);
            } else if ("PENDING_FIRST".equals(activeSort)) {
                int pA = "PENDING".equalsIgnoreCase(a.optString("appeal_status", "")) ? 0 : 1;
                int pB = "PENDING".equalsIgnoreCase(b.optString("appeal_status", "")) ? 0 : 1;
                return pA != pB ? Integer.compare(pA, pB) : dateB.compareTo(dateA);
            }
            return dateB.compareTo(dateA); // NEWEST default
        });

        adapter.notifyDataSetChanged();
        
        TextView tvResults = findViewById(R.id.tvBanResultsCount);
        TextView tvEmpty = findViewById(R.id.tvEmptyBans);
        if (tvResults != null) {
            tvResults.setText("Showing " + filteredCases.size() + " results");
            tvResults.setVisibility(filteredCases.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (tvEmpty != null) {
            tvEmpty.setVisibility(filteredCases.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private class BansAppealsAdapter extends RecyclerView.Adapter<BansAppealsAdapter.ViewHolder> {
        private List<JSONObject> cases;

        BansAppealsAdapter(List<JSONObject> cases) {
            this.cases = cases;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ban_appeal, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject caseObj = cases.get(position);
            String userId = caseObj.optString("user_id");
            String actionType = caseObj.optString("action_type");
            String appealStatus = caseObj.optString("appeal_status", "NONE");

            holder.tvUserId.setText("User ID: " + userId);
            holder.tvActionType.setText(actionType);
            holder.tvAppealStatus.setText("Appeal: " + appealStatus);

            holder.itemView.setOnClickListener(v -> showCaseDialog(caseObj));
        }

        @Override
        public int getItemCount() {
            return cases.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvUserId, tvActionType, tvAppealStatus;
            ViewHolder(View view) {
                super(view);
                tvUserId = view.findViewById(R.id.tvUserId);
                tvActionType = view.findViewById(R.id.tvActionType);
                tvAppealStatus = view.findViewById(R.id.tvAppealStatus);
            }
        }
    }

    private void showCaseDialog(JSONObject caseObj) {
        String caseId = caseObj.optString("id");
        String userId = caseObj.optString("user_id");
        String actionType = caseObj.optString("action_type");
        long suspensionEndTime = caseObj.optLong("suspension_end_time", 0);
        String adminReason = caseObj.optString("admin_reason", "");
        String userAppealText = caseObj.optString("user_appeal_text", "");
        String appealStatus = caseObj.optString("appeal_status", "");
        String adminUrl = caseObj.optString("admin_file_url", caseObj.optString("admin_attachment_url", null));
        String userUrl = caseObj.optString("user_file_url", caseObj.optString("user_attachment_url", null));

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ban_appeal_details, null);
        TextView tvDetails = dialogView.findViewById(R.id.tvDetails);
        Button btnAdminEvidence = dialogView.findViewById(R.id.btnAdminEvidence);
        Button btnUserEvidence = dialogView.findViewById(R.id.btnUserEvidence);
        Button btnLiftRestriction = dialogView.findViewById(R.id.btnLiftRestriction);

        String details = "User: " + userId + "\nAction: " + actionType + "\n\nAdmin Reason: " + adminReason;
        if ("SUSPENDED".equalsIgnoreCase(actionType) && suspensionEndTime > System.currentTimeMillis()) {
            long remaining = suspensionEndTime - System.currentTimeMillis();
            long days = remaining / (1000 * 60 * 60 * 24);
            long hours = (remaining / (1000 * 60 * 60)) % 24;
            details += "\nRemaining Suspension: " + days + "d " + hours + "h";
        }
        if (!userAppealText.isEmpty()) {
            details += "\n\nUser Appeal (" + appealStatus + "): " + userAppealText;
        }
        tvDetails.setText(details);

        if (adminUrl != null && !adminUrl.isEmpty()) {
            btnAdminEvidence.setVisibility(View.VISIBLE);
            btnAdminEvidence.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(adminUrl))));
        }

        if (userUrl != null && !userUrl.isEmpty()) {
            btnUserEvidence.setVisibility(View.VISIBLE);
            btnUserEvidence.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(userUrl))));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("Close", null)
                .show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#F8F9FA")));
        }

        if ("APPROVED".equals(appealStatus)) {
            btnLiftRestriction.setVisibility(View.GONE);
        } else {
            btnLiftRestriction.setOnClickListener(v -> {
                liftRestriction(userId, caseId);
                dialog.dismiss();
            });
        }
    }

    private void liftRestriction(String userId, String caseId) {
        executor.execute(() -> {
            try {
                String token = SupabaseHelper.getAccessToken(this);
                String encTarget = SupabaseHelper.encode(userId);
                // PATCH user profile
                JSONObject profilePatch = new JSONObject();
                profilePatch.put("is_suspended", false);
                SupabaseHelper.patch(
                        "/rest/v1/profiles?or=(id.eq." + encTarget + ",firebase_uid.eq." + encTarget + ")",
                        profilePatch,
                        token
                );

                // PATCH case
                JSONObject casePatch = new JSONObject();
                casePatch.put("appeal_status", "APPROVED");
                java.net.HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/moderation_cases?id=eq." + caseId, "PATCH", token);
                SupabaseHelper.writeJson(conn, casePatch);
                conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Restriction lifted", Toast.LENGTH_SHORT).show();
                    loadCases();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}