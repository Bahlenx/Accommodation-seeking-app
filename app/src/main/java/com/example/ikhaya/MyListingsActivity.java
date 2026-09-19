package com.example.ikhaya;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MyListingsActivity extends AppCompatActivity {

    private LinearLayout myListingsContainer;
    private TextView txtNoListings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_my_listings);

        // Connect Java to XML
        myListingsContainer = findViewById(R.id.myListingsContainer);
        txtNoListings = findViewById(R.id.txtNoListings);

        // For now, there are no saved listings.
        // Firebase will be connected later.

        showNoListingsMessage();
    }

    private void showNoListingsMessage() {

        txtNoListings.setVisibility(TextView.VISIBLE);

    }
}