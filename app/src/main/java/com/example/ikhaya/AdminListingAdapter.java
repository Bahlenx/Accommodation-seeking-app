package com.example.ikhaya;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AdminListingAdapter extends RecyclerView.Adapter<AdminListingAdapter.ViewHolder> {

    public interface OnListingActionListener {
        void onListingActionTaken();
    }

    private List<AdminListing> pendingListings;
    private Context context;
    private OnListingActionListener actionListener;

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

// OPTION 3: Click the card to inspect details
        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(context, AdminInspectionActivity.class);
            intent.putExtra("LISTING_ID", listing.getId());
            intent.putExtra("LISTING_TITLE", listing.getTitle());
            intent.putExtra("LISTING_DETAILS", listing.getDetails());
            intent.putExtra("LANDLORD_NAME", listing.getLandlordName());
            context.startActivity(intent);
        });

        // OPTION 1: Reject Button with Reason Input
        holder.btnRejectListing.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(context);
            input.setHint(" e.g., Blurry photos, Unrealistic price");
            input.setPadding(40, 40, 40, 40);

            new AlertDialog.Builder(context)
                    .setTitle("Reject Listing")
                    .setMessage("Please provide a reason for rejecting '" + listing.getTitle() + "':")
                    .setView(input)
                    .setPositiveButton("Reject", (dialog, which) -> {
                        String reason = input.getText().toString().trim();
                        if (reason.isEmpty()) {
                            Toast.makeText(context, "A reason is required to reject.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int currentPos = holder.getAdapterPosition();
                        if (currentPos != RecyclerView.NO_POSITION) {
                            listing.setStatus("REJECTED");
                            pendingListings.remove(currentPos);
                            notifyItemRemoved(currentPos);
                            notifyItemRangeChanged(currentPos, pendingListings.size());
                            Toast.makeText(context, "Rejected: " + reason, Toast.LENGTH_LONG).show();
                            if (actionListener != null) actionListener.onListingActionTaken();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Approve Button Logic (Unchanged)
        holder.btnApproveListing.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Approve Listing")
                    .setMessage("Approve '" + listing.getTitle() + "' to go live for all tenants?")
                    .setPositiveButton("Yes, Approve", (dialog, which) -> {
                        int currentPos = holder.getAdapterPosition();
                        if (currentPos != RecyclerView.NO_POSITION) {
                            listing.setStatus("APPROVED");
                            pendingListings.remove(currentPos);
                            notifyItemRemoved(currentPos);
                            notifyItemRangeChanged(currentPos, pendingListings.size());
                            Toast.makeText(context, "Listing Approved & Live", Toast.LENGTH_SHORT).show();
                            if (actionListener != null) actionListener.onListingActionTaken();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
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