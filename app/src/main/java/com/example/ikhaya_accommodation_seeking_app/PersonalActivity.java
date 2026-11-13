package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
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
import java.io.InputStream;
import java.io.OutputStream;
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

        String passedSaId = getIntent().getStringExtra("USER_SA_ID");
        if (passedSaId != null && !passedSaId.isEmpty()) {
            idInput.setText(passedSaId);
            idInput.setEnabled(false);
            idInput.setFocusable(false);
        }

        provinceInput =
                findViewById(R.id.provinceInputText);

        cityInput =
                findViewById(R.id.cityInputText);

        suburbInput =
                findViewById(R.id.suburbInputText);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (provinceInput != null) provinceInput.setAutofillHints("postalAddressRegion");
            if (cityInput != null) cityInput.setAutofillHints("postalAddressLocality");
            if (suburbInput != null) suburbInput.setAutofillHints("postalAddressStreetAddress");
        }

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
        
        android.view.View btnSupportMail = findViewById(R.id.btnSupportMail);
        if (btnSupportMail != null) {
            btnSupportMail.setOnClickListener(v -> {
                String userHint = getIntent().getStringExtra("EMAIL") != null ? getIntent().getStringExtra("EMAIL") : "";
                SupabaseHelper.openAdminSupportChat(this, userHint);
            });
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

        if (enteredId.isEmpty()) {
            SupabaseHelper.showValidationError(this, idInput, "Please enter your 13-digit South African ID number.");
            return;
        } else if (province.isEmpty() || city.isEmpty() || suburb.isEmpty()) {
            android.widget.EditText firstEmpty = province.isEmpty() ? provinceInput : (city.isEmpty() ? cityInput : suburbInput);
            SupabaseHelper.showValidationError(this, firstEmpty, "Please tap 'Get GPS Location' to auto-fill your address, or fill in Province, City, and Suburb.");
            return;
        }

        if (!province.matches("[a-zA-Z0-9\\s.,'()/&-]+")) {
            provinceInput.setError(null);
            Toast.makeText(this, "Please enter a valid province name", Toast.LENGTH_SHORT).show();
            provinceInput.requestFocus();
            return;
        }

        if (!city.matches("[a-zA-Z0-9\\s.,'()/&-]+")) {
            cityInput.setError(null);
            Toast.makeText(this, "Please enter a valid city name", Toast.LENGTH_SHORT).show();
            cityInput.requestFocus();
            return;
        }

        if (!suburb.matches("[a-zA-Z0-9\\s.,'()/&-]+")) {
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
                            "Registration could not be completed: "
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

                String email = getIntent().getStringExtra("USER_EMAIL");
                if (email != null && !email.trim().isEmpty()) {
                    profile.put("email", email.trim());
                }

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

                String prov = provinceInput.getText().toString().trim();
                String cit = cityInput.getText().toString().trim();
                String sub = suburbInput.getText().toString().trim();

                profile.put("province", prov);
                profile.put("city", cit);
                profile.put("suburb", sub);

                String saId = idInput.getText().toString().trim();
                if (!saId.isEmpty()) {
                    profile.put("sa_id_number", saId);
                }

                if ("landlord".equalsIgnoreCase(role)) {
                    String idDocUriStr = getIntent().getStringExtra("USER_ID_DOC_URI");
                    String selfieUriStr = getIntent().getStringExtra("USER_SELFIE_URI");

                    if (idDocUriStr != null && !idDocUriStr.isEmpty()) {
                        String docUrl = uploadFileToSupabaseStorage(Uri.parse(idDocUriStr), "identity/doc_" + userId + "_" + System.currentTimeMillis() + ".jpg", accessToken);
                        if (docUrl != null) {
                            profile.put("id_document_url", docUrl);
                        }
                    }

                    if (selfieUriStr != null && !selfieUriStr.isEmpty()) {
                        String selfieUrl = uploadFileToSupabaseStorage(Uri.parse(selfieUriStr), "identity/selfie_" + userId + "_" + System.currentTimeMillis() + ".jpg", accessToken);
                        if (selfieUrl != null) {
                            profile.put("selfie_url", selfieUrl);
                            profile.put("verification_selfie_url", selfieUrl);
                        }
                    }

                    profile.put("identity_status", "PENDING_VERIFICATION");
                    profile.put("is_identity_verified", false);
                }

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

    @SuppressWarnings("deprecation")
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

    private String cleanAddressPart(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "South Africa".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private String cleanMunicipalityToCity(String subAdmin) {
        String s = cleanAddressPart(subAdmin);
        if (s.isEmpty()) return "";
        s = s.replace("City of ", "")
             .replace(" Metropolitan Municipality", "")
             .replace(" Local Municipality", "")
             .replace(" District Municipality", "")
             .replace(" Municipality", "")
             .trim();
        if ("Tshwane".equalsIgnoreCase(s)) return "Pretoria";
        return s;
    }

    private void handleAddresses(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            runOnUiThread(
                    () -> Toast.makeText(
                            PersonalActivity.this,
                            "Could not find a physical address for this location.",
                            Toast.LENGTH_SHORT
                    ).show()
            );
            return;
        }

        Address address = addresses.get(0);
        String province = cleanAddressPart(address.getAdminArea());
        String rawLocality = cleanAddressPart(address.getLocality());
        String rawSubLocality = cleanAddressPart(address.getSubLocality());
        String rawSubAdmin = cleanMunicipalityToCity(address.getSubAdminArea());
        String rawThoroughfare = cleanAddressPart(address.getThoroughfare());

        String city = "";
        String suburb = "";

        if (!rawSubLocality.isEmpty() && !rawLocality.isEmpty() && !rawSubLocality.equalsIgnoreCase(rawLocality)) {
            suburb = rawSubLocality;
            city = rawLocality;
        } else if (rawSubLocality.isEmpty() && !rawLocality.isEmpty()) {
            String fullLine = address.getAddressLine(0);
            String parsedSuburbFromLine = "";
            if (fullLine != null && fullLine.contains(",")) {
                String[] parts = fullLine.split(",");
                for (String rawPart : parts) {
                    String p = cleanAddressPart(rawPart);
                    if (p.isEmpty() || p.matches(".*\\d{4}.*") || p.matches("^\\d+\\s+.*")) continue;
                    if (p.equalsIgnoreCase(province) || p.equalsIgnoreCase(rawLocality) || p.equalsIgnoreCase(rawSubAdmin)) continue;
                    parsedSuburbFromLine = p;
                    break;
                }
            }

            if (!parsedSuburbFromLine.isEmpty()) {
                suburb = parsedSuburbFromLine;
                city = rawLocality;
            } else if (!rawSubAdmin.isEmpty() && !rawSubAdmin.equalsIgnoreCase(rawLocality)) {
                suburb = rawLocality;
                city = rawSubAdmin;
            } else {
                city = rawLocality;
                suburb = !rawThoroughfare.isEmpty() ? rawThoroughfare : rawLocality;
            }
        } else if (!rawSubLocality.isEmpty()) {
            suburb = rawSubLocality;
            city = !rawSubAdmin.isEmpty() ? rawSubAdmin : rawSubLocality;
        } else if (!rawSubAdmin.isEmpty()) {
            city = rawSubAdmin;
            suburb = !rawThoroughfare.isEmpty() ? rawThoroughfare : rawSubAdmin;
        }

        if (province.isEmpty()) province = "Gauteng";
        if (city.isEmpty()) city = suburb.isEmpty() ? "Johannesburg" : suburb;
        if (suburb.isEmpty()) suburb = city;

        final String finalProvince = province;
        final String finalCity = city;
        final String finalSuburb = suburb;

        runOnUiThread(() -> {
            if (provinceInput != null) {
                provinceInput.setText(finalProvince);
                provinceInput.setError(null);
            }
            if (cityInput != null) {
                cityInput.setText(finalCity);
                cityInput.setError(null);
            }
            if (suburbInput != null) {
                suburbInput.setText(finalSuburb);
                suburbInput.setError(null);
            }
            Toast.makeText(PersonalActivity.this, "Location auto-filled!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executorService.shutdown();
    }

    private String uploadFileToSupabaseStorage(Uri uri, String storagePath, String accessToken) {
        HttpURLConnection connection = null;
        try {
            connection = SupabaseHelper.createStorageConnection(
                    "property-images",
                    storagePath,
                    "POST",
                    accessToken
            );

            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "image/jpeg";

            connection.setRequestProperty("Content-Type", mimeType);
            connection.setRequestProperty("x-upsert", "true");
            connection.setDoOutput(true);

            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = connection.getOutputStream()) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }

            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                return SupabaseHelper.getPublicStorageUrl("property-images", storagePath);
            }
        } catch (Exception e) {
            Log.e("PersonalActivity", "Upload error", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }
}