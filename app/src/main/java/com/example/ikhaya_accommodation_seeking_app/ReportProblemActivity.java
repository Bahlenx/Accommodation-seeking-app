package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ReportProblemActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnSubmit = findViewById(R.id.btnSubmitProblem);

        btnBack.setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Report submitted",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }
}