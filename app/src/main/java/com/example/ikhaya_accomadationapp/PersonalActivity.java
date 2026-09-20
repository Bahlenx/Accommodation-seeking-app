package com.example.ikhaya_accomadationapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PersonalActivity extends AppCompatActivity {

    private EditText idInput, provinceInput, cityInput, suburbInput;
    private Button registerBtn, gpsBtn;

    private FusedLocationProviderClient fusedLocationClient;
    private Geocoder geocoder;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Permission launcher for Location
    private final ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineLocationGranted;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    fineLocationGranted = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false));
                } else {
                    fineLocationGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                }
                
                boolean coarseLocationGranted;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    coarseLocationGranted = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));
                } else {
                    coarseLocationGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                }
                
                if (fineLocationGranted || coarseLocationGranted) {
                    fetchLocation();
                } else {
                    Toast.makeText(this, "Location permission denied. Cannot fetch GPS.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userverification); // Make sure layout filename matches

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this, Locale.getDefault());

        // 1. Link XML elements with updated IDs
        idInput = findViewById(R.id.idNum);
        provinceInput = findViewById(R.id.provinceInputText);
        cityInput = findViewById(R.id.cityInputText);
        suburbInput = findViewById(R.id.suburbInputText);

        registerBtn = findViewById(R.id.registrationBtn);
        gpsBtn = findViewById(R.id.GPSbtn);
        ImageButton backButton = findViewById(R.id.back_button);

        // 2. Back Button Logic
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // 3. GPS Button Logic
        if (gpsBtn != null) {
            gpsBtn.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fetchLocation();
                } else {
                    locationPermissionRequest.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                }
            });
        }

        // 4. Registration Submission & Validation Logic
        if (registerBtn != null) {
            registerBtn.setOnClickListener(v -> {
                String enteredId = idInput.getText().toString().trim();
                String province = provinceInput.getText().toString().trim();
                String city = cityInput.getText().toString().trim();
                String suburb = suburbInput.getText().toString().trim();

                // Advanced regex validation for South African fields
                // 1. Province: Should only contain letters and spaces (e.g. KwaZulu-Natal, Gauteng)
                if (!province.matches("[a-zA-Z\\s-]+")) {
                    provinceInput.setError("Please enter a valid province name");
                    return;
                }
                
                // 2. City: Should only contain letters and spaces
                if (!city.matches("[a-zA-Z\\s-]+")) {
                    cityInput.setError("Please enter a valid city name");
                    return;
                }
                
                // 3. Suburb: Should only contain letters, spaces, and numbers (e.g. Ext 10)
                if (!suburb.matches("[a-zA-Z0-9\\s-]+")) {
                    suburbInput.setError("Please enter a valid suburb name");
                    return;
                }

                // Check for empty fields
                if (enteredId.isEmpty() || province.isEmpty() || city.isEmpty() || suburb.isEmpty()) {
                    Toast.makeText(PersonalActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Retrieve the role from the Intent (or use a default/fallback if null)
                final String role = getIntent().getStringExtra("USER_ROLE") != null ? getIntent().getStringExtra("USER_ROLE") : "Resident";

                // --- ID Validation & Legal Security Snippet ---
                if (!IdSecurityHelper.isValidLuhn(enteredId)) {
                    Toast.makeText(this, "Invalid ID Number format detected.", Toast.LENGTH_LONG).show();
                    return; // Stop registration
                }
    
                int age = IdSecurityHelper.getAgeAndValidateDate(enteredId);
    
                if (age == -1) {
                    Toast.makeText(this, "Invalid Date of Birth in ID.", Toast.LENGTH_LONG).show();
                    return;
                }
    
                // Legal Compliance: Landlords must be 18+ to enter legally binding lease contracts in SA.
                if ("Landlord".equals(role) && age < 18) {
                    Toast.makeText(this, "Under South African Contract Law, Landlords must be 18 or older.", Toast.LENGTH_LONG).show();
                    return;
                }
    
                // 5. Encrypt the ID locally and save to Firebase
                String encryptedId = IdSecurityHelper.encryptId(enteredId);
    
                if (encryptedId == null) {
                    Toast.makeText(this, "Encryption failed. Please try again.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Retrieve other details
                String email = getIntent().getStringExtra("USER_EMAIL");
                String password = getIntent().getStringExtra("USER_PASSWORD");
                String firstName = getIntent().getStringExtra("USER_NAME");
                String surname = getIntent().getStringExtra("USER_SURNAME");
                String phone = getIntent().getStringExtra("USER_PHONE");

                if (email == null || password == null) {
                    Toast.makeText(this, "Missing user details. Please restart registration.", Toast.LENGTH_LONG).show();
                    return;
                }

                // Disable button to prevent double-clicks
                registerBtn.setEnabled(false);
                Toast.makeText(PersonalActivity.this, "Registering account...", Toast.LENGTH_SHORT).show();

                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Account created! Now save the rest of the profile data
                                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                                Map<String, Object> user = new HashMap<>();
                                user.put("firstName", firstName);
                                user.put("surname", surname);
                                user.put("phone", phone);
                                user.put("email", email);
                                user.put("role", role);
                                
                                // Location info
                                user.put("province", province);
                                user.put("city", city);
                                user.put("suburb", suburb);

                                // Save the encrypted ID
                                user.put("secureId", encryptedId);

                                FirebaseFirestore.getInstance().collection("Users").document(userId)
                                        .set(user)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d("iKayaAuth", "SUCCESS: User profile securely saved to Firestore!");
                                            Toast.makeText(PersonalActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                                            // Redirect to Login page after successful registration
                                            Intent intent = new Intent(PersonalActivity.this, LoginActivity.class);
                                            startActivity(intent);
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("iKayaAuth", "ERROR: Failed to save to Firestore", e);
                                            registerBtn.setEnabled(true);
                                            Toast.makeText(PersonalActivity.this, "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        });

                            } else {
                                registerBtn.setEnabled(true);
                                Toast.makeText(PersonalActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            });
        }
    }

    private void fetchLocation() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            Toast.makeText(this, "Location found! Resolving address...", Toast.LENGTH_SHORT).show();
                            
                            // Reverse geocoding in a background thread to prevent blocking the UI
                            executorService.execute(() -> {
                                try {
                                    List<Address> addresses;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1, this::handleAddresses);
                                    } else {
                                        addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                                        handleAddresses(addresses);
                                    }
                                } catch (IOException e) {
                                    Log.e("PersonalActivity", "Geocoding failed", e);
                                    runOnUiThread(() -> Toast.makeText(PersonalActivity.this, "Network error: Make sure you have an internet connection.", Toast.LENGTH_SHORT).show());
                                }
                            });
                        } else {
                            Toast.makeText(this, "Current location is unavailable. Try opening Google Maps first.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(this, e -> Toast.makeText(this, "Error fetching location: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (SecurityException e) {
            Log.e("PersonalActivity", "Permission lost unexpectedly", e);
            Toast.makeText(this, "Permission lost unexpectedly.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleAddresses(List<Address> addresses) {
        if (addresses != null && !addresses.isEmpty()) {
            Address address = addresses.get(0);
            
            String province = address.getAdminArea();
            String city = address.getLocality();
            if (city == null) {
                city = address.getSubAdminArea(); // Fallback
            }
            String suburb = address.getSubLocality();
            
            final String finalProvince = province != null ? province : "";
            final String finalCity = city != null ? city : "";
            final String finalSuburb = suburb != null ? suburb : "";
            
            // Update UI on the main thread
            runOnUiThread(() -> {
                provinceInput.setText(finalProvince);
                cityInput.setText(finalCity);
                suburbInput.setText(finalSuburb);
                Toast.makeText(PersonalActivity.this, "Address updated!", Toast.LENGTH_SHORT).show();
            });
        } else {
            runOnUiThread(() -> Toast.makeText(PersonalActivity.this, "Could not find a physical address for this location.", Toast.LENGTH_SHORT).show());
        }
    }
}