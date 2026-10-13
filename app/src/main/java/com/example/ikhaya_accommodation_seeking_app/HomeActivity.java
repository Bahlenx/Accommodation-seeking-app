package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        EditText searchAccommodation = findViewById(R.id.searchAccommodation);
        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnProfile = findViewById(R.id.btnProfile);
        Button btnChat = findViewById(R.id.btnChat);

        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                Intent intent = new Intent(
                        HomeActivity.this,
                        UserSelectionActivity.class
                );
                startActivity(intent);
            });
        }

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                Intent intent = new Intent(
                        HomeActivity.this,
                        TenantProfileActivity.class
                );
                startActivity(intent);
            });
        }

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                String query = (searchAccommodation != null)
                        ? searchAccommodation.getText().toString().trim()
                        : "";
                openSearch(query, null);
            });
        }

        // Dynamically bind quick filter buttons without hard compiler dependencies on R.id
        setupFilterButton("btnMthatha", "Mthatha", null);
        setupFilterButton("btnGiyani", "Giyani", null);
        setupFilterButton("btnTypeRoom", "", "Room");
        setupFilterButton("btnTypeShared", "", "Shared Accommodation");
        setupFilterButton("btnTypeHouse", "", "House");
    }

    private void setupFilterButton(String idName, String query, String propertyType) {
        int resId = getResources().getIdentifier(idName, "id", getPackageName());
        if (resId != 0) {
            View view = findViewById(resId);
            if (view != null) {
                view.setOnClickListener(v -> openSearch(query, propertyType));
            }
        }
    }

    private void openSearch(String query, String propertyType) {
        Intent intent = new Intent(
                HomeActivity.this,
                SearchResultsActivity.class
        );

        intent.putExtra("SEARCH_QUERY", query);

        if (propertyType != null) {
            intent.putExtra("PROPERTY_TYPE", propertyType);
        }

        startActivity(intent);
    }
}