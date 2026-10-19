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

import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminListingAdapter extends RecyclerView.Adapter<AdminListingAdapter.ViewHolder> {

    public interface OnListingActionListener {
        void onListingActionTaken();
    }

    private final Context context;
    private final List<AdminListing> pendingListings;
    private final OnListingActionListener actionListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AdminListingAdapter(Context context, List<AdminListing> pendingListings, OnListingActionListener listener) {
        this.context = context;
        this.pendingListings = pendingListings;
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_listing, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminListing listing = pendingListings.get(position);

        holder.tvListingTitle.setText(listing.getTitle());
        holder.tvListingDetails.setText(listing.getDetails());
        holder.tvLandlordInfo.setText("Submitted by: " + listing.getLandlordName());

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, AdminInspectionActivity.class);
            intent.putExtra("LISTING_ID", listing.getId());
            intent.putExtra("LISTING_TITLE", listing.getTitle());
            intent.putExtra("LISTING_DETAILS", listing.getDetails());
            intent.putExtra("LANDLORD_NAME", listing.getLandlordName());
            intent.putExtra("IMAGE_URL", listing.getImageUrl());
            context.startActivity(intent);
        });

        holder.btnApproveListing.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Approve Listing")
                    .setMessage("Approve '" + listing.getTitle() + "' to go live on the platform?")
                    .setPositiveButton("Yes, Approve", (dialog, which) -> {
                        updateListingStatus(listing, "APPROVED", null, holder.getBindingAdapterPosition());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        holder.btnRejectListing.setOnClickListener(v -> {
            EditText input = new EditText(context);
            input.setHint("e.g., Unclear photos, incorrect address, misleading price");
            input.setPadding(40, 40, 40, 40);

            new AlertDialog.Builder(context)
                    .setTitle("Reject Listing")
                    .setMessage("Please provide a reason for rejecting '" + listing.getTitle() + "':")
                    .setView(input)
                    .setPositiveButton("Reject", (dialog, which) -> {
                        String reason = input.getText().toString().trim();
                        if (reason.isEmpty()) {
                            Toast.makeText(context, "A reason is required to reject a listing.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        updateListingStatus(listing, "REJECTED", reason, holder.getBindingAdapterPosition());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateListingStatus(AdminListing listing, String newStatus, String reason, int position) {
        String token = SupabaseHelper.getAccessToken(context);

        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/listings?id=eq." + listing.getId().trim();

                JSONObject body = new JSONObject();
                body.put("status", newStatus);
                body.put("availability", "APPROVED".equalsIgnoreCase(newStatus) ? "Available" : "Rejected");
                if (reason != null && !reason.isEmpty()) {
                    body.put("rejection_reason", reason);
                }

                // Native OkHttp PATCH
                SupabaseHelper.patch(endpoint, body, token);

                if ("REJECTED".equalsIgnoreCase(newStatus) && listing.getLandlordUid() != null && !listing.getLandlordUid().isEmpty()) {
                    sendRejectionNotification(token, listing.getLandlordUid(), listing.getTitle(), reason);
                }

                mainHandler.post(() -> {
                    if (position >= 0 && position < pendingListings.size()) {
                        pendingListings.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, pendingListings.size());
                    }
                    Toast.makeText(context, "Listing " + newStatus.toLowerCase() + " successfully.", Toast.LENGTH_SHORT).show();
                    AnalyticsHelper.logAdminAction(context, newStatus.toLowerCase(), listing.getTitle());
                    if (actionListener != null) {
                        actionListener.onListingActionTaken();
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void sendRejectionNotification(String token, String landlordUid, String propertyTitle, String reason) {
        HttpURLConnection conn = null;
        try {
            conn = SupabaseHelper.createConnection("/rest/v1/notifications", "POST", token);
            conn.setRequestProperty("Prefer", "return=minimal");

            JSONObject notif = new JSONObject();
            notif.put("user_uid", landlordUid);
            notif.put("title", "Listing Rejected: " + propertyTitle);
            notif.put("message", "Your accommodation submission was rejected. Reason: " + reason);

            SupabaseHelper.writeJson(conn, notif);
            conn.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public int getItemCount() {
        return pendingListings.size();
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