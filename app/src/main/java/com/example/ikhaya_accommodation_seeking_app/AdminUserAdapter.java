package com.example.ikhaya_accommodation_seeking_app;



import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    private List<AdminUser> users;
    private Context context;

    public AdminUserAdapter(Context context, List<AdminUser> users) {
        this.context = context;
        this.users = users;
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
        holder.tvUserRole.setText(user.getRole());

        if (user.isSuspended()) {
            holder.btnSuspendUser.setText("Unsuspend");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#28A745")); // Green
            holder.tvUserName.setTextColor(Color.parseColor("#DC3545")); // Red text
        } else {
            holder.btnSuspendUser.setText("Suspend");
            holder.btnSuspendUser.setBackgroundColor(Color.parseColor("#DC3545")); // Red
            holder.tvUserName.setTextColor(Color.parseColor("#212529")); // Default text
        }

        holder.btnSuspendUser.setOnClickListener(v -> {
            user.setSuspended(!user.isSuspended());
            notifyItemChanged(position);
            String status = user.isSuspended() ? "suspended" : "restored";
            Toast.makeText(context, user.getName() + " account " + status, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() { return users.size(); }

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