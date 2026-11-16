package com.example.ikhaya_accommodation_seeking_app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    private final Context context;
    private final List<AdminUser> users;
    private final String currentAdminId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AdminUserAdapter(Context context, List<AdminUser> users, String currentAdminId) {
        this.context = context;
        this.users = users;
        this.currentAdminId = currentAdminId == null ? "" : currentAdminId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminUser user = users.get(position);
        holder.tvUserName.setText(user.getName());
        holder.tvUserRole.setText(user.getRole().toUpperCase());

        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
        String identityStatus = user.getIdentityStatus() != null ? user.getIdentityStatus().toUpperCase() : "UNVERIFIED";

        if ("landlord".equals(role)) {
            holder.tvUserRole.setText("LANDLORD • " + identityStatus.replace("_", " "));
            if (holder.btnVerifyUser != null) {
                holder.btnVerifyUser.setVisibility(View.VISIBLE);
                if ("VERIFIED".equalsIgnoreCase(identityStatus)) {
                    holder.btnVerifyUser.setText("VIEW ID");
                    holder.btnVerifyUser.setBackgroundColor(Color.parseColor("#5E72E4"));
                    holder.btnVerifyUser.setOnClickListener(v -> {
                        if (context instanceof AdminUsersActivity) {
                            ((AdminUsersActivity) context).showLandlordIdReviewDialog(user);
                        }
                    });
                } else if ("PENDING_VERIFICATION".equalsIgnoreCase(identityStatus)) {
                    holder.btnVerifyUser.setText(R.string.btn_verify_id);
                    holder.btnVerifyUser.setBackgroundColor(Color.parseColor("#28A745"));
                    holder.btnVerifyUser.setOnClickListener(v -> {
                        if (context instanceof AdminUsersActivity) {
                            ((AdminUsersActivity) context).showLandlordIdReviewDialog(user);
                        }
                    });
                } else {
                    // Landlord is UNVERIFIED or REJECTED — allow Admin to prompt/enforce ID upload
                    holder.btnVerifyUser.setText("REQUEST ID");
                    holder.btnVerifyUser.setBackgroundColor(Color.parseColor("#FB6340"));
                    holder.btnVerifyUser.setOnClickListener(v -> {
                        if (context instanceof AdminUsersActivity) {
                            ((AdminUsersActivity) context).showRequestLandlordVerificationDialog(user);
                        }
                    });
                }
            }
        } else {
            holder.tvUserRole.setText(user.getRole().toUpperCase());
            if (holder.btnVerifyUser != null) {
                holder.btnVerifyUser.setVisibility(View.GONE);
            }
        }

        // Anti-Self-Destruction & Admin Protection Check
        if (user.getUserId().equals(currentAdminId)) {
            holder.btnSuspendUser.setText("YOU");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#6C757D")); // Gray disabled color
            holder.btnSuspendUser.setEnabled(false);
            holder.tvUserName.setTextColor(Color.parseColor("#5E72E4")); // Highlight own name in blue
            if (holder.btnDeleteUser != null) holder.btnDeleteUser.setVisibility(View.GONE);
            return; 
        } else if ("admin".equalsIgnoreCase(user.getRole())) {
            holder.btnSuspendUser.setText("ADMIN");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#6C757D")); // Gray disabled color
            holder.btnSuspendUser.setEnabled(false);
            if (holder.btnDeleteUser != null) holder.btnDeleteUser.setVisibility(View.GONE);
            return; 
        }

        holder.btnSuspendUser.setEnabled(true);
        if (holder.btnDeleteUser != null) holder.btnDeleteUser.setVisibility(View.VISIBLE);
        updateButtonUI(holder, user.isSuspended());

        // Hide suspend/delete button for the currently logged in Admin (so they don't break their own account)
        if (user.getUserId().equals(currentAdminId)) {
            holder.btnSuspendUser.setVisibility(View.GONE);
            if (holder.btnDeleteUser != null) holder.btnDeleteUser.setVisibility(View.GONE);
        } else {
            holder.btnSuspendUser.setVisibility(View.VISIBLE);
            if (holder.btnDeleteUser != null) holder.btnDeleteUser.setVisibility(View.VISIBLE);
        }

        holder.btnSuspendUser.setOnClickListener(v -> {
            boolean targetState = !user.isSuspended();

            if (targetState) {
                // Prompt admin for suspension reason
                final EditText inputReason = new EditText(context);
                inputReason.setHint("e.g. Policy violation, suspicious activity, invalid ID...");
                int paddingPx = (int) (16 * context.getResources().getDisplayMetrics().density);
                inputReason.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

                new AlertDialog.Builder(context)
                        .setTitle("Suspend " + user.getName())
                        .setMessage("Please specify a reason for suspending this user's account:")
                        .setView(inputReason)
                        .setPositiveButton("Suspend", (dialog, which) -> {
                            String reason = inputReason.getText().toString().trim();
                            executeSuspensionChange(holder, user, true, reason);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                executeSuspensionChange(holder, user, false, "");
            }
        });

        if (holder.btnDeleteUser != null) {
            holder.btnDeleteUser.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Delete Account")
                        .setMessage("Are you sure you want to permanently delete " + user.getName() + "'s account? This action cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            holder.btnDeleteUser.setEnabled(false);
                            SupabaseHelper.deleteAccountCompletely((Activity) context, user.getUserId(), SupabaseHelper.getAccessToken(context), "", "Deleted by Administrator");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }

    private void executeSuspensionChange(ViewHolder holder, AdminUser user, boolean targetState, String reason) {
        holder.btnSuspendUser.setEnabled(false);

        String token = SupabaseHelper.getAccessToken(context);

        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/profiles?firebase_uid=eq." + user.getUserId().trim();

                JSONObject body = new JSONObject();
                body.put("is_suspended", targetState);

                SupabaseHelper.patch(endpoint, body, token);

                if ("landlord".equalsIgnoreCase(user.getRole())) {
                    JSONObject listingPatch = new JSONObject();
                    if (targetState) {
                        listingPatch.put("status", "SUSPENDED_FOR_REVERIFICATION");
                        listingPatch.put("reverification_reason", "Landlord account suspended by administrator.");
                    } else {
                        listingPatch.put("status", "APPROVED");
                        listingPatch.put("reverification_reason", "");
                    }
                    SupabaseHelper.patch(
                            "/rest/v1/listings?landlord_uid=eq." + SupabaseHelper.encode(user.getUserId().trim())
                                    + (targetState ? "&status=in.(APPROVED,VERIFIED)" : "&status=eq.SUSPENDED_FOR_REVERIFICATION"),
                            listingPatch,
                            token
                    );
                }

                // Notify the user in Supabase so they receive an alert/audit record
                if (targetState) {
                    String notifMsg = "Your iKhaya account has been suspended by an administrator."
                            + (reason.isEmpty() ? "" : "\n\nReason: " + reason)
                            + "\n\nContact support via the Login screen if you need assistance.";
                    SupabaseHelper.createNotification(
                            user.getUserId().trim(),
                            "Account Suspended",
                            notifMsg,
                            token
                    );
                } else {
                    SupabaseHelper.createNotification(
                            user.getUserId().trim(),
                            "Account Reactivated",
                            "Your iKhaya account suspension has been lifted. You may now use all platform features.",
                            token
                    );
                }

                mainHandler.post(() -> {
                    user.setSuspended(targetState);
                    holder.btnSuspendUser.setEnabled(true);
                    updateButtonUI(holder, targetState);
                    String toastMsg = targetState
                            ? user.getName() + " suspended. Reason: " + (reason.isEmpty() ? "None specified" : reason)
                            : user.getName() + " reactivated.";
                    Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    holder.btnSuspendUser.setEnabled(true);
                    Toast.makeText(context, context.getString(R.string.error_account_update_failed) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateButtonUI(ViewHolder holder, boolean isSuspended) {
        if (isSuspended) {
            holder.btnSuspendUser.setText("Unsuspend");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#28A745")); // Green
            holder.tvUserName.setTextColor(Color.parseColor("#DC3545")); // Red text
        } else {
            holder.btnSuspendUser.setText("Suspend");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#DC3545")); // Red
            holder.tvUserName.setTextColor(Color.parseColor("#212529")); // Default dark text
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvUserRole;
        Button btnSuspendUser, btnVerifyUser, btnDeleteUser;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserRole = itemView.findViewById(R.id.tvUserRole);
            btnSuspendUser = itemView.findViewById(R.id.btnSuspendUser);
            btnVerifyUser = itemView.findViewById(R.id.btnVerifyUser);
            btnDeleteUser = itemView.findViewById(R.id.btnDeleteUser);
        }
    }
}