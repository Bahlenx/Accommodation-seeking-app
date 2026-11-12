package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminInspectionActivity extends AppCompatActivity {

    private ImageView ivInspectImage;
    private TextView tvInspectTitle;
    private TextView tvInspectDetails;
    private TextView tvInspectLandlord;
    private TextView tvInspectDescription;
    private TextView tvInspectUtilities;

    private TextView tvInspectStatusBadge;
    private TextView tvReverificationReason;
    private TextView tvAuthorityDocInfo;
    private Button btnViewAuthorityDoc;
    private LinearLayout layoutDiffContainer;

    private TextView tvLandlordName;
    private TextView tvLandlordSaId;
    private TextView tvLandlordIdentityStatus;
    private Button btnViewIdDoc;
    private Button btnViewSelfie;

    private Button btnRequireChanges;
    private Button btnApprove;
    private Button btnTakedown;

    private MapView adminMapView;
    private TextView tvAdminCoords;

    private String listingId = "";
    private String landlordUid = "";
    private String propertyTitle = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Initialize osmdroid BEFORE setContentView()
        IConfigurationProvider osmConfig = Configuration.getInstance();
        osmConfig.load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        osmConfig.setUserAgentValue("iKhayaStudentHousingApp/1.0 (contact: support@ikhaya.co.za)");

        // Force clear any previously cached 403 error tiles
        File tileCache = osmConfig.getOsmdroidTileCache();
        if (tileCache != null && tileCache.exists()) {
            deleteRecursive(tileCache);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_inspection);

        listingId = getIntent().getStringExtra("LISTING_ID");
        if (listingId == null || listingId.isEmpty()) {
            Toast.makeText(this, R.string.error_missing_listing_id, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adminMapView = findViewById(R.id.adminInspectionMapView);
        tvAdminCoords = findViewById(R.id.tvAdminCoords);

        if (adminMapView != null) {
            XYTileSource tileSource = new XYTileSource(
                    "OpenStreetMapDE",
                    0,
                    19,
                    256,
                    ".png",
                    new String[]{"https://tile.openstreetmap.de/"},
                    "© OpenStreetMap contributors"
            );
            adminMapView.setTileSource(tileSource);
            adminMapView.setMultiTouchControls(true);
            adminMapView.getController().setZoom(16.0);
        }

        ivInspectImage = findViewById(R.id.ivInspectImage);
        tvInspectTitle = findViewById(R.id.tvInspectTitle);
        tvInspectDetails = findViewById(R.id.tvInspectDetails);
        tvInspectLandlord = findViewById(R.id.tvInspectLandlord);
        tvInspectDescription = findViewById(R.id.tvInspectDescription);
        tvInspectUtilities = findViewById(R.id.tvInspectUtilities);

        tvInspectStatusBadge = findViewById(R.id.tvInspectStatusBadge);
        tvReverificationReason = findViewById(R.id.tvReverificationReason);
        tvAuthorityDocInfo = findViewById(R.id.tvAuthorityDocInfo);
        btnViewAuthorityDoc = findViewById(R.id.btnViewAuthorityDoc);
        layoutDiffContainer = findViewById(R.id.layoutDiffContainer);

        btnRequireChanges = findViewById(R.id.btnRequireChanges);
        btnApprove = findViewById(R.id.btnApprove);
        btnTakedown = findViewById(R.id.btnTakedown);
        if (btnTakedown != null) {
            btnTakedown.setOnClickListener(v -> showTakedownDialog());
        }

        tvLandlordName = findViewById(R.id.tvLandlordName);
        tvLandlordSaId = findViewById(R.id.tvLandlordSaId);
        tvLandlordIdentityStatus = findViewById(R.id.tvLandlordIdentityStatus);
        btnViewIdDoc = findViewById(R.id.btnViewIdDoc);
        btnViewSelfie = findViewById(R.id.btnViewSelfie);

        View btnBack = findViewById(R.id.btnBackInspection);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Apply fast intent extras if supplied
        String passedTitle = getIntent().getStringExtra("LISTING_TITLE");
        if (passedTitle != null && !passedTitle.isEmpty()) {
            tvInspectTitle.setText(passedTitle);
        }

        String passedDetails = getIntent().getStringExtra("LISTING_DETAILS");
        if (passedDetails != null && !passedDetails.isEmpty()) {
            tvInspectDetails.setText(passedDetails);
        }

        String passedLandlord = getIntent().getStringExtra("LANDLORD_NAME");
        if (passedLandlord != null && !passedLandlord.isEmpty()) {
            tvInspectLandlord.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.label_submitted_by), passedLandlord));
        }

        String status = getIntent().getStringExtra("LISTING_STATUS");
        setupActionButtons(status);

        fetchFullListingDetails();
    }

    private void setupActionButtons(String status) {
        if ("APPROVED".equalsIgnoreCase(status)) {
            if (btnApprove != null) {
                btnApprove.setVisibility(View.GONE);
            }
            if (btnRequireChanges != null) {
                btnRequireChanges.setText(R.string.btn_revoke_live_status);
                btnRequireChanges.setOnClickListener(v -> showComplianceDialog());
            }
        } else {
            if (btnRequireChanges != null) {
                btnRequireChanges.setText(R.string.btn_require_fixes);
                btnRequireChanges.setOnClickListener(v -> showComplianceDialog());
            }
            if (btnApprove != null) {
                btnApprove.setVisibility(View.VISIBLE);
                btnApprove.setOnClickListener(v -> approveListing(listingId, landlordUid, propertyTitle));
            }
        }
    }

    private void openAuthorityDoc(String urlStr) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open authority document link", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchFullListingDetails() {
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String cleanId = listingId.trim();
                String endpoint = "/rest/v1/listings?select=*&id=eq." + URLEncoder.encode(cleanId, StandardCharsets.UTF_8.name()) + "&limit=1";
                conn = SupabaseHelper.createConnection(endpoint, "GET", token);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(conn));
                    if (arr.length() > 0) {
                        JSONObject listing = arr.getJSONObject(0);

                        propertyTitle = listing.optString("property_name", listing.optString("title", getString(R.string.default_untitled_property)));
                        landlordUid = listing.optString("landlord_id", listing.optString("landlord_uid", ""));

                        if (!landlordUid.isEmpty()) {
                            loadLandlordProfile(landlordUid);
                        }

                        String currentStatus = listing.optString("status", "PENDING_VERIFICATION");
                        String docTypeKey = listing.optString("authority_doc_type", "MUNICIPAL_RATES");
                        String docTypeLabel = AddListingActivity.getDocTypeLabel(docTypeKey);
                        String docUrl = listing.optString("authority_doc_url", "");
                        String reverificationReason = listing.optString("reverification_reason", "");

                        JSONObject originalPayload = listing.optJSONObject("original_payload");
                        List<ListingDiff> diffs = ListingDiff.computeDiffs(originalPayload, listing);

                        String price = listing.optString("price", "0");
                        String loc = listing.optString("location", listing.optString("address", getString(R.string.default_unknown_location)));
                        String type = listing.optString("property_type", getString(R.string.filter_chip_rooms));
                        String rooms = listing.optString("rooms", "1");
                        String desc = listing.optString("description", getString(R.string.default_no_description));
                        String imageUrl = listing.optString("image_url", "");

                        String water = listing.optString("water_source", "");
                        String electricity = listing.optString("electricity", "");
                        String sanitation = listing.optString("sanitation", "");
                        JSONArray amenitiesArr = listing.optJSONArray("amenities");

                        StringBuilder utilitiesStr = new StringBuilder();

                        if (!water.isEmpty() && !"null".equalsIgnoreCase(water)) {
                            utilitiesStr.append("• Water: ").append(water).append("\n");
                        }
                        if (!electricity.isEmpty() && !"null".equalsIgnoreCase(electricity)) {
                            utilitiesStr.append("• Electricity: ").append(electricity).append("\n");
                        }
                        if (!sanitation.isEmpty() && !"null".equalsIgnoreCase(sanitation)) {
                            utilitiesStr.append("• Sanitation: ").append(sanitation).append("\n");
                        }

                        // Fallback: Check amenities JSON array if discrete fields are null
                        if (amenitiesArr != null && amenitiesArr.length() > 0) {
                            utilitiesStr.append("• Included Features: ");
                            for (int i = 0; i < amenitiesArr.length(); i++) {
                                if (i > 0) utilitiesStr.append(", ");
                                utilitiesStr.append(amenitiesArr.optString(i));
                            }
                        }

                        if (utilitiesStr.length() == 0) {
                            utilitiesStr.append("No specific utilities or amenities specified.");
                        }

                        String offerType = listing.optString("offer_type", "rent");
                        boolean isSale = "sale".equalsIgnoreCase(offerType)
                                || "Farm".equalsIgnoreCase(type)
                                || "Vacant Stand / Yard Space".equalsIgnoreCase(type)
                                || "Vacant Land".equalsIgnoreCase(type)
                                || "Land".equalsIgnoreCase(type);

                        String purposeTag = isSale ? getString(R.string.badge_for_sale) : getString(R.string.badge_for_rent);
                        String priceFormatted = isSale ? ("R " + price) : ("R " + price + " / month");

                        String detailsStr = String.format(Locale.getDefault(), "%s • %s • %s %s\n%s • %s",
                                purposeTag, type, rooms, getString(R.string.label_rooms_count), loc, priceFormatted);
                        String landlordShortId = landlordUid.length() > 8 ? landlordUid.substring(0, 8) : landlordUid;
                        String landlordStr = String.format(Locale.getDefault(), "%s: %s (%s...)",
                                getString(R.string.label_submitted_by), getString(R.string.role_landlord), landlordShortId);

                        double lat = listing.optDouble("latitude", listing.optDouble("lat", 0.0));
                        double lng = listing.optDouble("longitude", listing.optDouble("lng", 0.0));

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            tvInspectTitle.setText(propertyTitle);
                            tvInspectDetails.setText(detailsStr);
                            tvInspectLandlord.setText(landlordStr);
                            tvInspectDescription.setText(desc);
                            if (tvInspectUtilities != null) {
                                tvInspectUtilities.setText(utilitiesStr.toString().trim());
                            }

                            renderPropertyPinOnAdminMap(lat, lng, propertyTitle);

                            // Re-bind click listener with updated data
                            setupActionButtons(currentStatus);

                            if (tvInspectStatusBadge != null) {
                                tvInspectStatusBadge.setText(currentStatus);
                                if ("SUSPENDED_FOR_REVERIFICATION".equalsIgnoreCase(currentStatus)) {
                                    tvInspectStatusBadge.setBackgroundColor(Color.parseColor("#DC3545"));
                                    tvInspectStatusBadge.setTextColor(Color.WHITE);
                                    if (tvReverificationReason != null) {
                                        tvReverificationReason.setVisibility(View.VISIBLE);
                                        tvReverificationReason.setText("Reason: " + (reverificationReason.isEmpty() ? "High-risk fields updated." : reverificationReason));
                                    }
                                } else if ("CHANGES_PENDING_REVIEW".equalsIgnoreCase(currentStatus)) {
                                    tvInspectStatusBadge.setBackgroundColor(Color.parseColor("#2CA8FF"));
                                    tvInspectStatusBadge.setTextColor(Color.WHITE);
                                    if (tvReverificationReason != null) tvReverificationReason.setVisibility(View.GONE);
                                } else if ("APPROVED".equalsIgnoreCase(currentStatus) || "VERIFIED".equalsIgnoreCase(currentStatus)) {
                                    tvInspectStatusBadge.setBackgroundColor(Color.parseColor("#2DCE89"));
                                    tvInspectStatusBadge.setTextColor(Color.WHITE);
                                    if (tvReverificationReason != null) tvReverificationReason.setVisibility(View.GONE);
                                } else {
                                    tvInspectStatusBadge.setBackgroundColor(Color.parseColor("#BA5536"));
                                    tvInspectStatusBadge.setTextColor(Color.WHITE);
                                    if (tvReverificationReason != null) tvReverificationReason.setVisibility(View.GONE);
                                }
                            }

                            if (tvAuthorityDocInfo != null) {
                                tvAuthorityDocInfo.setText("Doc Type: " + docTypeLabel);
                            }

                            if (btnViewAuthorityDoc != null) {
                                if (!docUrl.isEmpty()) {
                                    btnViewAuthorityDoc.setVisibility(View.VISIBLE);
                                    btnViewAuthorityDoc.setOnClickListener(v -> openAuthorityDoc(docUrl));
                                } else {
                                    btnViewAuthorityDoc.setVisibility(View.GONE);
                                }
                            }

                            if (layoutDiffContainer != null) {
                                layoutDiffContainer.removeAllViews();
                                if (diffs == null || diffs.isEmpty()) {
                                    TextView noDiffView = new TextView(this);
                                    noDiffView.setText("Initial submission (No historical diffs)");
                                    noDiffView.setTextColor(Color.parseColor("#8C7A70"));
                                    noDiffView.setTextSize(13);
                                    layoutDiffContainer.addView(noDiffView);
                                } else {
                                    for (ListingDiff diff : diffs) {
                                        TextView diffTextView = new TextView(this);
                                        diffTextView.setText("• " + diff.getFormattedDiff());
                                        diffTextView.setTextSize(13);
                                        diffTextView.setPadding(0, 4, 0, 4);

                                        if (diff.isHighRisk()) {
                                            diffTextView.setTextColor(ContextCompat.getColor(this, R.color.colorHighRiskRed));
                                            diffTextView.setTypeface(null, Typeface.BOLD);
                                        } else {
                                            diffTextView.setTextColor(ContextCompat.getColor(this, R.color.colorLowRiskBlue));
                                        }

                                        layoutDiffContainer.addView(diffTextView);
                                    }
                                }
                            }
                        });

                        if (!imageUrl.isEmpty()) {
                            loadPropertyImageAsync(imageUrl);
                        }
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.error_failed_load_listing_data, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void loadPropertyImageAsync(String urlStr) {
        executor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = conn.getInputStream()) {
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        if (bmp != null) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                ivInspectImage.setImageBitmap(bmp);
                            });
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) { }
        });
    }

    private void showComplianceDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.hint_compliance_reason);
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_require_changes)
                .setMessage(R.string.msg_require_changes_instructions)
                .setView(input)
                .setPositiveButton(R.string.btn_send_back, (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, R.string.error_must_provide_instructions, Toast.LENGTH_LONG).show();
                        return;
                    }
                    requestReverification(listingId, landlordUid, propertyTitle, reason);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void approveListing(String currentListingId, String landlordId, String title) {
        if (btnApprove != null) btnApprove.setEnabled(false);
        if (btnRequireChanges != null) btnRequireChanges.setEnabled(false);
        if (btnTakedown != null) btnTakedown.setEnabled(false);

        JSONObject updatePayload = new JSONObject();
        try {
            updatePayload.put("status", "APPROVED");
            updatePayload.put("availability", "Available");
            updatePayload.put("is_archived", false);
            updatePayload.put("reverification_reason", "");
            updatePayload.put("updated_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));
            updatePayload.put("original_payload", JSONObject.NULL);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String cleanId = currentListingId.trim();
        String endpoint = "/rest/v1/listings?id=eq." + SupabaseHelper.encode(cleanId);
        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            try {
                SupabaseHelper.patch(endpoint, updatePayload, token);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (landlordId != null && !landlordId.isEmpty()) {
                        SupabaseHelper.createNotification(
                                landlordId,
                                "Listing Approved!",
                                "Your property '" + (title.isEmpty() ? "Listing" : title) + "' has been verified and is now live on Discover.",
                                token
                        );
                    }

                    Toast.makeText(AdminInspectionActivity.this, "Listing approved and live!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(AdminInspectionActivity.this, "Failed to approve listing: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (btnApprove != null) btnApprove.setEnabled(true);
                    if (btnRequireChanges != null) btnRequireChanges.setEnabled(true);
                    if (btnTakedown != null) btnTakedown.setEnabled(true);
                });
            }
        });
    }

    private void requestReverification(String currentListingId, String landlordId, String title, String reason) {
        if (btnApprove != null) btnApprove.setEnabled(false);
        if (btnRequireChanges != null) btnRequireChanges.setEnabled(false);
        if (btnTakedown != null) btnTakedown.setEnabled(false);

        String token = SupabaseHelper.getAccessToken(this);

        executor.execute(() -> {
            try {
                String cleanId = currentListingId.trim();
                String endpoint = "/rest/v1/listings?id=eq." + SupabaseHelper.encode(cleanId);
                
                JSONObject updatePayload = new JSONObject();
                updatePayload.put("status", "SUSPENDED_FOR_REVERIFICATION");
                updatePayload.put("reverification_reason", reason);

                // Execute PATCH request
                SupabaseHelper.patch(endpoint, updatePayload, token);

                if (landlordId != null && !landlordId.isEmpty()) {
                    String notifTitle = "Action Required on Listing";
                    String notifMsg = "Your property '" + (title.isEmpty() ? "Listing" : title) + "' requires update: " + reason;
                    SupabaseHelper.createNotification(landlordId, notifTitle, notifMsg, token);
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Landlord notified of re-verification request.", Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e("AdminInspection", "Reverification Error: ", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Failed to request updates: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (btnApprove != null) btnApprove.setEnabled(true);
                    if (btnRequireChanges != null) btnRequireChanges.setEnabled(true);
                    if (btnTakedown != null) btnTakedown.setEnabled(true);
                });
            }
        });
    }

    private void showTakedownDialog() {
        EditText input = new EditText(this);
        input.setHint("e.g., Removed by Admin due to fraudulent documentation");
        input.setText("Removed by Admin due to fraudulent documentation");
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle("Takedown / Force Remove")
                .setMessage("Provide reason for takedown:")
                .setView(input)
                .setPositiveButton("Takedown", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = "Removed by Admin due to fraudulent documentation";
                    }
                    submitTakedownDecision(reason);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void submitTakedownDecision(String reason) {
        String token = SupabaseHelper.getAccessToken(this);
        if (btnApprove != null) btnApprove.setEnabled(false);
        if (btnRequireChanges != null) btnRequireChanges.setEnabled(false);
        if (btnTakedown != null) btnTakedown.setEnabled(false);

        executor.execute(() -> {
            try {
                String cleanId = listingId.trim();
                String endpoint = "/rest/v1/listings?id=eq." + SupabaseHelper.encode(cleanId);
                JSONObject body = new JSONObject();
                body.put("status", "REMOVED_BY_ADMIN");
                body.put("is_archived", true);
                body.put("reverification_reason", reason);

                SupabaseHelper.patch(endpoint, body, token);

                if (!landlordUid.isEmpty()) {
                    String notifTitle = "Listing Taken Down: " + propertyTitle;
                    String notifMsg = "Your listing was removed by Admin. Reason: " + reason;
                    SupabaseHelper.createNotification(landlordUid, notifTitle, notifMsg, token);
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Listing taken down successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Takedown failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (btnApprove != null) btnApprove.setEnabled(true);
                    if (btnRequireChanges != null) btnRequireChanges.setEnabled(true);
                    if (btnTakedown != null) btnTakedown.setEnabled(true);
                });
            }
        });
    }

    private void loadLandlordProfile(String landlordId) {
        if (landlordId == null || landlordId.trim().isEmpty()) {
            tvLandlordName.setText("Landlord: Unknown");
            tvLandlordSaId.setText("SA ID: Not provided");
            tvLandlordIdentityStatus.setText("Identity Status: UNVERIFIED");
            return;
        }

        SupabaseHelper.fetchProfileById(landlordId, (profileObj, error) -> {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (profileObj != null) {
                    String fullName = profileObj.optString("full_name", profileObj.optString("name", "Unknown Landlord"));
                    String saId = profileObj.optString("sa_id_number", "");
                    String identityStatus = profileObj.optString("identity_status", "UNVERIFIED");

                    String idDocUrl = profileObj.optString("id_document_url", "");
                    String selfieUrl = profileObj.optString("selfie_url", profileObj.optString("verification_selfie_url", ""));

                    tvLandlordName.setText("Landlord: " + fullName);

                    if (saId.isEmpty()) {
                        tvLandlordSaId.setText("SA ID: Not provided (Legacy Account)");
                    } else {
                        tvLandlordSaId.setText("SA ID: " + saId);
                    }

                    tvLandlordIdentityStatus.setText("Identity Status: " + identityStatus);

                    if ("VERIFIED".equalsIgnoreCase(identityStatus)) {
                        tvLandlordIdentityStatus.setTextColor(Color.parseColor("#28A745"));
                    } else if ("PENDING_VERIFICATION".equalsIgnoreCase(identityStatus)) {
                        tvLandlordIdentityStatus.setTextColor(Color.parseColor("#FFC107"));
                    } else {
                        tvLandlordIdentityStatus.setTextColor(Color.parseColor("#DC3545"));
                    }

                    if (btnViewIdDoc != null) {
                        btnViewIdDoc.setEnabled(!idDocUrl.isEmpty());
                        btnViewIdDoc.setOnClickListener(v -> openUrlIntent(idDocUrl));
                    }
                    if (btnViewSelfie != null) {
                        btnViewSelfie.setEnabled(!selfieUrl.isEmpty());
                        btnViewSelfie.setOnClickListener(v -> openUrlIntent(selfieUrl));
                    }
                } else {
                    tvLandlordName.setText("Landlord ID: " + landlordId);
                    tvLandlordSaId.setText("SA ID: Profile Record Not Found");
                    tvLandlordIdentityStatus.setText("Identity Status: UNVERIFIED");
                }
            });
        });
    }

    private void openUrlIntent(String urlStr) {
        try {
            if (urlStr == null || urlStr.isEmpty()) {
                Toast.makeText(this, "Document link not available", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open document link", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderPropertyPinOnAdminMap(double lat, double lng, String title) {
        if (adminMapView == null) return;

        if (lat == 0.0 && lng == 0.0) {
            if (tvAdminCoords != null) tvAdminCoords.setText("No GPS coordinates pinned");
            adminMapView.setVisibility(View.GONE);
            return;
        }

        adminMapView.setVisibility(View.VISIBLE);
        if (tvAdminCoords != null) {
            tvAdminCoords.setText(String.format(Locale.US, "GPS: %.5f, %.5f", lat, lng));
        }

        GeoPoint point = new GeoPoint(lat, lng);
        adminMapView.getController().setCenter(point);
        adminMapView.getController().setZoom(16.0);

        adminMapView.getOverlays().clear();
        Marker marker = new Marker(adminMapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title != null ? title : "Pinned Property Location");
        adminMapView.getOverlays().add(marker);
        adminMapView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adminMapView != null) {
            adminMapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adminMapView != null) {
            adminMapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}