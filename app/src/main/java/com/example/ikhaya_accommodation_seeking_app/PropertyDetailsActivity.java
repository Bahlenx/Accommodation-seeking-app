package com.example.ikhaya_accommodation_seeking_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PropertyDetailsActivity extends AppCompatActivity {

    private static final String TAG = "PropertyDetails";

    private ImageView propertyImage;
    private TextView txtPropertyName;
    private TextView txtPropertyPrice;
    private TextView txtPropertyLocation;
    private TextView propertyDescription;

    private ChipGroup chipGroupAmenities;
    private ChipGroup chipGroupRules;
    private MapView mapView;
    private ProgressBar progressTranslate;
    private TextView btnTranslate;
    private Button btnApplyToRent;

    private boolean isTranslated = false;
    private String originalDescription = "";
    private String cachedTranslatedDescription = "";

    private String listingId;
    private String landlordUid = "";
    private String locationText = "";
    private double verifiedLatitude = Double.NaN;
    private double verifiedLongitude = Double.NaN;
    private String propertyName = "";
    private String priceText = "";

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(
                        getApplicationContext()
                )
        );

        Configuration.getInstance().setUserAgentValue(
                getPackageName()
        );

        setContentView(R.layout.activity_property_details);

        listingId = getIntent().getStringExtra("LISTING_ID");

        if (listingId == null) {
            listingId = getIntent().getStringExtra("listingId");
        }

        if (getIntent().hasExtra("latitude")
                && getIntent().hasExtra("longitude")) {

            verifiedLatitude =
                    getIntent().getDoubleExtra(
                            "latitude",
                            Double.NaN
                    );

            verifiedLongitude =
                    getIntent().getDoubleExtra(
                            "longitude",
                            Double.NaN
                    );
        }

        initializeViews();
        setupEmbeddedMap();
        setupButtons();
        setupChipsAndDescription();
        setupRoleViews();

        if (listingId != null && !listingId.trim().isEmpty()) {
            loadListingFromSupabase();
        }
    }

    private void initializeViews() {

        propertyImage = findViewById(R.id.propertyImage);
        txtPropertyName = findViewById(R.id.propertyName);
        txtPropertyPrice = findViewById(R.id.propertyPrice);
        txtPropertyLocation = findViewById(R.id.propertyLocation);
        propertyDescription = findViewById(R.id.propertyDescription);

        chipGroupAmenities = findViewById(R.id.chipGroupAmenities);
        chipGroupRules = findViewById(R.id.chipGroupRules);

        progressTranslate = findViewById(R.id.progressTranslate);
        btnTranslate = findViewById(R.id.btnTranslate);

        Button btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void setupChipsAndDescription() {

        if (chipGroupAmenities != null) {
            chipGroupAmenities.removeAllViews();
        }

        if (chipGroupRules != null) {
            chipGroupRules.removeAllViews();
        }

        if (propertyDescription != null) {
            propertyDescription.setText("");
        }

        Locale currentLocale =
                getResources().getConfiguration()
                        .getLocales()
                        .get(0);

        String currentLang = currentLocale.getLanguage();

        if (btnTranslate != null) {

            btnTranslate.setVisibility(View.GONE);

            btnTranslate.setOnClickListener(v -> {

                if (originalDescription.trim().isEmpty()) {
                    Toast.makeText(
                            this,
                            getString(
                                    R.string.description_not_available
                            ),
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                if (isTranslated) {

                    propertyDescription.setText(
                            originalDescription
                    );

                    btnTranslate.setText(
                            getString(
                                    R.string.translate_to_language,
                                    getLanguageDisplayName(currentLang)
                            )
                    );

                    isTranslated = false;
                    return;
                }

                if (!cachedTranslatedDescription.isEmpty()) {

                    propertyDescription.setText(
                            cachedTranslatedDescription
                    );

                    btnTranslate.setText(
                            getString(R.string.show_original)
                    );

                    isTranslated = true;
                    return;
                }

                if (progressTranslate != null) {
                    progressTranslate.setVisibility(View.VISIBLE);
                }

                btnTranslate.setEnabled(false);

                GeminiTranslator.translate(
                        originalDescription,
                        currentLang,
                        new GeminiTranslator.TranslationCallback() {

                            @Override
                            public void onSuccess(String result) {

                                if (progressTranslate != null) {
                                    progressTranslate.setVisibility(
                                            View.GONE
                                    );
                                }

                                btnTranslate.setEnabled(true);
                                cachedTranslatedDescription = result;

                                if (propertyDescription != null) {
                                    propertyDescription.setText(result);
                                }

                                btnTranslate.setText(
                                        getString(R.string.show_original)
                                );

                                isTranslated = true;
                            }

                            @Override
                            public void onError(String errorMessage) {

                                if (progressTranslate != null) {
                                    progressTranslate.setVisibility(
                                            View.GONE
                                    );
                                }

                                btnTranslate.setEnabled(true);

                                Toast.makeText(
                                        PropertyDetailsActivity.this,
                                        getString(
                                                R.string.translation_failed,
                                                errorMessage
                                        ),
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                );
            });
        }
    }

    private void populateChipGroup(
            ChipGroup group,
            Object rawValues
    ) {

        if (group == null
                || rawValues == null
                || rawValues == JSONObject.NULL) {
            return;
        }

        group.removeAllViews();

        if (rawValues instanceof JSONArray) {

            JSONArray values = (JSONArray) rawValues;

            for (int i = 0; i < values.length(); i++) {
                addChip(group, values.optString(i, ""));
            }

        } else {

            String raw = String.valueOf(rawValues).trim();

            if (raw.isEmpty()
                    || "null".equalsIgnoreCase(raw)) {
                return;
            }

            for (String part : raw.split(",")) {
                addChip(group, part);
            }
        }
    }

    private void addChip(
            ChipGroup group,
            String value
    ) {

        if (value == null || value.trim().isEmpty()) {
            return;
        }

        Chip chip = new Chip(this);
        chip.setText(value.trim());
        chip.setClickable(false);
        chip.setCheckable(false);

        group.addView(chip);
    }

    private void setupButtons() {

        Button btnGetDirections =
                findViewById(R.id.btnGetDirections);

        Button btnOpenFullMap =
                findViewById(R.id.btnOpenFullMap);

        View btnOpenMap = findViewById(R.id.btnOpenMap);
        View btnSave = findViewById(R.id.btnSave);
        View btnContact = findViewById(R.id.btnContactLandlord);

        if (btnGetDirections != null) {
            btnGetDirections.setOnClickListener(
                    v -> launchTurnByTurnNavigation()
            );
        }

        if (btnOpenFullMap != null) {
            btnOpenFullMap.setOnClickListener(
                    v -> openLocationInMaps(false)
            );
        }

        if (btnOpenMap != null) {
            btnOpenMap.setOnClickListener(
                    v -> openLocationInMaps(false)
            );
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveListing());
        }

        if (btnContact != null) {
            btnContact.setOnClickListener(v -> contactLandlord());
        }
    }

    private void setupRoleViews() {

        boolean isLandlord =
                getIntent().getBooleanExtra("isLandlord", false);

        Button btnContactLandlord =
                findViewById(R.id.btnContactLandlord);

        Button btnEditListing =
                findViewById(R.id.btnEditListing);

        btnApplyToRent =
                findViewById(R.id.btnApplyToRent);

        if (isLandlord) {

            if (btnContactLandlord != null) {
                btnContactLandlord.setVisibility(View.GONE);
            }

            if (btnApplyToRent != null) {
                btnApplyToRent.setVisibility(View.GONE);
            }

            if (btnEditListing != null) {

                btnEditListing.setVisibility(View.VISIBLE);

                btnEditListing.setOnClickListener(v -> {

                    Intent editIntent = new Intent(
                            this,
                            AddListingActivity.class
                    );

                    editIntent.putExtra("isEditing", true);
                    editIntent.putExtra("listingId", listingId);
                    editIntent.putExtra("propertyName", propertyName);

                    startActivity(editIntent);
                });
            }

        } else {

            if (btnContactLandlord != null) {
                btnContactLandlord.setVisibility(View.VISIBLE);
            }

            if (btnEditListing != null) {
                btnEditListing.setVisibility(View.GONE);
            }

            if (btnApplyToRent != null) {

                btnApplyToRent.setVisibility(View.VISIBLE);

                btnApplyToRent.setText(
                        getString(R.string.btn_apply_to_rent)
                );

                btnApplyToRent.setOnClickListener(
                        v -> showApplicationBottomSheet()
                );
            }
        }
    }

    private void showApplicationBottomSheet() {

        BottomSheetDialog dialog =
                new BottomSheetDialog(this);

        dialog.setContentView(R.layout.dialog_apply_rental);

        ImageButton btnCloseDialog =
                dialog.findViewById(R.id.btnCloseDialog);

        TextView dialogTitle =
                dialog.findViewById(R.id.dialogPropertyTitle);

        EditText etApplicantName =
                dialog.findViewById(R.id.etApplicantName);

        EditText etIdNumber =
                dialog.findViewById(R.id.etIdNumber);

        EditText etPhone =
                dialog.findViewById(R.id.etPhone);

        EditText etEmergencyContact =
                dialog.findViewById(R.id.etEmergencyContact);

        Spinner spinnerOccupation =
                dialog.findViewById(R.id.spinnerOccupation);

        TextView tvSelectedMoveInDate =
                dialog.findViewById(R.id.tvSelectedMoveInDate);

        Spinner spinnerLeaseDuration =
                dialog.findViewById(R.id.spinnerLeaseDuration);

        Spinner spinnerOccupants =
                dialog.findViewById(R.id.spinnerOccupants);

        EditText etTenantNote =
                dialog.findViewById(R.id.etTenantNote);

        CheckBox cbAcceptTerms =
                dialog.findViewById(R.id.cbAcceptTerms);

        Button btnSubmit =
                dialog.findViewById(R.id.btnSubmitApplication);

        if (dialogTitle != null) {
            dialogTitle.setText(
                    getString(
                            R.string.application_dialog_title,
                            propertyName,
                            priceText
                    )
            );
        }

        if (btnCloseDialog != null) {

            btnCloseDialog.setOnClickListener(v -> {

                new AlertDialog.Builder(this)
                        .setTitle(
                                getString(R.string.dialog_discard_title)
                        )
                        .setMessage(
                                getString(R.string.dialog_discard_message)
                        )
                        .setPositiveButton(
                                getString(R.string.dialog_btn_discard),
                                (d, which) -> dialog.dismiss()
                        )
                        .setNegativeButton(
                                getString(R.string.dialog_btn_continue),
                                null
                        )
                        .show();
            });
        }

        Calendar calendar = Calendar.getInstance();

        final String[] chosenDate = {
                getString(R.string.default_move_in_date)
        };

        if (tvSelectedMoveInDate != null) {

            tvSelectedMoveInDate.setOnClickListener(v -> {

                Calendar cal = Calendar.getInstance();

                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            chosenDate[0] = String.format(
                                    Locale.getDefault(),
                                    "%02d/%02d/%d",
                                    dayOfMonth,
                                    month + 1,
                                    year
                            );

                            tvSelectedMoveInDate.setText(
                                    chosenDate[0]
                            );
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                ).show();
            });
        }

        if (btnSubmit != null) {

            btnSubmit.setOnClickListener(v -> {

                String name = etApplicantName != null
                        ? etApplicantName.getText().toString().trim()
                        : "";

                String idNum = etIdNumber != null
                        ? etIdNumber.getText().toString().trim()
                        : "";

                String phone = etPhone != null
                        ? etPhone.getText().toString().trim()
                        : "";

                String emergency = etEmergencyContact != null
                        ? etEmergencyContact.getText().toString().trim()
                        : "";

                if (name.isEmpty()) {
                    showFormError(R.string.err_enter_name);
                    return;
                }

                if (idNum.length() != 13) {
                    showFormError(R.string.err_enter_id);
                    return;
                }

                if (phone.length() != 10 || !phone.startsWith("0")) {
                    showFormError(R.string.err_enter_phone);
                    return;
                }

                if (emergency.length() != 10
                        || !emergency.startsWith("0")) {
                    showFormError(R.string.err_enter_emergency);
                    return;
                }

                if (cbAcceptTerms != null
                        && !cbAcceptTerms.isChecked()) {
                    showFormError(R.string.err_accept_terms);
                    return;
                }

                String occupation =
                        spinnerOccupation != null
                                && spinnerOccupation.getSelectedItem() != null
                                ? spinnerOccupation.getSelectedItem().toString()
                                : getString(R.string.default_occupation);

                String duration =
                        spinnerLeaseDuration != null
                                && spinnerLeaseDuration.getSelectedItem() != null
                                ? spinnerLeaseDuration.getSelectedItem().toString()
                                : getString(R.string.default_lease_duration);

                String occupants =
                        spinnerOccupants != null
                                && spinnerOccupants.getSelectedItem() != null
                                ? spinnerOccupants.getSelectedItem().toString()
                                : getString(R.string.default_occupants);

                String note =
                        etTenantNote != null
                                ? etTenantNote.getText().toString().trim()
                                : "";

                if (note.isEmpty()) {
                    note = getString(R.string.default_tenant_note);
                }

                String appId = "APP-" + (System.currentTimeMillis() % 10000);

                ApplicationManager.addApplication(
                        PropertyDetailsActivity.this,
                        new ApplicationManager.RentalApplication(
                                appId,
                                propertyName,
                                name,
                                phone,
                                emergency,
                                occupation + " (ID: " + idNum + ")",
                                chosenDate[0],
                                duration,
                                occupants,
                                priceText,
                                note,
                                getString(R.string.application_status_pending)
                        )
                );

                dialog.dismiss();

                Toast.makeText(
                        this,
                        getString(R.string.msg_toast_applied),
                        Toast.LENGTH_LONG
                ).show();

                if (btnApplyToRent != null) {

                    btnApplyToRent.setText(
                            getString(R.string.btn_pending_review)
                    );

                    btnApplyToRent.setEnabled(false);

                    btnApplyToRent.setBackgroundTintList(
                            ColorStateList.valueOf(Color.GRAY)
                    );
                }
            });
        }

        dialog.show();
    }

    private void showFormError(int messageResource) {

        Toast.makeText(
                this,
                getString(messageResource),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void setupEmbeddedMap() {

        mapView = findViewById(R.id.mapView);

        if (mapView == null) {
            return;
        }

        XYTileSource tileSource = new XYTileSource(
                "OpenStreetMapDE",
                0,
                19,
                256,
                ".png",
                new String[]{
                        "https://tile.openstreetmap.de/"
                },
                "© OpenStreetMap contributors"
        );

        mapView.setTileSource(tileSource);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        if (Double.isNaN(verifiedLatitude)
                || Double.isNaN(verifiedLongitude)
                || Double.isInfinite(verifiedLatitude)
                || Double.isInfinite(verifiedLongitude)) {
            mapView.invalidate();
            return;
        }

        GeoPoint point = new GeoPoint(
                verifiedLatitude,
                verifiedLongitude
        );

        mapView.getController().setCenter(point);

        Marker marker = new Marker(mapView);

        marker.setPosition(point);
        marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
        );

        marker.setTitle(propertyName);

        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    private void launchTurnByTurnNavigation() {

        if (Double.isNaN(verifiedLatitude)
                || Double.isNaN(verifiedLongitude)
                || Double.isInfinite(verifiedLatitude)
                || Double.isInfinite(verifiedLongitude)) {
            openLocationInMaps(true);
            return;
        }

        Uri navigationUri = Uri.parse(
                "google.navigation:q="
                        + verifiedLatitude
                        + ","
                        + verifiedLongitude
                        + "&mode=d"
        );

        Intent navigationIntent = new Intent(
                Intent.ACTION_VIEW,
                navigationUri
        );

        navigationIntent.setPackage(
                "com.google.android.apps.maps"
        );

        if (navigationIntent.resolveActivity(getPackageManager()) != null) {

            startActivity(navigationIntent);

        } else {

            Uri fallbackUri = Uri.parse(
                    String.format(
                            Locale.ENGLISH,
                            "geo:%f,%f?q=%f,%f",
                            verifiedLatitude,
                            verifiedLongitude,
                            verifiedLatitude,
                            verifiedLongitude
                    )
            );

            startActivity(
                    new Intent(Intent.ACTION_VIEW, fallbackUri)
            );
        }
    }

    private void openLocationInMaps(boolean directions) {

        if (locationText == null || locationText.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    getString(R.string.location_not_available),
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String encodedLocation = Uri.encode(locationText);

        Uri uri = directions
                ? Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination="
                        + encodedLocation
        )
                : Uri.parse(
                "https://www.google.com/maps/search/?api=1&query="
                        + encodedLocation
        );

        try {

            startActivity(
                    new Intent(Intent.ACTION_VIEW, uri)
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    getString(R.string.could_not_open_maps),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void loadListingFromSupabase() {

        String accessToken =
                SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                String endpoint =
                        "/rest/v1/listings?select=*&id=eq."
                                + URLEncoder.encode(
                                listingId,
                                "UTF-8"
                        ).replace("+", "%20")
                                + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode = connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {

                    Log.e(
                            TAG,
                            "Could not load listing. HTTP "
                                    + responseCode
                                    + ": "
                                    + response
                    );

                    return;
                }

                JSONArray listings = new JSONArray(response);

                if (listings.length() == 0) {
                    return;
                }

                JSONObject listing = listings.getJSONObject(0);

                String imageUrl = listing.optString("image_url", "");
                String name = listing.optString("property_name", "");
                String price = listing.optString("price", "");
                String location = listing.optString("location", "");
                String description = listing.optString("description", "");

                landlordUid = listing.optString("landlord_uid", "");

                propertyName = clean(name);
                locationText = clean(location);
                originalDescription = clean(description);

                priceText = price == null || price.trim().isEmpty()
                        || "null".equalsIgnoreCase(price.trim())
                        ? ""
                        : getString(R.string.price_per_month, price);

                runOnUiThread(() -> {

                    if (txtPropertyName != null) {
                        txtPropertyName.setText(propertyName);
                    }

                    if (txtPropertyPrice != null) {
                        txtPropertyPrice.setText(priceText);
                    }

                    if (txtPropertyLocation != null) {
                        txtPropertyLocation.setText(locationText);
                    }

                    if (propertyDescription != null) {
                        propertyDescription.setText(originalDescription);
                    }

                    populateChipGroup(
                            chipGroupAmenities,
                            listing.opt("amenities")
                    );

                    populateChipGroup(
                            chipGroupRules,
                            listing.opt("rules")
                    );

                    Locale locale = getResources()
                            .getConfiguration()
                            .getLocales()
                            .get(0);

                    if (btnTranslate != null
                            && !"en".equals(locale.getLanguage())
                            && !originalDescription.trim().isEmpty()) {

                        btnTranslate.setVisibility(View.VISIBLE);

                        btnTranslate.setText(
                                getString(
                                        R.string.translate_to_language,
                                        getLanguageDisplayName(
                                                locale.getLanguage()
                                        )
                                )
                        );
                    }
                });

                if (!clean(imageUrl).isEmpty()) {
                    loadPropertyImage(imageUrl);
                }

                updateMapCoordinatesFromLocation(location);

            } catch (Exception e) {

                Log.e(TAG, "Error loading listing details", e);

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void loadPropertyImage(String imageUrl) {

        if (imageUrl == null
                || imageUrl.trim().isEmpty()
                || "null".equalsIgnoreCase(imageUrl.trim())) {
            return;
        }

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                connection = (HttpURLConnection)
                        new URL(imageUrl.trim()).openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {

                    try (InputStream input =
                                 connection.getInputStream()) {

                        Bitmap bitmap =
                                BitmapFactory.decodeStream(input);

                        if (bitmap != null
                                && !isFinishing()
                                && !isDestroyed()) {

                            runOnUiThread(() -> {

                                if (propertyImage != null) {
                                    propertyImage.setImageBitmap(bitmap);

                                    propertyImage.setScaleType(
                                            ImageView.ScaleType.CENTER_CROP
                                    );
                                }
                            });
                        }
                    }
                }

            } catch (Exception e) {

                Log.e(TAG, "Could not display property image", e);

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void updateMapCoordinatesFromLocation(String location) {

        if (location == null || location.trim().isEmpty()) {
            return;
        }

        executor.execute(() -> {

            try {

                Geocoder geocoder =
                        new Geocoder(this, Locale.getDefault());

                List<Address> addresses =
                        geocoder.getFromLocationName(location, 1);

                if (addresses != null && !addresses.isEmpty()) {

                    Address address = addresses.get(0);

                    verifiedLatitude = address.getLatitude();
                    verifiedLongitude = address.getLongitude();

                    runOnUiThread(() -> {

                        if (mapView != null) {

                            GeoPoint point = new GeoPoint(
                                    verifiedLatitude,
                                    verifiedLongitude
                            );

                            mapView.getController().setCenter(point);
                            mapView.invalidate();
                        }
                    });
                }

            } catch (Exception e) {
                Log.w(TAG, "Could not geocode property location", e);
            }
        });
    }

    private void saveListing() {

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {

            Toast.makeText(
                    this,
                    getString(R.string.please_log_in_to_save),
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                connection = SupabaseHelper.createConnection(
                        "/rest/v1/saved_listings",
                        "POST",
                        accessToken
                );

                JSONObject body = new JSONObject();

                body.put("tenant_uid", userId);
                body.put("listing_id", listingId);

                SupabaseHelper.writeJson(connection, body);

                int responseCode = connection.getResponseCode();

                runOnUiThread(() -> {

                    if (responseCode >= 200 && responseCode < 300) {

                        Toast.makeText(
                                this,
                                getString(R.string.accommodation_saved),
                                Toast.LENGTH_SHORT
                        ).show();

                    } else if (responseCode == 409) {

                        Toast.makeText(
                                this,
                                getString(R.string.already_saved),
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        Toast.makeText(
                                this,
                                getString(
                                        R.string.could_not_save_accommodation
                                ),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });

            } catch (Exception e) {

                Log.e(TAG, "Save listing error", e);

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void contactLandlord() {

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || accessToken == null || accessToken.isEmpty()) {

            Toast.makeText(
                    this,
                    getString(
                            R.string.please_log_in_to_contact_landlord
                    ),
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                connection = SupabaseHelper.createConnection(
                        "/rest/v1/enquiries",
                        "POST",
                        accessToken
                );

                JSONObject body = new JSONObject();

                body.put("tenant_uid", userId);
                body.put("landlord_uid", landlordUid);
                body.put("listing_id", listingId);
                body.put(
                        "message",
                        getString(R.string.default_enquiry_message)
                );

                SupabaseHelper.writeJson(connection, body);

                int responseCode = connection.getResponseCode();

                runOnUiThread(() -> {

                    if (responseCode >= 200 && responseCode < 300) {

                        Toast.makeText(
                                this,
                                getString(R.string.enquiry_sent),
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        Toast.makeText(
                                this,
                                getString(R.string.could_not_send_enquiry),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });

            } catch (Exception e) {

                Log.e(TAG, "Contact landlord error", e);

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String clean(String value) {

        if (value == null
                || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }

        return value.trim();
    }

    private String getLanguageDisplayName(String code) {

        switch (code) {
            case "zu":
                return getString(R.string.language_isizulu);

            case "xh":
                return getString(R.string.language_isixhosa);

            case "st":
                return getString(R.string.language_sesotho);

            default:
                return getString(R.string.language_local);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {

        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (mapView != null) {
            mapView.onDetach();
        }

        executor.shutdownNow();

        super.onDestroy();
    }
}