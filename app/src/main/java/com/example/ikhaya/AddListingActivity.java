package com.example.ikhaya;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class AddListingActivity extends AppCompatActivity {

    private EditText edtPropertyName;
    private EditText edtLocation;
    private EditText edtPrice;
    private EditText edtRooms;
    private EditText edtDescription;

    private ImageView imgProperty;

    private Uri selectedImageUri;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_listing);

        // Connect Java to XML views
        edtPropertyName = findViewById(R.id.edtPropertyName);
        edtLocation = findViewById(R.id.edtLocation);
        edtPrice = findViewById(R.id.edtPrice);
        edtRooms = findViewById(R.id.edtRooms);
        edtDescription = findViewById(R.id.edtDescription);

        imgProperty = findViewById(R.id.imgProperty);

        Button btnUploadImage =
                findViewById(R.id.btnUploadImage);

        Button btnSubmitListing =
                findViewById(R.id.btnSubmitListing);


        // Image picker
        imagePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        uri -> {

                            if (uri != null) {

                                selectedImageUri = uri;

                                imgProperty.setImageURI(uri);

                            }

                        }
                );


        // Open gallery
        btnUploadImage.setOnClickListener(v -> {

            imagePickerLauncher.launch("image/*");

        });


        // Submit listing
        btnSubmitListing.setOnClickListener(v -> {

            String propertyName =
                    edtPropertyName.getText().toString().trim();

            String location =
                    edtLocation.getText().toString().trim();

            String price =
                    edtPrice.getText().toString().trim();

            String rooms =
                    edtRooms.getText().toString().trim();

            String description =
                    edtDescription.getText().toString().trim();


            // Validation

            if (propertyName.isEmpty()) {

                edtPropertyName.setError("Enter property name");
                edtPropertyName.requestFocus();
                return;

            }

            if (location.isEmpty()) {

                edtLocation.setError("Enter location");
                edtLocation.requestFocus();
                return;

            }

            if (price.isEmpty()) {

                edtPrice.setError("Enter the price");
                edtPrice.requestFocus();
                return;

            }

            if (rooms.isEmpty()) {

                edtRooms.setError("Enter number of rooms");
                edtRooms.requestFocus();
                return;

            }

            if (description.isEmpty()) {

                edtDescription.setError("Enter a description");
                edtDescription.requestFocus();
                return;

            }


            // Check if an image was selected

            if (selectedImageUri == null) {

                Toast.makeText(
                        AddListingActivity.this,
                        "Please select a property image",
                        Toast.LENGTH_SHORT
                ).show();

                return;

            }


            // Temporary success message
            // Firebase will replace this later

            Toast.makeText(
                    AddListingActivity.this,
                    "Listing submitted successfully!",
                    Toast.LENGTH_SHORT
            ).show();


            // Return to landlord dashboard

            finish();

        });

    }

}