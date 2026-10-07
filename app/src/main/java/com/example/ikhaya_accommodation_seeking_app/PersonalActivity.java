package com.example.ikhaya_accommodation_seeking_app;

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
// Removed unused Firestore import as we are migrating to Supabase

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

                // Call Supabase registration (bypassing Firebase completely)
                proceedToSupabaseBackend(encryptedId);
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
            
            // 1. Province Field: Exact match for the state/province
            String province = address.getAdminArea();
            
            // 2. City Field: Primary locality, fallback to sub-admin area (district/municipality)
            String city = address.getLocality();
            if (city == null || city.trim().isEmpty()) {
                city = address.getSubAdminArea(); // Fallback
            }
            
            // 3. Suburb Field: Sub-locality, fallback to address line string parsing
            String suburb = address.getSubLocality();
            if (suburb == null || suburb.trim().isEmpty()) {
                // Android Geocoder AddressLine(0) usually looks like: "123 Main St, Midrand, Johannesburg, 1685, South Africa"
                String fullAddress = address.getAddressLine(0);
                if (fullAddress != null && !fullAddress.trim().isEmpty()) {
                    String[] parts = fullAddress.split(",");
                    if (parts.length > 1) {
                        // If the first part contains a number (e.g., street number), the suburb/neighborhood is usually the second part.
                        if (parts[0].matches(".*\\d.*")) {
                            suburb = parts[1].trim();
                        } else {
                            // Otherwise, take the very first part
                            suburb = parts[0].trim();
                        }
                    } else {
                        suburb = parts[0].trim();
                    }
                }
            }
            
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

    private void proceedToSupabaseBackend(String encryptedId) {
        // The teammate will use these variables in the JSON body of the Supabase POST request.
        String province = provinceInput.getText().toString().trim();
        String city = cityInput.getText().toString().trim();
        String suburb = suburbInput.getText().toString().trim();
        String role = getIntent().getStringExtra("USER_ROLE") != null ? getIntent().getStringExtra("USER_ROLE") : "Resident";
        String firstName = getIntent().getStringExtra("USER_NAME");
        String surname = getIntent().getStringExtra("USER_SURNAME");
        String phone = getIntent().getStringExtra("USER_PHONE");
        
        executorService.execute(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                // 1. Sign up user via Supabase Auth API
                String endpoint = "https://wdtazumjjurumrhklwgv.supabase.co/auth/v1/signup";
                java.net.URL url = new java.net.URL(endpoint);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("apikey", "sb_publishable_SgXZRNFBL4tswVtapc3pFQ_qpD-DijB");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                
                String email = getIntent().getStringExtra("USER_EMAIL");
                String password = getIntent().getStringExtra("USER_PASSWORD");
                
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("email", email);
                body.put("password", password);
                
                connection.getOutputStream().write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode >= 200 && responseCode < 300) {
                    // 2. Read Response for access_token and UUID
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    reader.close();
                    
                    org.json.JSONObject data = new org.json.JSONObject(result.toString());
                    String accessToken = data.optString("access_token", "");
                    org.json.JSONObject user = data.optJSONObject("user");
                    
                    if (!accessToken.isEmpty() && user != null) {
                        String supabaseUserId = user.optString("id", "");
                        
                        // 3. Save profile to Supabase Users table
                        saveProfileToSupabase(supabaseUserId, accessToken, firstName, surname, email, phone, role, province, city, suburb, encryptedId);
                        
                        // Log device security event after successful registration
                        logDeviceSecurityEvent(supabaseUserId, accessToken);
                        
                        // 4. Save session locally
                        getSharedPreferences("iKhayaSession", MODE_PRIVATE)
                            .edit()
                            .putString("access_token", accessToken)
                            .putString("user_id", supabaseUserId)
                            .apply();
                            
                        // 5. Route user
                        runOnUiThread(() -> {
                            Toast.makeText(PersonalActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                            Intent homeIntent;
                            if ("Landlord".equalsIgnoreCase(role)) {
                                homeIntent = new Intent(PersonalActivity.this, LandlordDashboardActivity.class);
                            } else {
                                homeIntent = new Intent(PersonalActivity.this, HomeActivity.class);
                            }
                            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(homeIntent);
                            finish();
                        });
                    }
                } else {
                    java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResult = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResult.append(errorLine);
                    }
                    errorReader.close();
                    
                    String errorMsg = "Registration Failed";
                    try {
                        org.json.JSONObject errorJson = new org.json.JSONObject(errorResult.toString());
                        errorMsg = errorJson.optString("msg", "Registration Failed");
                    } catch (Exception ignored) {}
                    
                    final String finalError = errorMsg;
                    runOnUiThread(() -> {
                        registerBtn.setEnabled(true);
                        Toast.makeText(PersonalActivity.this, finalError, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    registerBtn.setEnabled(true);
                    Toast.makeText(PersonalActivity.this, "Network Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void saveProfileToSupabase(String userId, String accessToken, String firstName, String surname, String email, String phone, String role, String province, String city, String suburb, String encryptedId) {
        java.net.HttpURLConnection connection = null;
        try {
            String endpoint = "https://wdtazumjjurumrhklwgv.supabase.co/rest/v1/Users";
            java.net.URL url = new java.net.URL(endpoint);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("apikey", "sb_publishable_SgXZRNFBL4tswVtapc3pFQ_qpD-DijB");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "return=minimal");
            connection.setDoOutput(true);
            
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("id", userId);
            body.put("firstName", firstName);
            body.put("surname", surname);
            body.put("email", email);
            body.put("phone", phone);
            body.put("user_role", role);
            body.put("province", province);
            body.put("city", city);
            body.put("suburb", suburb);
            body.put("secureId", encryptedId); 

            connection.getOutputStream().write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            connection.getResponseCode(); // Execute
            
        } catch (Exception e) {
            android.util.Log.e("SupabaseIntegration", "Failed to save profile", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void logDeviceSecurityEvent(String userId, String accessToken) {
        try {
            // Fetch IP Address
            String ipAddress = "Unknown";
            try {
                java.net.URL ipUrl = new java.net.URL("https://api.ipify.org");
                java.net.HttpURLConnection ipConn = (java.net.HttpURLConnection) ipUrl.openConnection();
                ipConn.setConnectTimeout(5000);
                ipConn.setReadTimeout(5000);
                ipConn.setRequestMethod("GET");
                if (ipConn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(ipConn.getInputStream()));
                    ipAddress = reader.readLine();
                    reader.close();
                }
                ipConn.disconnect();
            } catch (Exception ignored) {
                // Ignore if it fails, IP will remain Unknown
            }

            // Push to Supabase
            String endpoint = "https://wdtazumjjurumrhklwgv.supabase.co/rest/v1/security_logs";
            java.net.URL url = new java.net.URL(endpoint);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("apikey", "sb_publishable_SgXZRNFBL4tswVtapc3pFQ_qpD-DijB");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            org.json.JSONObject body = new org.json.JSONObject();
            body.put("user_id", userId);
            body.put("manufacturer", android.os.Build.MANUFACTURER);
            body.put("model", android.os.Build.MODEL);
            body.put("os_version", android.os.Build.VERSION.RELEASE);
            body.put("time_zone", java.util.TimeZone.getDefault().getID());
            body.put("ip_address", ipAddress);

            connection.getOutputStream().write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            connection.getResponseCode(); // Execute the request
            connection.disconnect();

        } catch (Exception e) {
            android.util.Log.e("SupabaseSecurity", "Failed to log security event", e);
        }
    }
}