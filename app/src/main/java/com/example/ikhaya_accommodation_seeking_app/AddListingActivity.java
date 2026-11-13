package com.example.ikhaya_accommodation_seeking_app;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddListingActivity extends AppCompatActivity {

    private static final String TAG = "iKhayaListing";

    private EditText propertyNameInput;
    private EditText locationInput;
    private EditText priceInput;
    private EditText roomsInput;
    private EditText descriptionInput;

    private AutoCompleteTextView propertyTypeInput;
    private AutoCompleteTextView waterSourceInput;
    private AutoCompleteTextView electricityInput;
    private AutoCompleteTextView sanitationInput;

    private AutoCompleteTextView authorityDocTypeInput;
    private TextView authorityDocNameText;
    private Button uploadAuthorityDocButton;
    private Uri selectedAuthorityDocUri = null;

    private ImageView imagePreview;
    private TextView locationStatus;
    private TextView imageCountText;
    private Button verifyLocationButton;
    private Button uploadImageButton;
    private Button submitButton;
    private ChipGroup amenitiesGroup;

    private final ArrayList<Uri> selectedImageUris = new ArrayList<>();
    private final ArrayList<String> selectedAmenities = new ArrayList<>();

    private boolean isLocationVerified = false;
    private double verifiedLatitude;
    private double verifiedLongitude;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static String getDocTypeKey(String label) {
        if (label == null) return "MUNICIPAL_RATES";
        String clean = label.trim();
        if (clean.contains("Municipal Rates") || clean.contains("MUNICIPAL_RATES")) {
            return "MUNICIPAL_RATES";
        } else if (clean.contains("Lease") || clean.contains("Title Deed") || clean.contains("LEASE_AGREEMENT")) {
            return "LEASE_AGREEMENT";
        } else if (clean.contains("PTO") || clean.contains("Permission to Occupy") || clean.contains("PTO_LETTER")) {
            return "PTO_LETTER";
        } else if (clean.contains("Traditional") || clean.contains("Tribal") || clean.contains("TRADITIONAL_LETTER")) {
            return "TRADITIONAL_LETTER";
        }
        return "MUNICIPAL_RATES";
    }

    public static String getDocTypeLabel(String key) {
        if (key == null) return "Urban: Municipal Rates & Taxes Bill";
        switch (key.trim().toUpperCase()) {
            case "LEASE_AGREEMENT":
                return "Urban: Title Deed / Lease Agreement";
            case "PTO_LETTER":
                return "Rural: Permission to Occupy (PTO)";
            case "TRADITIONAL_LETTER":
                return "Rural: Traditional Council Letter";
            case "MUNICIPAL_RATES":
            default:
                return "Urban: Municipal Rates & Taxes Bill";
        }
    }

    private final ActivityResultLauncher<String> docPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedAuthorityDocUri = uri;
                    if (authorityDocNameText != null) {
                        authorityDocNameText.setText(getString(R.string.doc_attached_prefix, uri.getLastPathSegment()));
                        authorityDocNameText.setTextColor(Color.parseColor("#2DCE89"));
                    }
                    if (uploadAuthorityDocButton != null) {
                        uploadAuthorityDocButton.setText(R.string.btn_change_authority_doc);
                    }
                }
            });

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetMultipleContents(),
                    uris -> {
                        if (uris == null || uris.isEmpty()) {
                            return;
                        }

                        selectedImageUris.clear();
                        selectedImageUris.addAll(uris);

                        imagePreview.setImageURI(selectedImageUris.get(0));

                        imageCountText.setText(
                                selectedImageUris.size()
                                        + (selectedImageUris.size() == 1
                                        ? (" " + getString(R.string.photo_selected_singular))
                                        : (" " + getString(R.string.photo_selected_plural)))
                        );

                        uploadImageButton.setText(R.string.btn_change_selected_photos);

                        Toast.makeText(
                                this,
                                selectedImageUris.size() + " " + getString(R.string.photos_selected_toast),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    captureDeviceGps();
                } else {
                    Toast.makeText(this, R.string.permission_location_denied_map_fallback, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_add_listing);

        propertyNameInput = findViewById(R.id.edtPropertyName);
        locationInput = findViewById(R.id.autoCompleteLocation);

        priceInput = findViewById(R.id.edtPrice);
        roomsInput = findViewById(R.id.edtRooms);
        descriptionInput = findViewById(R.id.edtDescription);

        propertyTypeInput = findViewById(R.id.dropdownPropertyType);
        waterSourceInput = findViewById(R.id.dropdownWaterSource);
        electricityInput = findViewById(R.id.dropdownElectricity);
        sanitationInput = findViewById(R.id.dropdownSanitation);

        imagePreview = findViewById(R.id.imgProperty);
        locationStatus = findViewById(R.id.tvLocationCoords);

        imageCountText = findViewById(R.id.tvSelectedImageCount);
        verifyLocationButton = findViewById(R.id.btnAdjustPinOnMap);

        uploadImageButton = findViewById(R.id.btnUploadImage);
        submitButton = findViewById(R.id.btnSubmitListing);
        amenitiesGroup = findViewById(R.id.cgSelectedAmenities);

        Button btnUseCurrentGps = findViewById(R.id.btnUseCurrentGps);
        if (btnUseCurrentGps != null) {
            btnUseCurrentGps.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    captureDeviceGps();
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            });
        }

        if (submitButton != null) {
            submitButton.setText(R.string.btn_submit_for_review);
        }

        View backButton = findViewById(R.id.btnBackAddListing);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        setupDropdown(propertyTypeInput, R.array.sa_property_types);
        setupDropdown(waterSourceInput, R.array.sa_water_sources);
        setupDropdown(electricityInput, R.array.sa_electricity_types);
        setupDropdown(sanitationInput, R.array.sa_sanitation_types);

        authorityDocTypeInput = findViewById(R.id.dropdownAuthorityDocType);
        authorityDocNameText = findViewById(R.id.tvSelectedDocName);
        uploadAuthorityDocButton = findViewById(R.id.btnUploadAuthorityDoc);

        setupDropdown(authorityDocTypeInput, R.array.sa_authority_doc_types);
        if (uploadAuthorityDocButton != null) {
            uploadAuthorityDocButton.setOnClickListener(v -> docPicker.launch("*/*"));
        }

        TextView tvPriceLabel = findViewById(R.id.tvPriceLabel);
        TextView tvRoomsLabel = findViewById(R.id.tvRoomsLabel);
        ChipGroup cgOfferType = findViewById(R.id.cgOfferType);

        if (cgOfferType != null) {
            cgOfferType.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipSell)) {
                    if (tvPriceLabel != null) tvPriceLabel.setText(R.string.total_selling_price_r);
                } else {
                    if (tvPriceLabel != null) tvPriceLabel.setText(R.string.monthly_rental_price_r);
                }
            });
        }

        if (propertyTypeInput != null) {
            propertyTypeInput.setOnItemClickListener((parent, view, position, id) -> {
                String selectedType = parent.getItemAtPosition(position).toString().toLowerCase();

                if (selectedType.contains("land") || selectedType.contains("plot")) {
                    if (tvRoomsLabel != null) tvRoomsLabel.setVisibility(View.GONE);
                    if (roomsInput != null) {
                        roomsInput.setVisibility(View.GONE);
                        roomsInput.setText("0");
                    }
                } else {
                    if (tvRoomsLabel != null) tvRoomsLabel.setVisibility(View.VISIBLE);
                    if (roomsInput != null) {
                        roomsInput.setVisibility(View.VISIBLE);
                        if (roomsInput.getText().toString().equals("0")) {
                            roomsInput.setText("");
                        }
                    }
                }
            });
        }

        uploadImageButton.setOnClickListener(v -> imagePicker.launch("image/*"));

        if (locationInput != null) {
            locationInput.setFocusable(false);
        }

        View.OnClickListener openPickerListener = v -> {
            launchUberStylePinPicker(this, verifiedLatitude, verifiedLongitude, (lat, lon, resolvedAddress) -> {
                verifiedLatitude = lat;
                verifiedLongitude = lon;
                isLocationVerified = true;
                if (locationInput != null) {
                    locationInput.setText(resolvedAddress);
                }
                if (locationStatus != null) {
                    locationStatus.setText(String.format(Locale.getDefault(), "Pinned: %.5f, %.5f (Verified)", lat, lon));
                    locationStatus.setTextColor(Color.parseColor("#2DCE89"));
                }
            });
        };

        if (locationInput != null) {
            locationInput.setOnClickListener(openPickerListener);
        }

        if (verifyLocationButton != null) {
            verifyLocationButton.setOnClickListener(openPickerListener);
        }

        View btnExtraAmenities = findViewById(R.id.btnSelectExtraAmenities);
        if (btnExtraAmenities != null) {
            btnExtraAmenities.setOnClickListener(v -> showAmenitiesPickerDialog());
        }

        submitButton.setOnClickListener(v -> addListing());
    }

    private void setupDropdown(AutoCompleteTextView dropdown, int arrayResource) {
        if (dropdown == null) return;
        String[] items = getResources().getStringArray(arrayResource);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                items
        );

        dropdown.setAdapter(adapter);
        dropdown.setKeyListener(null);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());
        dropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdown.showDropDown();
        });
    }

    private void showAmenitiesPickerDialog() {
        String[] options = {
                getString(R.string.amenity_wifi),
                getString(R.string.amenity_parking),
                getString(R.string.amenity_security),
                getString(R.string.amenity_furnished),
                getString(R.string.amenity_water_included),
                getString(R.string.amenity_electricity_included),
                getString(R.string.amenity_deposit_required),
                getString(R.string.amenity_no_smoking),
                getString(R.string.amenity_shared_kitchen),
                getString(R.string.amenity_laundry_facilities)
        };

        boolean[] checked = new boolean[options.length];

        for (int i = 0; i < options.length; i++) {
            checked[i] = selectedAmenities.contains(options[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_select_features_and_rules)
                .setMultiChoiceItems(
                        options,
                        checked,
                        (dialog, which, isChecked) -> {
                            if (isChecked) {
                                if (!selectedAmenities.contains(options[which])) {
                                    selectedAmenities.add(options[which]);
                                }
                            } else {
                                selectedAmenities.remove(options[which]);
                            }
                        }
                )
                .setPositiveButton(R.string.btn_done, (dialog, which) -> {
                    amenitiesGroup.removeAllViews();

                    for (String amenity : selectedAmenities) {
                        Chip chip = new Chip(this);
                        chip.setText(amenity);
                        chip.setCloseIconVisible(true);
                        chip.setOnCloseIconClickListener(v -> {
                            selectedAmenities.remove(amenity);
                            amenitiesGroup.removeView(chip);
                        });
                        amenitiesGroup.addView(chip);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void captureDeviceGps() {
        locationStatus.setText(R.string.acquiring_satellite_lock);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (location != null) {
                    verifiedLatitude = location.getLatitude();
                    verifiedLongitude = location.getLongitude();
                    isLocationVerified = true;

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        locationStatus.setText(String.format(Locale.getDefault(),
                                getString(R.string.format_gps_captured), verifiedLatitude, verifiedLongitude));
                        locationStatus.setTextColor(Color.parseColor("#2DCE89"));
                    });
                } else {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        locationStatus.setText(R.string.gps_signal_weak);
                    });
                }
            } catch (SecurityException ignored) {}
        }
    }

    public interface OnLocationSelectedListener {
        void onLocationSelected(double latitude, double longitude, String resolvedAddress);
    }

    public static void launchUberStylePinPicker(
            AppCompatActivity activity,
            double initialLat,
            double initialLon,
            OnLocationSelectedListener listener) {

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_pin_picker);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        MapView pickerMap = dialog.findViewById(R.id.pickerMapView);
        Button btnConfirmPin = dialog.findViewById(R.id.btnConfirmPin);
        View btnLocateMe = dialog.findViewById(R.id.btnDialogLocateMe);
        TextView tvSelectedAddress = dialog.findViewById(R.id.tvSelectedAddress);
        EditText etDialogSearch = dialog.findViewById(R.id.etDialogSearch);
        View btnDialogSearch = dialog.findViewById(R.id.btnDialogSearch);
        ProgressBar pbDialogSearch = dialog.findViewById(R.id.pbDialogSearch);
        View cardSearchSuggestions = dialog.findViewById(R.id.cardSearchSuggestions);
        LinearLayout layoutSearchSuggestions = dialog.findViewById(R.id.layoutSearchSuggestions);

        Handler handler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        if (pickerMap != null) {
            XYTileSource tileSource = new XYTileSource(
                    "OpenStreetMapDE",
                    0,
                    19,
                    256,
                    ".png",
                    new String[]{"https://tile.openstreetmap.de/"},
                    "© OpenStreetMap contributors"
            );
            pickerMap.setTileSource(tileSource);
            pickerMap.setMultiTouchControls(true);

            GeoPoint startPoint = (!Double.isNaN(initialLat) && initialLat != 0.0)
                    ? new GeoPoint(initialLat, initialLon)
                    : new GeoPoint(-25.9124, 28.1419); // Default Kosmosdal / Centurion

            pickerMap.getController().setZoom(17.0);
            pickerMap.getController().setCenter(startPoint);
            pickerMap.onResume();

            // Perform initial reverse-geocode
            reverseGeocodeCoords(activity, executor, startPoint.getLatitude(), startPoint.getLongitude(), tvSelectedAddress);

            // Map scroll listener with debounced reverse-geocoding
            pickerMap.addMapListener(new MapListener() {
                @Override
                public boolean onScroll(ScrollEvent event) {
                    handler.removeCallbacksAndMessages(null);
                    if (cardSearchSuggestions != null) cardSearchSuggestions.setVisibility(View.GONE);
                    if (tvSelectedAddress != null) tvSelectedAddress.setText("Locating address...");

                    handler.postDelayed(() -> {
                        GeoPoint center = (GeoPoint) pickerMap.getMapCenter();
                        reverseGeocodeCoords(activity, executor, center.getLatitude(), center.getLongitude(), tvSelectedAddress);
                    }, 500);
                    return true;
                }

                @Override
                public boolean onZoom(ZoomEvent event) {
                    return false;
                }
            });

            Runnable triggerSearch = () -> {
                String query = etDialogSearch != null ? etDialogSearch.getText().toString().trim() : "";
                if (!query.isEmpty()) {
                    hideKeyboard(activity, etDialogSearch);
                    searchPlaces(activity, executor, query, pickerMap, tvSelectedAddress, cardSearchSuggestions, layoutSearchSuggestions, pbDialogSearch);
                }
            };

            // 1. Keyboard "Search" action
            if (etDialogSearch != null) {
                etDialogSearch.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                        triggerSearch.run();
                        return true;
                    }
                    return false;
                });
            }

            // 2. Search icon button click
            if (btnDialogSearch != null) {
                btnDialogSearch.setOnClickListener(v -> triggerSearch.run());
            }

            // 3. Locate Me button
            if (btnLocateMe != null) {
                btnLocateMe.setOnClickListener(v -> {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
                        if (lm != null) {
                            try {
                                Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                                if (loc != null) {
                                    GeoPoint myPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                                    pickerMap.getController().animateTo(myPoint);
                                    pickerMap.getController().setZoom(18.0);
                                    reverseGeocodeCoords(activity, executor, loc.getLatitude(), loc.getLongitude(), tvSelectedAddress);
                                } else {
                                    Toast.makeText(activity, "Searching for GPS signal...", Toast.LENGTH_SHORT).show();
                                }
                            } catch (SecurityException ignored) {}
                        }
                    } else {
                        Toast.makeText(activity, "Location permission required to pinpoint device.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 4. Confirm Location button
            if (btnConfirmPin != null) {
                btnConfirmPin.setOnClickListener(v -> {
                    GeoPoint finalCenter = (GeoPoint) pickerMap.getMapCenter();
                    String resolvedAddress = tvSelectedAddress != null ? tvSelectedAddress.getText().toString() : "";
                    if (listener != null) {
                        listener.onLocationSelected(finalCenter.getLatitude(), finalCenter.getLongitude(), resolvedAddress);
                    }
                    dialog.dismiss();
                });
            }

            dialog.setOnDismissListener(d -> {
                pickerMap.onPause();
                pickerMap.onDetach();
                executor.shutdownNow();
            });
        }

        dialog.show();
    }

    private static void searchPlaces(
            AppCompatActivity activity,
            ExecutorService executor,
            String query,
            MapView map,
            TextView tvSelectedAddress,
            View cardSuggestions,
            LinearLayout layoutSuggestions,
            ProgressBar pbSearch) {

        if (pbSearch != null) pbSearch.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
                // Fetch up to 5 matching candidates
                List<Address> matches = geocoder.getFromLocationName(query + ", South Africa", 5);

                activity.runOnUiThread(() -> {
                    if (pbSearch != null) pbSearch.setVisibility(View.GONE);

                    if (matches == null || matches.isEmpty()) {
                        Toast.makeText(activity, "No matching places found. Try moving the map directly.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (matches.size() == 1) {
                        Address single = matches.get(0);
                        flyToAddress(activity, executor, single, map, tvSelectedAddress, cardSuggestions);
                    } else {
                        // Populate multi-option dropdown
                        if (layoutSuggestions == null || cardSuggestions == null) return;
                        layoutSuggestions.removeAllViews();
                        cardSuggestions.setVisibility(View.VISIBLE);

                        for (Address addr : matches) {
                            String fullTitle = formatAddress(addr);

                            TextView optionView = new TextView(activity);
                            optionView.setText(fullTitle);
                            optionView.setTextColor(Color.parseColor("#1F1614"));
                            optionView.setTextSize(13.5f);
                            optionView.setPadding(32, 24, 32, 24);
                            optionView.setBackgroundResource(android.R.drawable.list_selector_background);

                            optionView.setOnClickListener(v -> {
                                flyToAddress(activity, executor, addr, map, tvSelectedAddress, cardSuggestions);
                            });

                            layoutSuggestions.addView(optionView);

                            View divider = new View(activity);
                            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                            divider.setBackgroundColor(Color.parseColor("#EFEBE4"));
                            layoutSuggestions.addView(divider);
                        }
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (pbSearch != null) pbSearch.setVisibility(View.GONE);
                    Toast.makeText(activity, "Search failed. Please pan the map directly.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static void flyToAddress(
            AppCompatActivity activity,
            ExecutorService executor,
            Address addr,
            MapView map,
            TextView tvSelectedAddress,
            View cardSuggestions) {

        if (cardSuggestions != null) cardSuggestions.setVisibility(View.GONE);
        GeoPoint target = new GeoPoint(addr.getLatitude(), addr.getLongitude());
        map.getController().animateTo(target);
        map.getController().setZoom(17.5);
        reverseGeocodeCoords(activity, executor, addr.getLatitude(), addr.getLongitude(), tvSelectedAddress);
    }

    private static void reverseGeocodeCoords(
            AppCompatActivity activity,
            ExecutorService executor,
            double lat,
            double lon,
            TextView displayView) {

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String cleanAddress = formatAddress(addresses.get(0));
                    activity.runOnUiThread(() -> {
                        if (displayView != null) displayView.setText(cleanAddress);
                    });
                } else {
                    activity.runOnUiThread(() -> {
                        if (displayView != null) displayView.setText(String.format(Locale.getDefault(), "Coordinates: %.5f, %.5f", lat, lon));
                    });
                }
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (displayView != null) displayView.setText(String.format(Locale.getDefault(), "Pinned: %.5f, %.5f", lat, lon));
                });
            }
        });
    }

    private static String formatAddress(Address addr) {
        StringBuilder sb = new StringBuilder();
        String feature = addr.getFeatureName();
        String thoroughfare = addr.getThoroughfare();
        String subThoroughfare = addr.getSubThoroughfare();
        String locality = addr.getLocality() != null ? addr.getLocality() : addr.getSubLocality();
        String admin = addr.getAdminArea();

        if (subThoroughfare != null) sb.append(subThoroughfare).append(" ");
        if (thoroughfare != null) {
            sb.append(thoroughfare).append(", ");
        } else if (feature != null && !feature.matches("\\d+") && !feature.equalsIgnoreCase(locality)) {
            sb.append(feature).append(", ");
        }

        if (locality != null) sb.append(locality).append(", ");
        if (admin != null) sb.append(admin);

        String res = sb.toString().trim();
        if (res.endsWith(",")) res = res.substring(0, res.length() - 1);
        return res.isEmpty() ? addr.getAddressLine(0) : res;
    }

    private static void hideKeyboard(AppCompatActivity activity, View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void addListing() {
        // 1. Enforce GPS Pin selection
        if (verifiedLatitude == 0.0 && verifiedLongitude == 0.0) {
            Toast.makeText(this, R.string.error_pin_location_required, Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Enforce Ownership / Occupancy Document attachment
        boolean hasNewDoc = (selectedAuthorityDocUri != null);
        boolean hasExistingDoc = false;

        if (!hasNewDoc && !hasExistingDoc) {
            Toast.makeText(this, R.string.error_ownership_doc_required, Toast.LENGTH_LONG).show();
            return;
        }

        String propertyName = propertyNameInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();
        String roomsText = roomsInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        String propertyType = propertyTypeInput.getText().toString().trim();
        String waterSource = waterSourceInput.getText().toString().trim();
        String electricity = electricityInput.getText().toString().trim();
        String sanitation = sanitationInput.getText().toString().trim();

        if (propertyName.isEmpty()
                || location.isEmpty()
                || priceText.isEmpty()
                || roomsText.isEmpty()
                || description.isEmpty()
                || propertyType.isEmpty()
                || waterSource.isEmpty()
                || electricity.isEmpty()
                || sanitation.isEmpty()) {

            Toast.makeText(this, R.string.error_complete_required_fields, Toast.LENGTH_LONG).show();
            return;
        }

        if (!isLocationVerified) {
            Toast.makeText(this, R.string.error_verify_location_first, Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, R.string.error_select_photo_first, Toast.LENGTH_LONG).show();
            return;
        }

        double price;
        int rooms;

        try {
            price = Double.parseDouble(priceText);
            rooms = Integer.parseInt(roomsText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_price_or_rooms, Toast.LENGTH_LONG).show();
            return;
        }

        boolean isLand = propertyType.toLowerCase().contains("land") || propertyType.toLowerCase().contains("plot");
        if (price <= 0 || (rooms <= 0 && !isLand)) {
            Toast.makeText(this, isLand ? getString(R.string.error_price_greater_zero) : getString(R.string.error_price_rooms_greater_zero), Toast.LENGTH_LONG).show();
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, R.string.session_expired_login_again, Toast.LENGTH_LONG).show();
            return;
        }

        submitButton.setEnabled(false);
        uploadImageButton.setEnabled(false);
        verifyLocationButton.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = SupabaseHelper.createConnection(
                        "/rest/v1/listings",
                        "POST",
                        accessToken
                );

                connection.setRequestProperty("Prefer", "return=representation");

                JSONObject listing = new JSONObject();
                listing.put("landlord_uid", userId);

                ChipGroup cgOfferType = findViewById(R.id.cgOfferType);
                String offerType = (cgOfferType != null && cgOfferType.getCheckedChipId() == R.id.chipSell) ? "sale" : "rent";
                listing.put("offer_type", offerType);

                listing.put("property_name", propertyName);
                listing.put("location", location);
                listing.put("price", price);
                listing.put("rooms", rooms);
                listing.put("property_type", propertyType);
                listing.put("description", description);

                listing.put("water_source", waterSource);
                listing.put("electricity", electricity);
                listing.put("sanitation", sanitation);

                if (isLocationVerified) {
                    listing.put("latitude", verifiedLatitude);
                    listing.put("longitude", verifiedLongitude);
                }

                String docTypeLabel = authorityDocTypeInput != null ? authorityDocTypeInput.getText().toString().trim() : "";
                String docTypeKey = getDocTypeKey(docTypeLabel);

                listing.put("authority_doc_type", docTypeKey);
                listing.put("status", "PENDING_VERIFICATION");
                listing.put("availability", "Pending");

                SupabaseHelper.writeJson(connection, listing);

                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode < 200 || responseCode >= 300) {
                    Log.e(TAG, "Create listing response: " + response);
                    showError(getString(R.string.error_check_supabase_permissions));
                    return;
                }

                JSONArray returned = new JSONArray(response);

                if (returned.length() == 0) {
                    showError(getString(R.string.error_listing_not_returned));
                    return;
                }

                String listingId = returned.getJSONObject(0).getString("id");

                ArrayList<String> uploadedUrls = new ArrayList<>();

                for (int i = 0; i < selectedImageUris.size(); i++) {
                    String imagePath = userId
                            + "/"
                            + listingId
                            + "/property_"
                            + System.currentTimeMillis()
                            + "_"
                            + i
                            + ".jpg";

                    boolean uploaded = uploadImage(accessToken, selectedImageUris.get(i), imagePath);

                    if (!uploaded) {
                        Log.e(TAG, "Failed to upload image " + i);
                        continue;
                    }

                    String publicUrl = SupabaseHelper.getPublicStorageUrl("property-images", imagePath);
                    uploadedUrls.add(publicUrl);
                    saveImageRecord(accessToken, listingId, publicUrl);
                }

                if (uploadedUrls.isEmpty()) {
                    showError(getString(R.string.error_no_photos_uploaded));
                    return;
                }

                updateListingImagePath(accessToken, listingId, uploadedUrls.get(0));

                if (selectedAuthorityDocUri != null) {
                    String docPath = userId + "/" + listingId + "/authority_doc_" + System.currentTimeMillis() + ".jpg";
                    boolean docUploaded = uploadImage(accessToken, selectedAuthorityDocUri, docPath);
                    if (docUploaded) {
                        String docPublicUrl = SupabaseHelper.getPublicStorageUrl("property-images", docPath);
                        updateListingAuthorityDoc(accessToken, listingId, docPublicUrl);
                    }
                }

                SupabaseHelper.createNotification(
                        userId,
                        "Listing Under Review",
                        "Your listing '" + propertyName + "' has been submitted and is pending admin approval.",
                        accessToken
                );

                SupabaseHelper.notifyAdmins(
                        "New Listing Submitted",
                        "A landlord has submitted '" + propertyName + "'. It requires review.",
                        accessToken
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(
                            AddListingActivity.this,
                            getString(R.string.listing_submitted_success_prefix) + " " + uploadedUrls.size() + " " + getString(R.string.listing_submitted_success_suffix),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Add listing failed", e);
                showError(getString(R.string.error_adding_listing_general));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private boolean uploadImage(String accessToken, Uri imageUri, String imagePath) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createStorageConnection(
                    "property-images",
                    imagePath,
                    "POST",
                    accessToken
            );

            String mimeType = getContentResolver().getType(imageUri);
            if (mimeType == null) {
                mimeType = "image/jpeg";
            }

            connection.setRequestProperty("Content-Type", mimeType);
            connection.setRequestProperty("x-upsert", "true");
            connection.setDoOutput(true);

            try (InputStream inputStream = getContentResolver().openInputStream(imageUri);
                 OutputStream outputStream = connection.getOutputStream()) {

                if (inputStream == null) return false;

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            Log.d(TAG, "Upload response: " + code + " " + response);
            return code >= 200 && code < 300;

        } catch (Exception e) {
            Log.e(TAG, "Image upload failed", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void saveImageRecord(String accessToken, String listingId, String imageUrl) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    "/rest/v1/listing_images",
                    "POST",
                    accessToken
            );

            JSONObject record = new JSONObject();
            record.put("listing_id", listingId);
            record.put("image_url", imageUrl);

            SupabaseHelper.writeJson(connection, record);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e(TAG, "Could not save image record: " + response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not save image record", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void updateListingImagePath(String accessToken, String listingId, String imageUrl) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    "/rest/v1/listings?id=eq." + SupabaseHelper.encode(listingId),
                    "PATCH",
                    accessToken
            );

            JSONObject body = new JSONObject();
            body.put("image_url", imageUrl);

            SupabaseHelper.writeJson(connection, body);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e(TAG, "Image URL update failed: " + response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not update image URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void updateListingAuthorityDoc(String accessToken, String listingId, String docUrl) {
        HttpURLConnection connection = null;

        try {
            connection = SupabaseHelper.createConnection(
                    "/rest/v1/listings?id=eq." + SupabaseHelper.encode(listingId),
                    "PATCH",
                    accessToken
            );

            JSONObject body = new JSONObject();
            body.put("authority_doc_url", docUrl);

            SupabaseHelper.writeJson(connection, body);

            int code = connection.getResponseCode();
            String response = SupabaseHelper.readResponse(connection);

            if (code < 200 || code >= 300) {
                Log.e(TAG, "Authority doc URL update failed: " + response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not update authority doc URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            submitButton.setEnabled(true);
            uploadImageButton.setEnabled(true);
            verifyLocationButton.setEnabled(true);

            Toast.makeText(AddListingActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}