package com.example.ikhaya_accommodation_seeking_app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminActiveListingAdapter extends RecyclerView.Adapter<AdminActiveListingAdapter.ViewHolder> {

    public interface OnListingRevokedListener {
        void onListingRevoked();
    }

    private final Context context;
    private final List<AdminListing> activeListings;
    private final OnListingRevokedListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AdminActiveListingAdapter(Context context, List<AdminListing> activeListings, OnListingRevokedListener listener) {
        this.context = context;
        this.activeListings = activeListings;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Reuse the same layout, we will modify the buttons programmatically
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_listing, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminListing listing = activeListings.get(position);

        holder.tvListingTitle.setText(listing.getTitle());
        holder.tvListingDetails.setText(listing.getDetails());
        holder.tvLandlordInfo.setText("Submitted by: " + listing.getLandlordName());

        // HIDE the Approve Button and Rename Reject to REVOKE
        holder.btnApproveListing.setVisibility(View.GONE);
        holder.btnRejectListing.setText("REVOKE");

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, AdminInspectionActivity.class);
            intent.putExtra("LISTING_ID", listing.getId());
            intent.putExtra("LISTING_TITLE", listing.getTitle());
            intent.putExtra("LISTING_DETAILS", listing.getDetails());
            intent.putExtra("LANDLORD_NAME", listing.getLandlordName());
            intent.putExtra("IMAGE_URL", listing.getImageUrl());
            intent.putExtra("LISTING_STATUS", listing.getStatus()); 
            context.startActivity(intent);
        });

        holder.btnRejectListing.setOnClickListener(v -> {
            EditText input = new EditText(context);
            input.setHint("e.g., Reported as a scam, invalid details");
            input.setPadding(40, 40, 40, 40);

            new AlertDialog.Builder(context)
                    .setTitle("Revoke Live Listing")
                    .setMessage("Pull '" + listing.getTitle() + "' offline immediately? Provide a reason:")
                    .setView(input)
                    .setPositiveButton("Revoke", (dialog, which) -> {
                        String reason = input.getText().toString().trim();
                        if (reason.isEmpty()) {
                            Toast.makeText(context, "Reason required to revoke a listing.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        revokeListing(listing, reason, holder.getBindingAdapterPosition());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void revokeListing(AdminListing listing, String reason, int position) {
        String token = SupabaseHelper.getAccessToken(context);
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/listings?id=eq." + listing.getId().trim();
                JSONObject body = new JSONObject();
                body.put("status", "REJECTED");
                body.put("availability", "Rejected");
                body.put("rejection_reason", "ADMIN REVOKED: " + reason);

                SupabaseHelper.patch(endpoint, body, token);

                mainHandler.post(() -> {
                    if (position >= 0 && position < activeListings.size()) {
                        activeListings.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, activeListings.size());
                    }
                    Toast.makeText(context, "Listing revoked and pulled offline.", Toast.LENGTH_SHORT).show();
                    AnalyticsHelper.logAdminAction(context, "revoked", listing.getTitle());
                    if (listener != null) listener.onListingRevoked();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Revoke failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public int getItemCount() {
        return activeListings.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvListingTitle, tvListingDetails, tvLandlordInfo;
        Button btnRejectListing, btnApproveListing;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvListingTitle = itemView.findViewById(R.id.tvListingTitle);
            tvListingDetails = itemView.findViewById(R.id.tvListingDetails);
            tvLandlordInfo = itemView.findViewById(R.id.tvLandlordInfo);
            btnRejectListing = itemView.findViewById(R.id.btnRejectListing);
            btnApproveListing = itemView.findViewById(R.id.btnApproveListing);
        }
    }
}