package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SearchResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_search_results);

        Button btnViewOne = findViewById(R.id.btnViewOne);

        btnViewOne.setOnClickListener(v -> {

            Intent intent = new Intent(
                    SearchResultsActivity.this,
                    PropertyDetailsActivity.class
            );

            startActivity(intent);
        });
    }
}