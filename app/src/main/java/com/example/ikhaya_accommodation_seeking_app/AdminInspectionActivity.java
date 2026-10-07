package com.example.ikhaya_accommodation_seeking_app;


import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AdminInspectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_inspection);

        TextView tvTitle = findViewById(R.id.tvInspectTitle);
        TextView tvDetails = findViewById(R.id.tvInspectDetails);
        TextView tvLandlord = findViewById(R.id.tvInspectLandlord);
        Button btnBack = findViewById(R.id.btnBackInspection);

        tvTitle.setText(getIntent().getStringExtra("LISTING_TITLE"));
        tvDetails.setText(getIntent().getStringExtra("LISTING_DETAILS"));
        tvLandlord.setText("Submitted by: " + getIntent().getStringExtra("LANDLORD_NAME"));

        btnBack.setOnClickListener(v -> finish());
    }
}