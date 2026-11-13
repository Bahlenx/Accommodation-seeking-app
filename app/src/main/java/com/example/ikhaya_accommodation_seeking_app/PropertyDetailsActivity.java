
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
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PropertyDetailsActivity extends AppCompatActivity {

    private static final String TAG = "PropertyDetails";

    private ImageView propertyImage;
    private TextView galleryCounter;
    private View galleryControls;
    private final ArrayList<String> galleryUrls = new ArrayList<>();
    private int galleryIndex = 0;
    private int galleryRequestId = 0;

    private TextView txtPropertyName;
    private TextView txtPropertyPrice;
    private TextView txtPropertyLocation;
    private TextView propertyDescription;
    private TextView tvTermsSubtitle;
    private TextView tvVerifiedBadge;
    private TextView tvContactLabel;

    private ChipGroup chipGroupAmenities;
    private ChipGroup chipGroupRules;
    private MapView mapView;
    private ProgressBar progressTranslate;
    private Button btnTranslate;
    private Button btnApplyToRent;
    private ImageButton btnSave;
    private boolean isListingSaved = false;

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
    private String propertyType = "Room";
    private boolean isForSale = false;
    private boolean listingLoaded = false;
    private boolean viewingSubmitting = false;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(3);

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

        setupWindowInsets();

        listingId = getIntent().getStringExtra("LISTING_ID");

        if (listingId == null || listingId.trim().isEmpty()) {
            listingId = getIntent().getStringExtra("listingId");
        }
        if (listingId == null || listingId.trim().isEmpty()) {
            listingId = getIntent().getStringExtra("id");
        }

        if (getIntent().hasExtra("latitude")
                && getIntent().hasExtra("longitude")) {
            verifiedLatitude = getIntent().getDoubleExtra(
                    "latitude", Double.NaN
            );
            verifiedLongitude = getIntent().getDoubleExtra(
                    "longitude", Double.NaN
            );
        }

        initializeViews();
        setupEmbeddedMap();
        setupButtons();
        setupChipsAndDescription();
        setupRoleViews();

        if (listingId != null && !listingId.trim().isEmpty()) {
            loadListingFromSupabase();
        } else {
            Toast.makeText(
                    this,
                    "Property ID is missing.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.propertyDetailsRoot);
        View bookingBar = findViewById(R.id.floatingBookingBar);

        if (bookingBar == null) {
            return;
        }

        bookingBar.setPadding(dpToPx(18), 0, dpToPx(18), 0);

        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

                if (bookingBar.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                    CoordinatorLayout.LayoutParams params =
                            (CoordinatorLayout.LayoutParams) bookingBar.getLayoutParams();

                    params.bottomMargin = navigation.bottom + dpToPx(16);
                    bookingBar.setLayoutParams(params);
                    bookingBar.setPadding(dpToPx(18), 0, dpToPx(18), 0);
                }

                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    private void initializeViews() {
        propertyImage = findViewById(R.id.propertyImage);
        txtPropertyName = findViewById(R.id.propertyName);
        txtPropertyPrice = findViewById(R.id.propertyPrice);
        txtPropertyLocation = findViewById(R.id.propertyLocation);
        propertyDescription = findViewById(R.id.propertyDescription);
        tvTermsSubtitle = findViewById(R.id.tvTermsSubtitle);
        tvVerifiedBadge = findViewById(R.id.tvVerifiedBadge);
        tvContactLabel = findViewById(R.id.tvContactLabel);

        chipGroupAmenities = findViewById(R.id.chipGroupAmenities);
        chipGroupRules = findViewById(R.id.chipGroupRules);

        progressTranslate = findViewById(R.id.progressTranslate);
        btnTranslate = findViewById(R.id.btnTranslate);

        View btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btnBackToDiscover = findViewById(R.id.btnBackToDiscover);
        if (btnBackToDiscover != null) {
            btnBackToDiscover.setOnClickListener(v -> finish());
        }

        setupImageGalleryControls();
    }

    private void setupImageGalleryControls() {
        galleryCounter = findViewById(R.id.tvPhotoCounter);
        galleryControls = findViewById(
                R.id.propertyGalleryControls
        );

        View previous = findViewById(R.id.btnPreviousPhoto);
        View next = findViewById(R.id.btnNextPhoto);

        if (previous != null) {
            previous.setOnClickListener(
                    v -> showGalleryImage(galleryIndex - 1)
            );
        }

        if (next != null) {
            next.setOnClickListener(
                    v -> showGalleryImage(galleryIndex + 1)
            );
        }

        if (propertyImage != null) {
            propertyImage.setOnClickListener(
                    v -> showGalleryImage(galleryIndex + 1)
            );
        }

        if (galleryControls != null) {
            galleryControls.setVisibility(View.GONE);
        }
    }

    private void loadListingGallery(
            String accessToken,
            String coverUrl
    ) {
        executor.execute(() -> {
            ArrayList<String> urls = new ArrayList<>();

            String cleanedCover = clean(coverUrl);

            if (!cleanedCover.isEmpty()) {
                urls.add(cleanedCover);
            }

            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listing_images"
                                + "?select=image_url"
                                + "&listing_id=eq."
                                + URLEncoder.encode(
                                listingId,
                                "UTF-8"
                        ).replace("+", "%20");

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode >= 200
                        && responseCode < 300) {
                    JSONArray images = new JSONArray(response);

                    for (int i = 0; i < images.length(); i++) {
                        JSONObject image = images.optJSONObject(i);

                        if (image == null) {
                            continue;
                        }

                        String url = clean(
                                image.optString("image_url", "")
                        );

                        if (!url.isEmpty() && !urls.contains(url)) {
                            urls.add(url);
                        }
                    }
                } else {
                    Log.w(
                            TAG,
                            "Gallery request failed: HTTP "
                                    + responseCode
                                    + " "
                                    + response
                    );
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading gallery", e);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                galleryUrls.clear();
                galleryUrls.addAll(urls);
                galleryIndex = 0;

                if (galleryControls != null) {
                    galleryControls.setVisibility(
                            galleryUrls.size() > 1
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }

                if (!galleryUrls.isEmpty()) {
                    showGalleryImage(0);
                } else if (galleryCounter != null) {
                    galleryCounter.setText("0 / 0");
                }
            });
        });
    }

    private void showGalleryImage(int requestedIndex) {
        if (galleryUrls.isEmpty()) {
            return;
        }

        int size = galleryUrls.size();

        galleryIndex =
                ((requestedIndex % size) + size) % size;

        if (galleryCounter != null) {
            galleryCounter.setText(
                    (galleryIndex + 1) + " / " + size
            );
        }

        loadPropertyImage(galleryUrls.get(galleryIndex));
    }

    private void loadPropertyImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }

        final int requestId = ++galleryRequestId;

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        new URL(imageUrl).openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                int responseCode =
                        connection.getResponseCode();

                if (responseCode >= 200
                        && responseCode < 300) {
                    try (InputStream stream =
                                 connection.getInputStream()) {

                        Bitmap bitmap =
                                BitmapFactory.decodeStream(stream);

                        if (bitmap != null) {
                            runOnUiThread(() -> {
                                if (isFinishing()
                                        || isDestroyed()
                                        || requestId != galleryRequestId
                                        || propertyImage == null) {
                                    return;
                                }

                                propertyImage.setImageBitmap(bitmap);
                                propertyImage.setScaleType(
                                        ImageView.ScaleType.CENTER_CROP
                                );
                            });
                        }
                    }
                }

            } catch (Exception e) {
                Log.w(
                        TAG,
                        "Could not load photo: " + imageUrl,
                        e
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
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

        Locale locale =
                getResources().getConfiguration().getLocales().get(0);

        String language = locale.getLanguage();

        if (btnTranslate == null) {
            return;
        }

        btnTranslate.setVisibility(View.GONE);

        btnTranslate.setOnClickListener(v -> {
            if (originalDescription.trim().isEmpty()) {
                showFormError(R.string.description_not_available);
                return;
            }

            if (isTranslated) {
                if (txtPropertyName != null) {
                    txtPropertyName.setText(propertyName);
                }

                if (propertyDescription != null) {
                    propertyDescription.setText(
                            originalDescription
                    );
                }

                btnTranslate.setText(
                        getString(
                                R.string.translate_to_language,
                                getLanguageDisplayName(language)
                        )
                );

                isTranslated = false;
                return;
            }

            if (!cachedTranslatedDescription.isEmpty()) {
                displayTranslation(
                        cachedTranslatedDescription
                );
                return;
            }

            if (progressTranslate != null) {
                progressTranslate.setVisibility(View.VISIBLE);
            }

            btnTranslate.setEnabled(false);

            String text =
                    propertyName + " ::: " + originalDescription;

            GeminiTranslator.translate(
                    text,
                    language,
                    new GeminiTranslator.TranslationCallback() {
                        @Override
                        public void onSuccess(String result) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }

                                if (progressTranslate != null) {
                                    progressTranslate.setVisibility(
                                            View.GONE
                                    );
                                }

                                btnTranslate.setEnabled(true);
                                cachedTranslatedDescription = result;

                                displayTranslation(result);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }

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
                                                message
                                        ),
                                        Toast.LENGTH_SHORT
                                ).show();
                            });
                        }
                    }
            );
        });
    }

    private void displayTranslation(String result) {
        String[] parts = result.split(" ::: ", 2);

        if (parts.length >= 2) {
            if (txtPropertyName != null) {
                txtPropertyName.setText(parts[0].trim());
            }

            if (propertyDescription != null) {
                propertyDescription.setText(parts[1].trim());
            }

        } else if (propertyDescription != null) {
            propertyDescription.setText(result);
        }

        if (btnTranslate != null) {
            btnTranslate.setText(R.string.show_original);
        }

        isTranslated = true;
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
            String raw = clean(String.valueOf(rawValues));

            if (raw.isEmpty()) {
                return;
            }

            for (String value : raw.split(",")) {
                addChip(group, value);
            }
        }
    }

    private void addChip(ChipGroup group, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        Chip chip = new Chip(this);

        chip.setText(value.trim());
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setChipBackgroundColor(
                ColorStateList.valueOf(Color.WHITE)
        );
        chip.setTextColor(Color.parseColor("#1F1614"));
        chip.setTextSize(12);

        group.addView(chip);
    }

    private void setupButtons() {
        View btnGetDirections =
                findViewById(R.id.btnGetDirections);

        View btnOpenMap =
                findViewById(R.id.btnOpenMap);

        btnSave = findViewById(R.id.btnSave);

        View btnContact =
                findViewById(R.id.btnContactLandlord);

        if (btnGetDirections != null) {
            btnGetDirections.setOnClickListener(
                    v -> launchTurnByTurnNavigation()
            );
        }

        if (btnOpenMap != null) {
            btnOpenMap.setOnClickListener(
                    v -> openLocationInMaps(false)
            );
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> toggleSaveListing());
        }

        if (btnContact != null) {
            btnContact.setOnClickListener(
                    v -> contactLandlord()
            );
        }
    }

    private void setupRoleViews() {
        boolean isLandlord = getIntent().getBooleanExtra(
                "isLandlord",
                false
        );

        View btnContact =
                findViewById(R.id.btnContactLandlord);

        Button btnEditListing =
                findViewById(R.id.btnEditListing);

        btnApplyToRent =
                findViewById(R.id.btnApplyToRent);

        if (btnEditListing != null) {
            btnEditListing.setVisibility(View.GONE);
        }

        if (isLandlord) {
            if (btnContact != null) {
                btnContact.setVisibility(View.GONE);
            }

            if (btnApplyToRent != null) {
                btnApplyToRent.setVisibility(View.VISIBLE);
                btnApplyToRent.setText("Edit Listing");
                btnApplyToRent.setOnClickListener(v -> {
                    Intent intent = new Intent(this, EditListingActivity.class);
                    intent.putExtra("LISTING_ID", listingId);
                    intent.putExtra("listingId", listingId);
                    startActivity(intent);
                });
            }

        } else {
            if (btnContact != null) {
                btnContact.setVisibility(View.VISIBLE);
            }

            if (btnApplyToRent != null) {
                btnApplyToRent.setVisibility(View.VISIBLE);
                updateActionButtonsForOffering();
            }
        }
    }

    private void updateActionButtonsForOffering() {
        boolean isLandlord = getIntent().getBooleanExtra("isLandlord", false);

        if (isForSale) {
            if (tvTermsSubtitle != null) {
                tvTermsSubtitle.setText(
                        R.string.terms_sale_subtitle
                );
            }

            if (tvVerifiedBadge != null) {
                tvVerifiedBadge.setText(
                        R.string.verified_property_badge
                );
            }

            if (tvContactLabel != null) {
                tvContactLabel.setText(
                        R.string.contact_seller
                );
            }

        } else {
            if (tvTermsSubtitle != null) {
                tvTermsSubtitle.setText(
                        R.string.terms_rent_subtitle
                );
            }

            if (tvVerifiedBadge != null) {
                tvVerifiedBadge.setText(
                        R.string.verified_rental_badge
                );
            }

            if (tvContactLabel != null) {
                tvContactLabel.setText(
                        R.string.contact_caretaker
                );
            }
        }

        if (btnApplyToRent == null) {
            return;
        }

        if (isLandlord) {
            btnApplyToRent.setText("Edit Listing");
            btnApplyToRent.setVisibility(View.VISIBLE);
            btnApplyToRent.setOnClickListener(v -> {
                Intent intent = new Intent(this, EditListingActivity.class);
                intent.putExtra("LISTING_ID", listingId);
                intent.putExtra("listingId", listingId);
                startActivity(intent);
            });
        } else if (isForSale) {
            btnApplyToRent.setText(
                    R.string.btn_book_site_visit
            );
            btnApplyToRent.setOnClickListener(
                    v -> showActionOptions()
            );
        } else {
            btnApplyToRent.setText(
                    R.string.btn_schedule_viewing_action
            );
            btnApplyToRent.setOnClickListener(
                    v -> showActionOptions()
            );
        }
    }

    private void showActionOptions() {
        if (!listingLoaded) {
            Toast.makeText(
                    this,
                    "Property details are still loading.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String[] options;

        if (isForSale) {
            options = new String[]{
                    getString(R.string.btn_book_site_visit),
                    getString(R.string.btn_make_offer)
            };
        } else {
            options = new String[]{
                    getString(R.string.btn_schedule_viewing),
                    getString(R.string.btn_apply_to_rent)
            };
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        propertyName.isEmpty()
                                ? getString(R.string.app_name)
                                : propertyName
                )
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showScheduleViewingDialog();
                    } else {
                        showApplicationBottomSheet();
                    }
                })
                .show();
    }

    private void showScheduleViewingDialog() {
        if (!listingLoaded
                || listingId == null
                || listingId.trim().isEmpty()
                || landlordUid.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Property or landlord information is not ready.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (viewingSubmitting) {
            Toast.makeText(
                    this,
                    "Your viewing request is being submitted.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Calendar calendar = Calendar.getInstance();

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String selectedDate = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            dayOfMonth
                    );

                    showTimeSlotPicker(selectedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        picker.getDatePicker().setMinDate(
                System.currentTimeMillis() - 1000
        );

        picker.show();
    }

    private void showTimeSlotPicker(String viewingDate) {
        String[] slots = {
                "Morning (09:00 - 11:00)",
                "Midday (11:00 - 13:00)",
                "Afternoon (14:00 - 16:00)",
                "Late Afternoon (16:00 - 18:00)"
        };

        String title = isForSale
                ? "Select Site Inspection Time"
                : "Select Viewing Time";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(slots, (dialog, which) -> {
                    String selectedSlot =
                            viewingDate + " " + slots[which];

                    submitViewingToSupabase(selectedSlot);
                })
                .show();
    }

    private void submitViewingToSupabase(
            String formattedViewingTime
    ) {
        if (viewingSubmitting) {
            return;
        }

        String userId = SupabaseHelper.getUserId(this);
        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.trim().isEmpty()
                || accessToken == null
                || accessToken.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Please log in to schedule a viewing.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (!listingLoaded
                || listingId == null
                || listingId.trim().isEmpty()
                || landlordUid.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Property information is not ready yet.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (userId.equals(landlordUid)) {
            Toast.makeText(
                    this,
                    "You cannot book a viewing of your own property.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        viewingSubmitting = true;

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                JSONObject body = new JSONObject();

                body.put("tenant_uid", userId);
                body.put("landlord_uid", landlordUid);
                body.put("listing_id", listingId);
                body.put("property_name", propertyName);
                body.put("location", locationText);
                body.put("viewing_date", formattedViewingTime);
                body.put("status", "Pending Host");

                connection = SupabaseHelper.createConnection(
                        "/rest/v1/viewings",
                        "POST",
                        accessToken
                );

                connection.setRequestProperty(
                        "Prefer",
                        "return=representation"
                );

                SupabaseHelper.writeJson(connection, body);

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                Log.d(
                        TAG,
                        "Viewing request HTTP "
                                + responseCode
                                + ": "
                                + response
                );

                runOnUiThread(() -> {
                    viewingSubmitting = false;

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (responseCode >= 200
                            && responseCode < 300) {
                        AnalyticsHelper.logViewingBooked(this, listingId);
                        String successMessage = isForSale
                                ? "Your site inspection request was sent successfully."
                                : "Your viewing request was sent successfully.";

                        new AlertDialog.Builder(this)
                                .setTitle(R.string.title_request_sent)
                                .setMessage(
                                        successMessage
                                                + "\n\nProperty: "
                                                + propertyName
                                                + "\nViewing: "
                                                + formattedViewingTime
                                                + "\nStatus: Pending Host"
                                                + "\n\nYou can track this request in My Viewings."
                                )
                                .setNegativeButton(
                                        "Stay Here",
                                        null
                                )
                                .setPositiveButton(
                                        "My Viewings",
                                        (dialog, which) -> {
                                            Intent intent = new Intent(
                                                    PropertyDetailsActivity.this,
                                                    ViewingsActivity.class
                                            );
                                            startActivity(intent);
                                        }
                                )
                                .show();

                    } else {
                        Toast.makeText(
                                this,
                                "Could not schedule viewing (HTTP "
                                        + responseCode
                                        + "). Check Logcat.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Viewing submission failed", e);

                runOnUiThread(() -> {
                    viewingSubmitting = false;

                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(
                                this,
                                "Network error requesting appointment.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
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
            String title = isForSale
                    ? "Offer / Purchase Enquiry"
                    : getString(
                    R.string.application_dialog_title,
                    propertyName,
                    priceText
            );

            dialogTitle.setText(title);
        }

        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.dialog_discard_title)
                            .setMessage(R.string.dialog_discard_message)
                            .setPositiveButton(
                                    R.string.dialog_btn_discard,
                                    (d, which) -> dialog.dismiss()
                            )
                            .setNegativeButton(
                                    R.string.dialog_btn_continue,
                                    null
                            )
                            .show()
            );
        }

        final String[] chosenDate = {
                getString(R.string.default_move_in_date)
        };

        if (tvSelectedMoveInDate != null) {
            tvSelectedMoveInDate.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();

                DatePickerDialog picker = new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            chosenDate[0] = String.format(
                                    Locale.US,
                                    "%02d/%02d/%d",
                                    dayOfMonth,
                                    month + 1,
                                    year
                            );

                            tvSelectedMoveInDate.setText(
                                    chosenDate[0]
                            );
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );

                picker.getDatePicker().setMinDate(
                        System.currentTimeMillis() - 1000
                );

                picker.show();
            });
        }

        if (btnSubmit != null) {
            if (isForSale) {
                btnSubmit.setText("Submit Purchase Offer");
            }

            btnSubmit.setOnClickListener(v -> {
                String name = etApplicantName != null
                        ? etApplicantName.getText().toString().trim()
                        : "";

                String idNumber = etIdNumber != null
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

                if (idNumber.length() != 13) {
                    showFormError(R.string.err_enter_id);
                    return;
                }

                if (phone.length() != 10
                        || !phone.startsWith("0")) {
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
                                && !etTenantNote.getText()
                                .toString().trim().isEmpty()
                                ? etTenantNote.getText()
                                .toString().trim()
                                : getString(R.string.default_tenant_note);

                submitRentalApplicationToSupabase(
                        dialog,
                        btnSubmit,
                        name,
                        phone,
                        emergency,
                        occupation,
                        chosenDate[0],
                        duration,
                        occupants,
                        note
                );
            });
        }

        dialog.show();
    }

    private void submitRentalApplicationToSupabase(
            BottomSheetDialog dialog,
            Button btnSubmit,
            String name,
            String phone,
            String emergency,
            String occupation,
            String moveInDate,
            String duration,
            String occupants,
            String note
    ) {
        String tenantUid = SupabaseHelper.getUserId(this);
        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (tenantUid == null || tenantUid.trim().isEmpty()
                || accessToken == null
                || accessToken.trim().isEmpty()
                || listingId == null
                || listingId.trim().isEmpty()
                || landlordUid.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.rental_application_missing_details,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                JSONObject body = new JSONObject();

                body.put("listing_id", listingId);
                body.put("landlord_uid", landlordUid);
                body.put("tenant_uid", tenantUid);
                body.put("applicant_name", name);
                body.put("phone", phone);
                body.put("emergency_contact", emergency);
                body.put("occupation", occupation);
                body.put("move_in_date", moveInDate);
                body.put("lease_duration", duration);
                body.put("occupants", occupants);
                body.put("note", note);
                body.put("status", "PENDING");

                connection = SupabaseHelper.createConnection(
                        "/rest/v1/rental_applications",
                        "POST",
                        accessToken
                );

                SupabaseHelper.writeJson(connection, body);

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                Log.d(
                        TAG,
                        "Rental application HTTP "
                                + responseCode
                                + ": "
                                + response
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (responseCode >= 200
                            && responseCode < 300) {
                        dialog.dismiss();

                        Toast.makeText(
                                this,
                                R.string.rental_application_sent,
                                Toast.LENGTH_LONG
                        ).show();

                        if (btnApplyToRent != null) {
                            btnApplyToRent.setText(
                                    R.string.btn_pending_review
                            );

                            btnApplyToRent.setEnabled(false);
                            btnApplyToRent.setBackgroundTintList(
                                    ColorStateList.valueOf(Color.GRAY)
                            );
                        }

                    } else {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText(
                                isForSale
                                        ? "Submit Purchase Offer"
                                        : getString(R.string.btn_apply_to_rent)
                        );

                        Toast.makeText(
                                this,
                                "Application failed (HTTP "
                                        + responseCode
                                        + "). Check Logcat.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Rental application submission failed",
                        e
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(
                            isForSale
                                    ? "Submit Purchase Offer"
                                    : getString(R.string.btn_apply_to_rent)
                    );

                    Toast.makeText(
                            this,
                            R.string.rental_application_send_failed,
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showFormError(int messageResource) {
        Toast.makeText(
                this,
                messageResource,
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
        mapView.getController().setZoom(16.0);

        if (!Double.isNaN(verifiedLatitude)
                && !Double.isNaN(verifiedLongitude)) {
            plotMarkerOnMap(
                    verifiedLatitude,
                    verifiedLongitude
            );
        }
    }

    private void updateMapCoordinatesFromLocation(
            String location
    ) {
        if (location == null || location.trim().isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(
                        this,
                        Locale.getDefault()
                );

                List<Address> addresses =
                        geocoder.getFromLocationName(location, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    double latitude = address.getLatitude();
                    double longitude = address.getLongitude();

                    verifiedLatitude = latitude;
                    verifiedLongitude = longitude;

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            plotMarkerOnMap(
                                    latitude,
                                    longitude
                            );
                        }
                    });
                }

            } catch (Exception e) {
                Log.w(
                        TAG,
                        "Could not geocode property location",
                        e
                );
            }
        });
    }

    private void plotMarkerOnMap(
            double latitude,
            double longitude
    ) {
        if (mapView == null) {
            return;
        }

        GeoPoint point = new GeoPoint(
                latitude,
                longitude
        );

        mapView.getOverlays().clear();

        Marker marker = new Marker(mapView);

        marker.setPosition(point);
        marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
        );

        marker.setTitle(
                propertyName.isEmpty()
                        ? "Property Location"
                        : propertyName
        );

        marker.setSnippet(locationText);

        mapView.getOverlays().add(marker);
        mapView.getController().setCenter(point);
        mapView.getController().setZoom(17.5);
        mapView.invalidate();
    }

    private void launchTurnByTurnNavigation() {
        if (!Double.isNaN(verifiedLatitude)
                && !Double.isNaN(verifiedLongitude)
                && !Double.isInfinite(verifiedLatitude)
                && !Double.isInfinite(verifiedLongitude)) {

            Uri uri = Uri.parse(
                    "google.navigation:q="
                            + verifiedLatitude
                            + ","
                            + verifiedLongitude
                            + "&mode=d"
            );

            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    uri
            );

            intent.setPackage("com.google.android.apps.maps");

            try {
                startActivity(intent);
                return;
            } catch (Exception ignored) {
                // Use browser-based maps below.
            }
        }

        openLocationInMaps(true);
    }

    private void openLocationInMaps(boolean directions) {
        if (!Double.isNaN(verifiedLatitude) && !Double.isNaN(verifiedLongitude)
                && verifiedLatitude != 0.0 && verifiedLongitude != 0.0) {
            String label = locationText.isEmpty() ? propertyName : locationText;
            String geoUri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                    verifiedLatitude, verifiedLongitude, verifiedLatitude, verifiedLongitude, Uri.encode(label));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
            try {
                startActivity(mapIntent);
                return;
            } catch (Exception e) {
                String webUri = directions
                        ? String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%f,%f", verifiedLatitude, verifiedLongitude)
                        : String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f", verifiedLatitude, verifiedLongitude);
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUri)));
                    return;
                } catch (Exception ignored) {}
            }
        }

        if (locationText == null || locationText.trim().isEmpty()) {
            showFormError(R.string.location_not_available);
            return;
        }

        String encoded = Uri.encode(locationText);
        Uri uri = directions
                ? Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + encoded)
                : Uri.parse("https://www.google.com/maps/search/?api=1&query=" + encoded);

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            showFormError(R.string.could_not_open_maps);
        }
    }

    private void loadListingFromSupabase() {
        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (accessToken == null
                || accessToken.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        "/rest/v1/listings?select=*&id=eq."
                                + URLEncoder.encode(
                                listingId.trim(),
                                "UTF-8"
                        ).replace("+", "%20")
                                + "&limit=1";

                connection = SupabaseHelper.createConnection(
                        endpoint,
                        "GET",
                        accessToken
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                if (responseCode < 200
                        || responseCode >= 300) {
                    Log.e(
                            TAG,
                            "Could not load listing. HTTP "
                                    + responseCode
                                    + ": "
                                    + response
                    );

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(
                                    this,
                                    "Could not load property details.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });

                    return;
                }

                JSONArray listings = new JSONArray(response);

                if (listings.length() == 0) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(
                                    this,
                                    "Property not found.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });

                    return;
                }

                JSONObject listing =
                        listings.getJSONObject(0);

                String imageUrl =
                        listing.optString("image_url", "");

                String name =
                        listing.optString("property_name", "");

                String price =
                        listing.optString("price", "");

                String location =
                        listing.optString("location", "");

                String description =
                        listing.optString("description", "");

                propertyType = listing.optString(
                        "property_type",
                        "Room"
                );

                String offerType = listing.optString(
                        "offer_type",
                        "rent"
                );

                isForSale =
                        "sale".equalsIgnoreCase(offerType)
                                || "Farm".equalsIgnoreCase(propertyType)
                                || "Vacant Land".equalsIgnoreCase(propertyType)
                                || "Land".equalsIgnoreCase(propertyType);

                landlordUid = clean(
                        listing.optString("landlord_uid", "")
                );

                propertyName = clean(name);
                locationText = clean(location);
                originalDescription = clean(description);

                if (price == null
                        || price.trim().isEmpty()
                        || "null".equalsIgnoreCase(price.trim())) {
                    priceText = "";
                } else {
                    priceText = PriceUtils.formatDisplayPrice(price, isForSale);
                }

                listingLoaded = true;
                checkSavedStatus();
                AnalyticsHelper.logListingView(this, listingId, propertyName);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

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
                        propertyDescription.setText(
                                originalDescription
                        );
                    }

                    populateChipGroup(
                            chipGroupAmenities,
                            listing.opt("amenities")
                    );

                    populateChipGroup(
                            chipGroupRules,
                            listing.opt("rules")
                    );

                    updateActionButtonsForOffering();

                    Locale locale =
                            getResources()
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

                    enforceTenantSafetyGuard(listing);
                });

                loadListingGallery(accessToken, imageUrl);

                double latitude = listing.optDouble(
                        "latitude",
                        Double.NaN
                );

                double longitude = listing.optDouble(
                        "longitude",
                        Double.NaN
                );

                if (!Double.isNaN(latitude)
                        && !Double.isNaN(longitude)
                        && latitude != 0.0
                        && longitude != 0.0) {
                    verifiedLatitude = latitude;
                    verifiedLongitude = longitude;

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            plotMarkerOnMap(
                                    latitude,
                                    longitude
                            );
                        }
                    });
                } else {
                    updateMapCoordinatesFromLocation(location);
                }

            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Error loading listing details",
                        e
                );

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(
                                this,
                                "Network error loading property.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void checkSavedStatus() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()
                || listingId == null || listingId.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "/rest/v1/saved_listings?tenant_uid=eq." + SupabaseHelper.encode(userId)
                        + "&listing_id=eq." + SupabaseHelper.encode(listingId.trim());
                connection = SupabaseHelper.createConnection(endpoint, "GET", accessToken);
                int responseCode = connection.getResponseCode();
                String response = SupabaseHelper.readResponse(connection);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONArray arr = new JSONArray(response);
                    isListingSaved = arr.length() > 0;
                    runOnUiThread(() -> updateFavoriteButtonUI(isListingSaved));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking saved status", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void updateFavoriteButtonUI(boolean isSaved) {
        if (btnSave == null) return;
        if (isSaved) {
            btnSave.setImageResource(R.drawable.heart_filled);
            btnSave.setImageTintList(ColorStateList.valueOf(Color.parseColor("#DC3545")));
        } else {
            btnSave.setImageResource(R.drawable.heart);
            btnSave.setImageTintList(ColorStateList.valueOf(Color.parseColor("#BA5536")));
        }
    }

    private void toggleSaveListing() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken = SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            showFormError(R.string.please_log_in_to_save);
            return;
        }

        if (listingId == null || listingId.trim().isEmpty()) {
            Toast.makeText(this, "Property ID is missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isListingSaved) {
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    String endpoint = "/rest/v1/saved_listings?tenant_uid=eq." + SupabaseHelper.encode(userId)
                            + "&listing_id=eq." + SupabaseHelper.encode(listingId.trim());
                    connection = SupabaseHelper.createConnection(endpoint, "DELETE", accessToken);
                    int code = connection.getResponseCode();

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (code >= 200 && code < 300) {
                            isListingSaved = false;
                            updateFavoriteButtonUI(false);
                            Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Could not remove from favourites.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Remove favorite error", e);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, "Could not remove from favourites.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        } else {
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = SupabaseHelper.createConnection("/rest/v1/saved_listings", "POST", accessToken);
                    JSONObject body = new JSONObject();
                    body.put("tenant_uid", userId);
                    body.put("listing_id", listingId.trim());
                    SupabaseHelper.writeJson(connection, body);

                    int responseCode = connection.getResponseCode();

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        if ((responseCode >= 200 && responseCode < 300) || responseCode == 409) {
                            isListingSaved = true;
                            updateFavoriteButtonUI(true);
                            Toast.makeText(this, "Saved to favourites!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Could not save to favourites.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Save listing error", e);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, "Could not save to favourites.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }
    }

    private void contactLandlord() {
        String userId = SupabaseHelper.getUserId(this);
        String accessToken =
                SupabaseHelper.getAccessToken(this);

        if (userId == null || userId.isEmpty()
                || accessToken == null
                || accessToken.isEmpty()) {
            showFormError(
                    R.string.please_log_in_to_contact_landlord
            );
            return;
        }

        if (listingId == null || listingId.isEmpty()
                || landlordUid.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Landlord information is not available yet.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (userId.equals(landlordUid)) {
            Toast.makeText(
                    this,
                    "This is your own property.",
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

                int responseCode =
                        connection.getResponseCode();

                String response =
                        SupabaseHelper.readResponse(connection);

                Log.d(
                        TAG,
                        "Contact landlord HTTP "
                                + responseCode
                                + ": "
                                + response
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (responseCode >= 200
                            && responseCode < 300) {
                        showFormError(R.string.enquiry_sent);
                    } else {
                        showFormError(
                                R.string.could_not_send_enquiry
                        );
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Contact landlord error", e);

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showFormError(
                                R.string.could_not_send_enquiry
                        );
                    }
                });

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

    private int dpToPx(int dp) {
        float density =
                getResources().getDisplayMetrics().density;

        return Math.round(dp * density);
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

    private void enforceTenantSafetyGuard(JSONObject listingObj) {
        String status = listingObj.optString("status", "APPROVED");
        boolean isArchived = listingObj.optBoolean("is_archived", false);

        JSONObject landlordProfile = listingObj.optJSONObject("profiles");
        String landlordIdentityStatus = landlordProfile != null ? landlordProfile.optString("identity_status", "VERIFIED") : "VERIFIED";

        boolean isFullyAccessible = !isArchived
                && !"REMOVED_BY_ADMIN".equalsIgnoreCase(status)
                && !"DELETED".equalsIgnoreCase(status)
                && !"SUSPENDED_FOR_REVERIFICATION".equalsIgnoreCase(status)
                && ("VERIFIED".equalsIgnoreCase(landlordIdentityStatus) || landlordIdentityStatus.isEmpty());

        Button btnRequestViewing = findViewById(R.id.btnApplyToRent);
        View btnContactLandlord = findViewById(R.id.btnContactLandlord);
        View layoutUnavailableOverlay = findViewById(R.id.layoutUnavailableOverlay);
        TextView tvUnavailableReason = findViewById(R.id.tvUnavailableReason);

        if (!isFullyAccessible) {
            if (btnRequestViewing != null) {
                btnRequestViewing.setEnabled(false);
                btnRequestViewing.setAlpha(0.5f);
            }
            if (btnContactLandlord != null) {
                btnContactLandlord.setEnabled(false);
                btnContactLandlord.setAlpha(0.5f);
            }

            if (layoutUnavailableOverlay != null) {
                layoutUnavailableOverlay.setVisibility(View.VISIBLE);
            }

            if (tvUnavailableReason != null) {
                if (isArchived || "DELETED".equalsIgnoreCase(status)) {
                    tvUnavailableReason.setText("This listing has been removed or archived by the owner.");
                } else if ("SUSPENDED_FOR_REVERIFICATION".equalsIgnoreCase(status) || "CHANGES_PENDING_REVIEW".equalsIgnoreCase(status)) {
                    tvUnavailableReason.setText("This property is currently undergoing routine administrative re-verification.");
                } else {
                    tvUnavailableReason.setText("This property is temporarily unavailable.");
                }
            }

            Toast.makeText(this, "This listing is currently unavailable for viewing requests.", Toast.LENGTH_LONG).show();
        } else {
            if (layoutUnavailableOverlay != null) {
                layoutUnavailableOverlay.setVisibility(View.GONE);
            }
            if (btnRequestViewing != null) {
                btnRequestViewing.setEnabled(true);
                btnRequestViewing.setAlpha(1.0f);
            }
            if (btnContactLandlord != null) {
                btnContactLandlord.setEnabled(true);
                btnContactLandlord.setAlpha(1.0f);
            }
        }
    }
}
