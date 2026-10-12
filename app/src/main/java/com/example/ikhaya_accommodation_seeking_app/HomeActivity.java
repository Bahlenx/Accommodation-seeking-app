
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        EditText searchAccommodation =
                findViewById(R.id.searchAccommodation);

        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnProfile = findViewById(R.id.btnProfile);

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeActivity.this,
                    TenantProfileActivity.class
            );
            startActivity(intent);
        });

        btnSearch.setOnClickListener(v -> {
            String query = searchAccommodation.getText()
                    .toString().trim();

            openSearch(query, null);
        });

        findViewById(R.id.btnMthatha).setOnClickListener(
                v -> openSearch("Mthatha", null)
        );

        findViewById(R.id.btnGiyani).setOnClickListener(
                v -> openSearch("Giyani", null)
        );

        findViewById(R.id.btnTypeRoom).setOnClickListener(
                v -> openSearch("", "Room")
        );

        findViewById(R.id.btnTypeShared).setOnClickListener(
                v -> openSearch("", "Shared Accommodation")
        );

        findViewById(R.id.btnTypeHouse).setOnClickListener(
                v -> openSearch("", "House")
        );
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