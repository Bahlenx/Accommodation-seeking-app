package com.example.ikhaya;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_register);

        Button btnRegister = findViewById(R.id.btnRegister);

        RadioGroup userTypeGroup = findViewById(R.id.userTypeGroup);

        btnRegister.setOnClickListener(v -> {

            int selectedTypeId =
                    userTypeGroup.getCheckedRadioButtonId();

            // Make sure a user type is selected
            if (selectedTypeId == -1) {

                Toast.makeText(
                        RegisterActivity.this,
                        "Please select Tenant or Landlord",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            // LANDLORD
            if (selectedTypeId == R.id.radioLandlord) {

                Intent intent = new Intent(
                        RegisterActivity.this,
                        LandlordDashboardActivity.class
                );

                startActivity(intent);
                finish();

            }


            // TENANT
            else if (selectedTypeId == R.id.radioTenant) {

                Intent intent = new Intent(
                        RegisterActivity.this,
                        HomeActivity.class
                );

                startActivity(intent);
                finish();

            }

        });
    }
}