package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        Button btnSearch = findViewById(R.id.btnSearch);

        Button btnProfile = findViewById(R.id.btnProfile);

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this,
                    TenantProfileActivity.class);
            startActivity(intent);
        });

        btnSearch.setOnClickListener(v -> {

            Intent intent = new Intent(
                    HomeActivity.this,
                    SearchResultsActivity.class
            );

            startActivity(intent);
        });
    }
}