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
import java.util.Arrays;
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
    private String locationText = "Mthatha";
    private double verifiedLatitude = -31.5889;
    private double verifiedLongitude = 28.7844;
    private String propertyName = "Mthatha Accommodation";
    private String priceText = "R950/month";

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configure OSM Tile Usage Policy User-Agent prior to layout inflation
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue("iKhayaApp-EduvosCapstone-v1.0 (Student Project; contact@ikhaya.ac.za)");

        setContentView(R.layout.activity_property_details);

        // 2. Extract Intent Extras
        if (getIntent().hasExtra("LISTING_ID")) {
            listingId = getIntent().getStringExtra("LISTING_ID");
        } else if (getIntent().hasExtra("listingId")) {
            listingId = getIntent().getStringExtra("listingId");
        }

        if (getIntent().hasExtra("propertyName") && getIntent().getStringExtra("propertyName") != null) {
            propertyName = getIntent().getStringExtra("propertyName");
        }
        if (getIntent().hasExtra("listingPrice") && getIntent().getStringExtra("listingPrice") != null) {
            priceText = getIntent().getStringExtra("listingPrice");
        }
        if (getIntent().hasExtra("listingLocation") && getIntent().getStringExtra("listingLocation") != null) {
            locationText = getIntent().getStringExtra("listingLocation");
        }
        if (getIntent().hasExtra("latitude") && getIntent().hasExtra("longitude")) {
            verifiedLatitude = getIntent().getDoubleExtra("latitude", verifiedLatitude);
            verifiedLongitude = getIntent().getDoubleExtra("longitude", verifiedLongitude);
        }

        // 3. Bind Views safely
        initializeViews();

        // 4. Setup Map
        setupEmbeddedMap();

        // 5. Setup Action Buttons & Translation
        setupButtons();

        // 6. Populate Localized Chips & Description
        setupChipsAndDescription();

        // 7. Role Handling (Landlord vs Tenant)
        setupRoleViews();

        // 8. If Supabase listingId exists, load remote data in background
        if (listingId != null && !listingId.trim().isEmpty()) {
            loadListingFromSupabase();
        }
    }

    private void initializeViews() {
        // Safe binding to prevent ClassCastException if an ID is used on a TextView
        View imgView = findViewById(R.id.propertyImage);
        if (imgView instanceof ImageView) {
            propertyImage = (ImageView) imgView;
        } else {
            int altPhotoId = getResources().getIdentifier("propertyPhoto", "id", getPackageName());
            if (altPhotoId != 0) {
                View altView = findViewById(altPhotoId);
                if (altView instanceof ImageView) {
                    propertyImage = (ImageView) altView;
                }
            }
        }

        txtPropertyName = findViewById(R.id.propertyName);
        txtPropertyPrice = findViewById(R.id.propertyPrice);
        txtPropertyLocation = findViewById(R.id.propertyLocation);
        propertyDescription = findViewById(R.id.propertyDescription);

        chipGroupAmenities = findViewById(R.id.chipGroupAmenities);
        chipGroupRules = findViewById(R.id.chipGroupRules);

        progressTranslate = findViewById(R.id.progressTranslate);
        btnTranslate = findViewById(R.id.btnTranslate);

        if (txtPropertyName != null) txtPropertyName.setText(propertyName);
        if (txtPropertyPrice != null) txtPropertyPrice.setText(priceText);
        if (txtPropertyLocation != null) txtPropertyLocation.setText(locationText);

        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupChipsAndDescription() {
        if (chipGroupAmenities != null) {
            chipGroupAmenities.removeAllViews();
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
        }

        if (chipGroupRules != null) {
            chipGroupRules.removeAllViews();
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
        }

        originalDescription = "Affordable accommodation located in a convenient area of Mthatha. Suitable for individuals looking for accessible and affordable housing.";
        if (propertyDescription != null) {
            propertyDescription.setText(originalDescription);
        }

        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        String currentLang = currentLocale.getLanguage();

        if (btnTranslate != null) {
            if ("en".equals(currentLang)) {
                btnTranslate.setVisibility(View.GONE);
            } else {
                btnTranslate.setVisibility(View.VISIBLE);
                btnTranslate.setText("Translate to " + getLanguageDisplayName(currentLang));
            }

            btnTranslate.setOnClickListener(v -> {
                if (isTranslated) {
                    if (propertyDescription != null) propertyDescription.setText(originalDescription);
                    btnTranslate.setText("Translate to " + getLanguageDisplayName(currentLang));
                    isTranslated = false;
                    return;
                }

                if (!cachedTranslatedDescription.isEmpty()) {
                    if (propertyDescription != null) propertyDescription.setText(cachedTranslatedDescription);
                    btnTranslate.setText("Show original");
                    isTranslated = true;
                    return;
                }

                if (progressTranslate != null) progressTranslate.setVisibility(View.VISIBLE);
                btnTranslate.setEnabled(false);

                GeminiTranslator.translate(originalDescription, currentLang, new GeminiTranslator.TranslationCallback() {
                    @Override
                    public void onSuccess(String result) {
                        if (progressTranslate != null) progressTranslate.setVisibility(View.GONE);
                        btnTranslate.setEnabled(true);
                        cachedTranslatedDescription = result;
                        if (propertyDescription != null) propertyDescription.setText(cachedTranslatedDescription);
                        btnTranslate.setText("Show original");
                        isTranslated = true;
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (progressTranslate != null) progressTranslate.setVisibility(View.GONE);
                        btnTranslate.setEnabled(true);
                        Toast.makeText(PropertyDetailsActivity.this, "Translation failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    private void setupButtons() {
        Button btnGetDirections = findViewById(R.id.btnGetDirections);
        Button btnOpenFullMap = findViewById(R.id.btnOpenFullMap);

        int openMapResId = getResources().getIdentifier("btnOpenMap", "id", getPackageName());
        View btnOpenMap = openMapResId != 0 ? findViewById(openMapResId) : null;

        int saveResId = getResources().getIdentifier("btnSave", "id", getPackageName());
        View btnSave = saveResId != 0 ? findViewById(saveResId) : null;

        int contactResId = getResources().getIdentifier("btnContact", "id", getPackageName());
        View btnContact = contactResId != 0 ? findViewById(contactResId) : null;
        if (btnContact == null) {
            btnContact = findViewById(R.id.btnContactLandlord);
        }

        if (btnGetDirections != null) {
            btnGetDirections.setOnClickListener(v -> launchTurnByTurnNavigation());
        }

        if (btnOpenFullMap != null) {
            btnOpenFullMap.setOnClickListener(v -> openLocationInMaps(false));
        }

        if (btnOpenMap != null) {
            btnOpenMap.setOnClickListener(v -> openLocationInMaps(false));
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveListing());
        }

        if (btnContact != null) {
            btnContact.setOnClickListener(v -> contactLandlord());
        }
    }

    private void setupRoleViews() {
        boolean isLandlord = getIntent().getBooleanExtra("isLandlord", false);
        Button btnContactLandlord = findViewById(R.id.btnContactLandlord);
        Button btnEditListing = findViewById(R.id.btnEditListing);
        btnApplyToRent = findViewById(R.id.btnApplyToRent);

        if (isLandlord) {
            if (btnContactLandlord != null) btnContactLandlord.setVisibility(View.GONE);
            if (btnApplyToRent != null) btnApplyToRent.setVisibility(View.GONE);

            if (btnEditListing != null) {
                btnEditListing.setVisibility(View.VISIBLE);
                btnEditListing.setOnClickListener(v -> {
                    Intent editIntent = new Intent(this, AddListingActivity.class);
                    editIntent.putExtra("isEditing", true);
                    editIntent.putExtra("listingId", listingId);
                    editIntent.putExtra("propertyName", propertyName);
                    startActivity(editIntent);
                });
            }
        } else {
            if (btnContactLandlord != null) btnContactLandlord.setVisibility(View.VISIBLE);
            if (btnEditListing != null) btnEditListing.setVisibility(View.GONE);
            if (btnApplyToRent != null) {
                btnApplyToRent.setVisibility(View.VISIBLE);
                btnApplyToRent.setText(getString(R.string.btn_apply_to_rent));
                btnApplyToRent.setOnClickListener(v -> showApplicationBottomSheet());
            }
        }
    }

    private void showApplicationBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
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
            dialogTitle.setText(propertyName + " • " + priceText);
        }

        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_discard_title))
                        .setMessage(getString(R.string.dialog_discard_message))
                        .setPositiveButton(getString(R.string.dialog_btn_discard), (d, which) -> dialog.dismiss())
                        .setNegativeButton(getString(R.string.dialog_btn_continue), null)
                        .show();
            });
        }

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
                        ? etTenantNote.getText().toString().trim() : "Responsible tenant seeking accommodation.";

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
                        priceText,
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
        mapView = findViewById(R.id.mapView);
        if (mapView == null) return;

        XYTileSource deTileSource = new XYTileSource(
                "OpenStreetMapDE",
                0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.de/"},
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

    private void openLocationInMaps(boolean directions) {
        if (locationText == null || locationText.trim().isEmpty()) {
            Toast.makeText(this, "Location is not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String encodedLocation = Uri.encode(locationText);
        Uri uri = directions
                ? Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + encodedLocation)
                : Uri.parse("https://www.google.com/maps/search/?api=1&query=" + encodedLocation);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Maps.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadListingFromSupabase() {
        String accessToken = SupabaseHelper.getAccessToken(this);
        if (accessToken == null || accessToken.isEmpty()) return;

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/listings?select=*&id=eq."
                        + URLEncoder.encode(listingId, "UTF-8")
                        + "&limit=1";

                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int code = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (code >= 200 && code < 300) {
                    JSONArray listings = new JSONArray(response);
                    if (listings.length() > 0) {
                        JSONObject listing = listings.getJSONObject(0);
                        String imageUrl = listing.optString("image_url", "");
                        String name = listing.optString("property_name", propertyName);
                        String price = listing.optString("price", priceText);
                        String loc = listing.optString("location", locationText);
                        String desc = listing.optString("description", originalDescription);
                        landlordUid = listing.optString("landlord_uid", "");

                        propertyName = name;
                        priceText = "R" + price + " per month";
                        locationText = loc;
                        originalDescription = desc;

                        runOnUiThread(() -> {
                            if (txtPropertyName != null) txtPropertyName.setText(propertyName);
                            if (txtPropertyPrice != null) txtPropertyPrice.setText(priceText);
                            if (txtPropertyLocation != null) txtPropertyLocation.setText(locationText);
                            if (propertyDescription != null) propertyDescription.setText(originalDescription);
                        });

                        if (!imageUrl.trim().isEmpty() && !imageUrl.equalsIgnoreCase("null")) {
                            loadPropertyImage(imageUrl);
                        }
                        updateMapCoordinatesFromLocation(loc);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading Supabase listing", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void loadPropertyImage(String imageUrl) {
        executor.execute(() -> {
            try {
                URL url = new URL(imageUrl);
                InputStream input = url.openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null && !isFinishing()) {
                    runOnUiThread(() -> {
                        if (propertyImage != null) {
                            propertyImage.setImageBitmap(bitmap);
                            propertyImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not display property image", e);
            }
        });
    }

    private void updateMapCoordinatesFromLocation(String loc) {
        if (loc == null || loc.trim().isEmpty()) return;
        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(loc, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    verifiedLatitude = addr.getLatitude();
                    verifiedLongitude = addr.getLongitude();
                    runOnUiThread(() -> {
                        if (mapView != null) {
                            GeoPoint point = new GeoPoint(verifiedLatitude, verifiedLongitude);
                            mapView.getController().setCenter(point);
                            mapView.invalidate();
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void saveListing() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, "Please log in to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = SupabaseHelper.createConnection("/rest/v1/saved_listings", "POST", accessToken);
                JSONObject body = new JSONObject();
                body.put("tenant_uid", userId);
                body.put("listing_id", listingId);
                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        Toast.makeText(this, "Accommodation saved.", Toast.LENGTH_SHORT).show();
                    } else if (code == 409) {
                        Toast.makeText(this, "Already saved.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Could not save accommodation.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Save listing error", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void contactLandlord() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, "Please log in to contact landlord.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = SupabaseHelper.createConnection("/rest/v1/enquiries", "POST", accessToken);
                JSONObject body = new JSONObject();
                body.put("tenant_uid", userId);
                body.put("landlord_uid", landlordUid);
                body.put("listing_id", listingId);
                body.put("message", "I am interested in this accommodation.");
                SupabaseHelper.writeJson(connection, body);

                int code = connection.getResponseCode();
                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        Toast.makeText(this, "Enquiry sent to landlord.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Could not send enquiry.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Contact landlord error", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String getLanguageDisplayName(String code) {
        switch (code) {
            case "zu": return "isiZulu";
            case "xh": return "isiXhosa";
            case "st": return "Sesotho";
            default: return "Local Language";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) mapView.onDetach();
        executor.shutdownNow();
        super.onDestroy();
    }
}