package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.List;

public class LandlordDashboardActivity extends AppCompatActivity {

    private TextView txtTotalListings;
    private TextView txtAvailableListings;
    private TextView txtPendingListings;
    private LinearLayout listingsContainer;
    private View cardPendingApplications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landlord_dashboard);

        Button btnAddListing = findViewById(R.id.btnAddListing);
        Button btnProfile = findViewById(R.id.btnProfile);

        Button navHome = findViewById(R.id.navHome);
        Button navListings = findViewById(R.id.navListings);
        Button navAdd = findViewById(R.id.navAdd);
        Button navProfile = findViewById(R.id.navProfile);

        // Bind Overview Elements
        txtTotalListings = findViewById(R.id.txtTotalListings);
        txtAvailableListings = findViewById(R.id.txtAvailableListings);
        txtPendingListings = findViewById(R.id.txtPendingListings);
        listingsContainer = findViewById(R.id.listingsContainer);
        cardPendingApplications = findViewById(R.id.cardPendingApplications);

        // 1. Pending Card Click -> Open ApplicationsActivity
        if (cardPendingApplications != null) {
            cardPendingApplications.setOnClickListener(v -> {
                Intent intent = new Intent(LandlordDashboardActivity.this, ApplicationsActivity.class);
                intent.putExtra("isLandlord", true);
                startActivity(intent);
            });
        }

        // 2. Add Listing Navigation
        if (btnAddListing != null) {
            btnAddListing.setOnClickListener(v -> startActivity(new Intent(this, AddListingActivity.class)));
        }

        if (navAdd != null) {
            navAdd.setOnClickListener(v -> startActivity(new Intent(this, AddListingActivity.class)));
        }

        // 3. Navigation Bar
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> startActivity(new Intent(this, LandlordProfileActivity.class)));
        }

        if (navProfile != null) {
            navProfile.setOnClickListener(v -> startActivity(new Intent(this, LandlordProfileActivity.class)));
        }

        if (navListings != null) {
            navListings.setOnClickListener(v -> startActivity(new Intent(this, MyListingsActivity.class)));
        }

        if (navHome != null) {
            navHome.setOnClickListener(v ->
                    Toast.makeText(this, "You are already on the dashboard", Toast.LENGTH_SHORT).show()
            );
        }

        refreshDashboardData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboardData();
    }

    private void refreshDashboardData() {
        // 1. Update Pending Count from ApplicationManager
        if (txtPendingListings != null) {
            txtPendingListings.setText(String.valueOf(ApplicationManager.getPendingCount(this)));
        }

        // 2. Load Listings from ListingManager
        List<ListingManager.Listing> listings = ListingManager.getListings(this);

        // 3. Update Statistics
        if (txtTotalListings != null) {
            txtTotalListings.setText(String.valueOf(listings.size()));
        }

        if (txtAvailableListings != null) {
            int availableCount = 0;
            for (ListingManager.Listing l : listings) {
                if ("Available".equalsIgnoreCase(l.status)) availableCount++;
            }
            txtAvailableListings.setText(String.valueOf(availableCount));
        }

        // 4. Render Dynamic Listing Cards
        if (listingsContainer != null) {
            listingsContainer.removeAllViews();

            for (ListingManager.Listing item : listings) {
                CardView card = new CardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardParams.setMargins(0, 0, 0, 18);
                card.setLayoutParams(cardParams);
                card.setRadius(16);
                card.setCardElevation(3);
                card.setCardBackgroundColor(Color.WHITE);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(24, 20, 24, 20);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(item.title);
                tvTitle.setTextSize(15);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setTextColor(Color.BLACK);

                TextView tvSub = new TextView(this);
                tvSub.setText(item.location + " • " + item.price);
                tvSub.setTextSize(13);
                tvSub.setTextColor(Color.parseColor("#666666"));
                tvSub.setPadding(0, 4, 0, 6);

                TextView tvStatus = new TextView(this);
                tvStatus.setText("Status: " + item.status);
                tvStatus.setTextSize(12);
                tvStatus.setTypeface(null, Typeface.BOLD);
                tvStatus.setTextColor(Color.parseColor("#2E7D32"));

                row.addView(tvTitle);
                row.addView(tvSub);
                row.addView(tvStatus);
                card.addView(row);
                
                card.setOnClickListener(v -> {
                    Intent detailsIntent = new Intent(this, PropertyDetailsActivity.class);
                    detailsIntent.putExtra("propertyName", item.title);
                    detailsIntent.putExtra("listingId", item.id);
                    detailsIntent.putExtra("listingPrice", item.price);
                    detailsIntent.putExtra("listingLocation", item.location);
                    detailsIntent.putExtra("isLandlord", true); // <--- Identifies Landlord session
                    startActivity(detailsIntent);
                });

                // Clicking a listing opens PropertyDetailsActivity
                card.setOnClickListener(v -> {
                    Intent detailsIntent = new Intent(this, PropertyDetailsActivity.class);
                    detailsIntent.putExtra("propertyName", item.title);
                    startActivity(detailsIntent);
                });

                listingsContainer.addView(card);
            }
        }
    }
}