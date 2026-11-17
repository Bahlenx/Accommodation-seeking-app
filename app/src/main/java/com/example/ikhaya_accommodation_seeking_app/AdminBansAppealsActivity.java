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
    private List<JSONObject> caseList;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_bans_appeals);

        rvBansAppeals = findViewById(R.id.rvBansAppeals);
        rvBansAppeals.setLayoutManager(new LinearLayoutManager(this));

        caseList = new ArrayList<>();
        adapter = new BansAppealsAdapter(caseList);
        rvBansAppeals.setAdapter(adapter);

        executor = Executors.newSingleThreadExecutor();

        loadCases();
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
                        caseList.clear();
                        caseList.addAll(tempList);
                        adapter.notifyDataSetChanged();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
        String adminReason = caseObj.optString("admin_reason", "");
        String userAppealText = caseObj.optString("user_appeal_text", "");
        String appealStatus = caseObj.optString("appeal_status", "");
        String adminUrl = caseObj.optString("admin_attachment_url", null);
        String userUrl = caseObj.optString("user_attachment_url", null);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ban_appeal_details, null);
        TextView tvDetails = dialogView.findViewById(R.id.tvDetails);
        Button btnAdminEvidence = dialogView.findViewById(R.id.btnAdminEvidence);
        Button btnUserEvidence = dialogView.findViewById(R.id.btnUserEvidence);
        Button btnLiftRestriction = dialogView.findViewById(R.id.btnLiftRestriction);

        String details = "User: " + userId + "\nAction: " + actionType + "\n\nAdmin Reason: " + adminReason;
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
                // PATCH user profile
                JSONObject profilePatch = new JSONObject();
                profilePatch.put("is_suspended", false);
                SupabaseHelper.patch("/rest/v1/profiles?id=eq." + userId, profilePatch, token);

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