package com.example.ikhaya;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class LandlordPersonalInformationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_landlord_personal_information);

        Button btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
    }
}