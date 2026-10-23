package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

        // Anti-Self-Destruction & Admin Protection Check
        if (user.getUserId().equals(currentAdminId)) {
            holder.btnSuspendUser.setText("YOU");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#6C757D")); // Gray disabled color
            holder.btnSuspendUser.setEnabled(false);
            holder.tvUserName.setTextColor(Color.parseColor("#5E72E4")); // Highlight own name in blue
            return; 
        } else if ("admin".equalsIgnoreCase(user.getRole())) {
            holder.btnSuspendUser.setText("ADMIN");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#6C757D")); // Gray disabled color
            holder.btnSuspendUser.setEnabled(false);
            return; 
        }

        holder.btnSuspendUser.setEnabled(true);
        updateButtonUI(holder, user.isSuspended());

        holder.btnSuspendUser.setOnClickListener(v -> {
            boolean targetState = !user.isSuspended();
            holder.btnSuspendUser.setEnabled(false);

            String token = SupabaseHelper.getAccessToken(context);

            executor.execute(() -> {
                try {
                    String endpoint = "/rest/v1/profiles?firebase_uid=eq." + user.getUserId().trim();

                    JSONObject body = new JSONObject();
                    body.put("is_suspended", targetState);

                    SupabaseHelper.patch(endpoint, body, token);

                    mainHandler.post(() -> {
                        user.setSuspended(targetState);
                        holder.btnSuspendUser.setEnabled(true);
                        updateButtonUI(holder, targetState);
                        Toast.makeText(context, String.format(Locale.getDefault(), context.getString(R.string.msg_account_status_updated), user.getName(), (targetState ? "suspended" : "reactivated")), Toast.LENGTH_SHORT).show();
                    });

                } catch (Exception e) {
                    mainHandler.post(() -> {
                        holder.btnSuspendUser.setEnabled(true);
                        Toast.makeText(context, context.getString(R.string.error_account_update_failed) + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
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
        Button btnSuspendUser;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserRole = itemView.findViewById(R.id.tvUserRole);
            btnSuspendUser = itemView.findViewById(R.id.btnSuspendUser);
        }
    }
}