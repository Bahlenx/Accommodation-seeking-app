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

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PersonalActivity extends AppCompatActivity {

    private EditText idInput;
    private EditText provinceInput;
    private EditText cityInput;
    private EditText suburbInput;

    private Button registerBtn;
    private Button gpsBtn;

    private FusedLocationProviderClient fusedLocationClient;
    private Geocoder geocoder;

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {

                        boolean fineLocationGranted =
                                Boolean.TRUE.equals(
                                        result.getOrDefault(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                false
                                        )
                                );

                        boolean coarseLocationGranted =
                                Boolean.TRUE.equals(
                                        result.getOrDefault(
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                false
                                        )
                                );

                        if (fineLocationGranted
                                || coarseLocationGranted) {

                            fetchLocation();

                        } else {

                            Toast.makeText(
                                    this,
                                    "Location permission denied. Cannot fetch GPS.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_userverification
        );

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        geocoder =
                new Geocoder(
                        this,
                        Locale.getDefault()
                );

        idInput =
                findViewById(R.id.idNum);

        provinceInput =
                findViewById(R.id.provinceInputText);

        cityInput =
                findViewById(R.id.cityInputText);

        suburbInput =
                findViewById(R.id.suburbInputText);

        registerBtn =
                findViewById(R.id.registrationBtn);

        gpsBtn =
                findViewById(R.id.GPSbtn);

        ImageButton backButton =
                findViewById(R.id.back_button);

        if (backButton != null) {
            backButton.setOnClickListener(
                    v -> finish()
            );
        }

        if (gpsBtn != null) {

            gpsBtn.setOnClickListener(v -> {

                boolean fineGranted =
                        ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;

                boolean coarseGranted =
                        ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;

                if (fineGranted || coarseGranted) {

                    fetchLocation();

                } else {

                    locationPermissionRequest.launch(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            }
                    );
                }
            });
        }

        if (registerBtn != null) {

            registerBtn.setOnClickListener(
                    v -> registerUser()
            );
        }
    }

    private void registerUser() {

        String enteredId =
                idInput.getText()
                        .toString()
                        .trim();

        String province =
                provinceInput.getText()
                        .toString()
                        .trim();

        String city =
                cityInput.getText()
                        .toString()
                        .trim();

        String suburb =
                suburbInput.getText()
                        .toString()
                        .trim();

        if (enteredId.isEmpty()
                || province.isEmpty()
                || city.isEmpty()
                || suburb.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please fill in all fields",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!province.matches("[a-zA-Z\\s-]+")) {
            provinceInput.setError(null);
            Toast.makeText(this, "Please enter a valid province name", Toast.LENGTH_SHORT).show();
            provinceInput.requestFocus();
            return;
        }

        if (!city.matches("[a-zA-Z\\s-]+")) {
            cityInput.setError(null);
            Toast.makeText(this, "Please enter a valid city name", Toast.LENGTH_SHORT).show();
            cityInput.requestFocus();
            return;
        }

        if (!suburb.matches("[a-zA-Z0-9\\s-]+")) {
            suburbInput.setError(null);
            Toast.makeText(this, "Please enter a valid suburb name", Toast.LENGTH_SHORT).show();
            suburbInput.requestFocus();
            return;
        }

        String role =
                getIntent().getStringExtra(
                        "USER_ROLE"
                );

        if (role == null) {
            role = "Resident";
        }

        if (!IdSecurityHelper.isValidLuhn(enteredId)) {

            Toast.makeText(
                    this,
                    "Invalid ID Number format detected.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        int age =
                IdSecurityHelper.getAgeAndValidateDate(
                        enteredId
                );

        if (age == -1) {

            Toast.makeText(
                    this,
                    "Invalid Date of Birth in ID.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if ("Landlord".equalsIgnoreCase(role)
                && age < 18) {

            Toast.makeText(
                    this,
                    "Landlords must be 18 or older.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String email =
                getIntent().getStringExtra(
                        "USER_EMAIL"
                );

        String password =
                getIntent().getStringExtra(
                        "USER_PASSWORD"
                );

        String firstName =
                getIntent().getStringExtra(
                        "USER_NAME"
                );

        String surname =
                getIntent().getStringExtra(
                        "USER_SURNAME"
                );

        String phone =
                getIntent().getStringExtra(
                        "USER_PHONE"
                );

        if (email == null
                || password == null
                || email.isEmpty()
                || password.isEmpty()) {

            Toast.makeText(
                    this,
                    "Missing account details. Please restart registration.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (firstName == null) {
            firstName = "";
        }

        if (surname == null) {
            surname = "";
        }

        if (phone == null) {
            phone = "";
        }

        registerBtn.setEnabled(false);

        final String finalRole =
                "landlord".equalsIgnoreCase(role)
                        ? "landlord"
                        : "tenant";

        String existingToken = SupabaseHelper.getAccessToken(this);
        String existingUserId = SupabaseHelper.getUserId(this);
        boolean isOtpVerified = getIntent().getBooleanExtra("OTP_VERIFIED", false);

        if (isOtpVerified && existingToken != null && !existingToken.isEmpty() && existingUserId != null && !existingUserId.isEmpty()) {
            saveProfile(existingToken, existingUserId, finalRole, firstName, surname, phone);
        } else {
            createSupabaseAccount(
                    email,
                    password,
                    firstName,
                    surname,
                    phone,
                    finalRole,
                    province,
                    city,
                    suburb
            );
        }
    }

    private void createSupabaseAccount(
            String email,
            String password,
            String firstName,
            String surname,
            String phone,
            String role,
            String province,
            String city,
            String suburb
    ) {

        executorService.execute(() -> {

            HttpURLConnection connection = null;

            try {

                String existingToken = SupabaseHelper.getAccessToken(PersonalActivity.this);
                if (existingToken != null && !existingToken.isEmpty()) {
                    connection = SupabaseHelper.createConnection("/auth/v1/user", "PUT", existingToken);
                } else {
                    connection = SupabaseHelper.createConnection("/auth/v1/signup", "POST", null);
                }

                connection.setDoOutput(true);

                JSONObject body =
                        new JSONObject();

                body.put(
                        "email",
                        email
                );

                body.put(
                        "password",
                        password
                );

                JSONObject metadata =
                        new JSONObject();

                metadata.put(
                        "first_name",
                        firstName
                );

                metadata.put(
                        "surname",
                        surname
                );

                metadata.put(
                        "phone",
                        phone
                );

                metadata.put(
                        "role",
                        role
                );

                metadata.put(
                        "province",
                        province
                );

                metadata.put(
                        "city",
                        city
                );

                metadata.put(
                        "suburb",
                        suburb
                );

                body.put(
                        "data",
                        metadata
                );

                SupabaseHelper.writeJson(
                        connection,
                        body
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(
                                connection
                        );

                if (responseCode >= 200
                        && responseCode < 300) {

                    JSONObject data =
                            new JSONObject(response);

                    String accessToken =
                            data.optString(
                                    "access_token",
                                    ""
                            );

                    String refreshToken =
                            data.optString(
                                    "refresh_token",
                                    ""
                            );

                    JSONObject user =
                            data.optJSONObject("user");

                    if (user == null) {

                        showRegistrationError(
                                "Account creation failed: no user was returned."
                        );

                        return;
                    }

                    String userId =
                            user.optString(
                                    "id",
                                    ""
                            );

                    if (userId.isEmpty()) {

                        showRegistrationError(
                                "Account creation failed: no user ID was returned."
                        );

                        return;
                    }

                    if (accessToken.isEmpty()) {

                        runOnUiThread(() -> {

                            registerBtn.setEnabled(true);

                            Toast.makeText(
                                    PersonalActivity.this,
                                    "Account created. Please confirm your email, then log in.",
                                    Toast.LENGTH_LONG
                            ).show();

                            Intent intent =
                                    new Intent(
                                            PersonalActivity.this,
                                            LoginActivity.class
                                    );

                            intent.setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                            );

                            startActivity(intent);
                            finish();
                        });

                        return;
                    }

                    SupabaseHelper.saveSession(PersonalActivity.this, accessToken, refreshToken, userId, null, null);

                    saveProfile(
                            accessToken,
                            userId,
                            role,
                            firstName,
                            surname,
                            phone
                    );

                } else {

                    showRegistrationError(
                            "Supabase registration error: "
                                    + responseCode
                                    + "\n"
                                    + extractErrorMessage(response)
                    );
                }

            } catch (Exception e) {

                Log.e(
                        "iKhayaSupabase",
                        "Supabase registration failed",
                        e
                );

                showRegistrationError(
                        "Database Error: "
                                + e.getMessage()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void saveProfile(
            String accessToken,
            String userId,
            String role,
            String firstName,
            String surname,
            String phone
    ) {

        executorService.execute(() -> {

            HttpURLConnection connection = null;

            try {

                connection =
                        SupabaseHelper.createConnection(
                                "/rest/v1/profiles?on_conflict=firebase_uid",
                                "POST",
                                accessToken
                        );

                connection.setRequestProperty(
                        "Prefer",
                        "resolution=merge-duplicates,return=minimal"
                );

                connection.setDoOutput(true);

                JSONObject profile =
                        new JSONObject();

                profile.put(
                        "firebase_uid",
                        userId
                );

                String fullName =
                        (firstName + " " + surname).trim();

                profile.put(
                        "full_name",
                        fullName
                );

                profile.put(
                        "phone",
                        phone
                );

                profile.put(
                        "role",
                        role
                );

                SupabaseHelper.writeJson(
                        connection,
                        profile
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(
                                connection
                        );

                if (responseCode >= 200
                        && responseCode < 300) {

                    runOnUiThread(() -> {

                        SupabaseHelper.saveSession(PersonalActivity.this, null, null, null, fullName, role);

                        Toast.makeText(
                                PersonalActivity.this,
                                "Registration Successful!",
                                Toast.LENGTH_SHORT
                        ).show();

                        Intent intent;

                        if ("landlord".equalsIgnoreCase(role)) {

                            intent =
                                    new Intent(
                                            PersonalActivity.this,
                                            LandlordDashboardActivity.class
                                    );

                        } else {

                            intent =
                                    new Intent(
                                            PersonalActivity.this,
                                            HomeActivity.class
                                    );
                        }

                        intent.setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );

                        startActivity(intent);
                        finish();
                    });

                } else {

                    showRegistrationError(
                            "Profile database error: "
                                    + responseCode
                                    + "\n"
                                    + extractErrorMessage(response)
                    );
                }

            } catch (Exception e) {

                Log.e(
                        "iKhayaSupabase",
                        "Profile creation failed",
                        e
                );

                showRegistrationError(
                        "Profile database error: "
                                + e.getMessage()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showRegistrationError(String message) {

        runOnUiThread(() -> {

            registerBtn.setEnabled(true);

            Toast.makeText(
                    PersonalActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private String extractErrorMessage(String response) {

        try {

            JSONObject error =
                    new JSONObject(response);

            String message =
                    error.optString(
                            "msg",
                            ""
                    );

            if (message.isEmpty()) {
                message =
                        error.optString(
                                "message",
                                ""
                        );
            }

            if (message.isEmpty()) {
                message =
                        error.optString(
                                "error_description",
                                ""
                        );
            }

            if (!message.isEmpty()) {
                return message;
            }

        } catch (Exception ignored) {
        }

        return response.isEmpty()
                ? "Unknown error"
                : response;
    }

    private void fetchLocation() {

        try {

            fusedLocationClient
                    .getLastLocation()
                    .addOnSuccessListener(
                            this,
                            location -> {

                                if (location != null) {

                                    Toast.makeText(
                                            this,
                                            "Location found! Resolving address...",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    executorService.execute(() -> {

                                        try {

                                            if (Build.VERSION.SDK_INT
                                                    >= Build.VERSION_CODES.TIRAMISU) {

                                                geocoder.getFromLocation(
                                                        location.getLatitude(),
                                                        location.getLongitude(),
                                                        1,
                                                        this::handleAddresses
                                                );

                                            } else {

                                                List<Address> addresses =
                                                        geocoder.getFromLocation(
                                                                location.getLatitude(),
                                                                location.getLongitude(),
                                                                1
                                                        );

                                                handleAddresses(
                                                        addresses
                                                );
                                            }

                                        } catch (IOException e) {

                                            runOnUiThread(
                                                    () -> Toast.makeText(
                                                            PersonalActivity.this,
                                                            "Network error while resolving address.",
                                                            Toast.LENGTH_SHORT
                                                    ).show()
                                            );
                                        }
                                    });

                                } else {

                                    Toast.makeText(
                                            this,
                                            "Current location is unavailable.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    )
                    .addOnFailureListener(
                            this,
                            e -> Toast.makeText(
                                    this,
                                    "Error fetching location: "
                                            + e.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show()
                    );

        } catch (SecurityException e) {

            Toast.makeText(
                    this,
                    "Location permission is required.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void handleAddresses(
            List<Address> addresses
    ) {

        if (addresses != null
                && !addresses.isEmpty()) {

            Address address =
                    addresses.get(0);

            String province =
                    address.getAdminArea();

            String city =
                    address.getLocality();

            if (city == null) {
                city =
                        address.getSubAdminArea();
            }

            String suburb =
                    address.getSubLocality();

            final String finalProvince =
                    province != null
                            ? province
                            : "";

            final String finalCity =
                    city != null
                            ? city
                            : "";

            final String finalSuburb =
                    suburb != null
                            ? suburb
                            : "";

            runOnUiThread(() -> {

                provinceInput.setText(
                        finalProvince
                );

                cityInput.setText(
                        finalCity
                );

                suburbInput.setText(
                        finalSuburb
                );

                Toast.makeText(
                        PersonalActivity.this,
                        "Address updated!",
                        Toast.LENGTH_SHORT
                ).show();
            });

        } else {

            runOnUiThread(
                    () -> Toast.makeText(
                            PersonalActivity.this,
                            "Could not find a physical address for this location.",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executorService.shutdown();
    }
}