package com.example.ikhaya_accommodation_seeking_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PropertyDetailsActivity extends AppCompatActivity {

    private boolean isTranslated = false;
    private String originalDescription = "";
    private String cachedTranslatedDescription = "";

    private MapView mapView;
    private Button btnApplyToRent;

    // Default coordinates (can also be passed via Intent extras)
    private double verifiedLatitude = -31.5889;
    private double verifiedLongitude = 28.7844;
    private String propertyName = "Mthatha Accommodation";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configure OSM Tile Usage Policy User-Agent prior to layout inflation
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue("iKhayaApp-EduvosCapstone-v1.0 (Student Project; contact@ikhaya.ac.za)");

        setContentView(R.layout.activity_property_details);

        // 2. Bind Views
        Button btnBack = findViewById(R.id.btnBack);
        TextView propertyDescription = findViewById(R.id.propertyDescription);
        TextView btnTranslate = findViewById(R.id.btnTranslate);
        ProgressBar progressTranslate = findViewById(R.id.progressTranslate);
        ChipGroup chipGroupAmenities = findViewById(R.id.chipGroupAmenities);
        ChipGroup chipGroupRules = findViewById(R.id.chipGroupRules);

        mapView = findViewById(R.id.mapView);
        Button btnGetDirections = findViewById(R.id.btnGetDirections);
        Button btnOpenFullMap = findViewById(R.id.btnOpenFullMap);

        btnBack.setOnClickListener(v -> finish());

        // 3. Receive Dynamic Coordinates if passed from previous screen
        if (getIntent().hasExtra("latitude") && getIntent().hasExtra("longitude")) {
            verifiedLatitude = getIntent().getDoubleExtra("latitude", verifiedLatitude);
            verifiedLongitude = getIntent().getDoubleExtra("longitude", verifiedLongitude);
            propertyName = getIntent().getStringExtra("propertyName") != null
                    ? getIntent().getStringExtra("propertyName")
                    : propertyName;
        }

        // 4. Setup Embedded In-App Map
        setupEmbeddedMap();

        // 5. Setup Navigation / Directions Actions
        btnGetDirections.setOnClickListener(v -> launchTurnByTurnNavigation());

        btnOpenFullMap.setOnClickListener(v -> {
            String uriString = String.format(Locale.ENGLISH, "geo:0,0?q=%f,%f(%s)",
                    verifiedLatitude, verifiedLongitude, Uri.encode(propertyName));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));

            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                String webUrl = String.format(Locale.ENGLISH, "https://www.google.com/maps/search/?api=1&query=%f,%f",
                        verifiedLatitude, verifiedLongitude);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
            }
        });

        // 6. Populate Localized Chips
        chipGroupAmenities.removeAllViews();
        chipGroupRules.removeAllViews();

        List<Integer> amenityStrings = Arrays.asList(
                R.string.amenity_water,
                R.string.amenity_electricity,
                R.string.amenity_parking
        );
        for (int resId : amenityStrings) {
            Chip chip = new Chip(this);
            chip.setText(getString(resId));
            chip.setClickable(false);
            chip.setCheckable(false);
            chipGroupAmenities.addView(chip);
        }

        List<Integer> ruleStrings = Arrays.asList(
                R.string.rule_available_now,
                R.string.rule_deposit_req
        );
        for (int resId : ruleStrings) {
            Chip chip = new Chip(this);
            chip.setText(getString(resId));
            chip.setClickable(false);
            chip.setCheckable(false);
            chipGroupRules.addView(chip);
        }

        // 7. Description & Gemini AI Translation
        originalDescription = "Affordable accommodation located in a convenient area of Mthatha. Suitable for individuals looking for accessible and affordable housing.";
        propertyDescription.setText(originalDescription);

        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        String currentLang = currentLocale.getLanguage();

        if ("en".equals(currentLang)) {
            btnTranslate.setVisibility(View.GONE);
        } else {
            btnTranslate.setVisibility(View.VISIBLE);
            btnTranslate.setText("Translate to " + getLanguageDisplayName(currentLang));
        }

        btnTranslate.setOnClickListener(v -> {
            if (isTranslated) {
                propertyDescription.setText(originalDescription);
                btnTranslate.setText("Translate to " + getLanguageDisplayName(currentLang));
                isTranslated = false;
                return;
            }

            if (!cachedTranslatedDescription.isEmpty()) {
                propertyDescription.setText(cachedTranslatedDescription);
                btnTranslate.setText("Show original");
                isTranslated = true;
                return;
            }

            progressTranslate.setVisibility(View.VISIBLE);
            btnTranslate.setEnabled(false);

            GeminiTranslator.translate(originalDescription, currentLang, new GeminiTranslator.TranslationCallback() {
                @Override
                public void onSuccess(String result) {
                    progressTranslate.setVisibility(View.GONE);
                    btnTranslate.setEnabled(true);
                    cachedTranslatedDescription = result;
                    propertyDescription.setText(cachedTranslatedDescription);
                    btnTranslate.setText("Show original");
                    isTranslated = true;
                }

                @Override
                public void onError(String errorMessage) {
                    progressTranslate.setVisibility(View.GONE);
                    btnTranslate.setEnabled(true);
                    Toast.makeText(PropertyDetailsActivity.this, "Translation failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Role Switching Handling
        boolean isLandlord = getIntent().getBooleanExtra("isLandlord", false);
        Button btnContact = findViewById(R.id.btnContactLandlord);
        Button btnEditListing = findViewById(R.id.btnEditListing);
        btnApplyToRent = findViewById(R.id.btnApplyToRent);

        if (isLandlord) {
            // 1. Hide Tenant-Specific Actions
            if (btnContact != null) {
                btnContact.setVisibility(View.GONE);
            }

            // 2. Show "Edit Listing"
            if (btnEditListing != null) {
                btnEditListing.setVisibility(View.VISIBLE);
                if (btnApplyToRent != null) btnApplyToRent.setVisibility(View.GONE);

                btnEditListing.setOnClickListener(v -> {
                    Intent editIntent = new Intent(this, AddListingActivity.class);
                    editIntent.putExtra("isEditing", true);
                    editIntent.putExtra("listingId", getIntent().getStringExtra("listingId"));
                    editIntent.putExtra("propertyName", propertyName);
                    startActivity(editIntent);
                });
            }
        } else {
            // Tenant View: Show Contact & Apply to Rent
            if (btnContact != null) btnContact.setVisibility(View.VISIBLE);
            if (btnEditListing != null) btnEditListing.setVisibility(View.GONE);
            if (btnApplyToRent != null) {
                btnApplyToRent.setVisibility(View.VISIBLE);
                btnApplyToRent.setText(getString(R.string.btn_apply_to_rent));
                btnApplyToRent.setOnClickListener(v -> showApplicationBottomSheet());
            }
        }
    }

    private void showApplicationBottomSheet() {
        BottomSheetDialog dialog =
                new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_apply_rental);

        ImageButton btnCloseDialog = dialog.findViewById(R.id.btnCloseDialog);
        TextView dialogTitle = dialog.findViewById(R.id.dialogPropertyTitle);
        EditText etApplicantName = dialog.findViewById(R.id.etApplicantName);
        EditText etIdNumber = dialog.findViewById(R.id.etIdNumber);
        EditText etPhone = dialog.findViewById(R.id.etPhone);
        EditText etEmergencyContact = dialog.findViewById(R.id.etEmergencyContact);
        Spinner spinnerOccupation = dialog.findViewById(R.id.spinnerOccupation);
        TextView tvSelectedMoveInDate = dialog.findViewById(R.id.tvSelectedMoveInDate);
        Spinner spinnerLeaseDuration = dialog.findViewById(R.id.spinnerLeaseDuration);
        Spinner spinnerOccupants = dialog.findViewById(R.id.spinnerOccupants);
        EditText etTenantNote = dialog.findViewById(R.id.etTenantNote);
        CheckBox cbAcceptTerms = dialog.findViewById(R.id.cbAcceptTerms);
        Button btnSubmit = dialog.findViewById(R.id.btnSubmitApplication);

        if (dialogTitle != null) {
            dialogTitle.setText(propertyName + " • R950/month");
        }

        // Confirmation warning when closing
        Runnable confirmExit = () -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_discard_title))
                    .setMessage(getString(R.string.dialog_discard_message))
                    .setPositiveButton(getString(R.string.dialog_btn_discard), (d, which) -> dialog.dismiss())
                    .setNegativeButton(getString(R.string.dialog_btn_continue), null)
                    .show();
        };

        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> confirmExit.run());
        }

        // Calendar Picker
        final String[] chosenDate = {"01/11/2026"};
        if (tvSelectedMoveInDate != null) {
            tvSelectedMoveInDate.setOnClickListener(v -> {
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                    chosenDate[0] = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year);
                    tvSelectedMoveInDate.setText(chosenDate[0]);
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            });
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String name = etApplicantName != null ? etApplicantName.getText().toString().trim() : "";
                String idNum = etIdNumber != null ? etIdNumber.getText().toString().trim() : "";
                String phone = etPhone != null ? etPhone.getText().toString().trim() : "";
                String emergency = etEmergencyContact != null ? etEmergencyContact.getText().toString().trim() : "";

                // Form & Format Validation
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.err_enter_name), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (idNum.length() != 13) {
                    Toast.makeText(this, getString(R.string.err_enter_id), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (phone.length() != 10 || !phone.startsWith("0")) {
                    Toast.makeText(this, getString(R.string.err_enter_phone), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (emergency.length() != 10 || !emergency.startsWith("0")) {
                    Toast.makeText(this, getString(R.string.err_enter_emergency), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (cbAcceptTerms != null && !cbAcceptTerms.isChecked()) {
                    Toast.makeText(this, getString(R.string.err_accept_terms), Toast.LENGTH_SHORT).show();
                    return;
                }

                String occupation = (spinnerOccupation != null && spinnerOccupation.getSelectedItem() != null)
                        ? spinnerOccupation.getSelectedItem().toString() : "Student";
                String duration = (spinnerLeaseDuration != null && spinnerLeaseDuration.getSelectedItem() != null)
                        ? spinnerLeaseDuration.getSelectedItem().toString() : "12 Months";
                String occupants = (spinnerOccupants != null && spinnerOccupants.getSelectedItem() != null)
                        ? spinnerOccupants.getSelectedItem().toString() : "1 Person";
                String note = (etTenantNote != null && !etTenantNote.getText().toString().trim().isEmpty())
                        ? etTenantNote.getText().toString().trim() : "Responsible tenant seeking quiet study space.";

                String appId = "APP-" + (System.currentTimeMillis() % 10000);
                ApplicationManager.addApplication(PropertyDetailsActivity.this, new ApplicationManager.RentalApplication(
                        appId,
                        propertyName,
                        name,
                        phone,
                        emergency,
                        occupation + " (ID: " + idNum + ")",
                        chosenDate[0],
                        duration,
                        occupants,
                        "R950/month",
                        note,
                        "PENDING"
                ));

                dialog.dismiss();
                Toast.makeText(this, getString(R.string.msg_toast_applied), Toast.LENGTH_LONG).show();

                if (btnApplyToRent != null) {
                    btnApplyToRent.setText(getString(R.string.btn_pending_review));
                    btnApplyToRent.setEnabled(false);
                    btnApplyToRent.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
                }
            });
        }

        dialog.show();
    }

    private void setupEmbeddedMap() {
        if (mapView == null) return;

        XYTileSource deTileSource = new XYTileSource(
                "OpenStreetMapDE",
                0, 19, 256, ".png",
                new String[]{
                        "https://tile.openstreetmap.de/"
                },
                "© OpenStreetMap contributors"
        );

        mapView.setTileSource(deTileSource);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        GeoPoint propertyLocation = new GeoPoint(verifiedLatitude, verifiedLongitude);
        mapView.getController().setCenter(propertyLocation);

        Marker marker = new Marker(mapView);
        marker.setPosition(propertyLocation);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(propertyName);
        marker.setSnippet("Verified Listing Stand");
        mapView.getOverlays().add(marker);

        mapView.invalidate();
    }

    private void launchTurnByTurnNavigation() {
        Uri navUri = Uri.parse("google.navigation:q=" + verifiedLatitude + "," + verifiedLongitude + "&mode=d");
        Intent navIntent = new Intent(Intent.ACTION_VIEW, navUri);
        navIntent.setPackage("com.google.android.apps.maps");

        if (navIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(navIntent);
        } else {
            Uri fallbackUri = Uri.parse(String.format(Locale.ENGLISH, "geo:%f,%f?q=%f,%f",
                    verifiedLatitude, verifiedLongitude, verifiedLatitude, verifiedLongitude));
            startActivity(new Intent(Intent.ACTION_VIEW, fallbackUri));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    private String getLanguageDisplayName(String code) {
        switch (code) {
            case "zu": return "isiZulu";
            case "xh": return "isiXhosa";
            case "st": return "Sesotho";
            default: return "Local Language";
        }
    }
}