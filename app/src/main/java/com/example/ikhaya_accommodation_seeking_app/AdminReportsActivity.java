package com.example.ikhaya_accommodation_seeking_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminReportsActivity extends AppCompatActivity {

    private LinearLayout reportsContainer;
    private LinearLayout ticketsListContainer;
    private ProgressBar progressReports;
    private TextView tvEmptyReports;

    private TextView tabIssueTickets, tabGuestChats;
    private boolean showingGuestChats = false;

    private String activeIssueFilter = "ALL";
    private String currentSearchQuery = "";
    private boolean isCompactListView = true;
    private JSONArray cachedReportsArray = new JSONArray();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBg(String fillHex, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private GradientDrawable roundedStrokeBg(String fillHex, String strokeHex, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(radiusDp));
        gd.setStroke(dp(1), Color.parseColor(strokeHex));
        return gd;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_reports);

        View btnBack = findViewById(R.id.btnBackToHub);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        reportsContainer = findViewById(R.id.reportsContainer);
        progressReports = findViewById(R.id.progressReports);
        tvEmptyReports = findViewById(R.id.tvEmptyReports);

        View layoutSupportTabs = findViewById(R.id.layoutSupportTabs);
        if (layoutSupportTabs != null) {
            layoutSupportTabs.setBackground(roundedBg("#EAE3DC", 12));
        }

        tabIssueTickets = findViewById(R.id.tabIssueTickets);
        tabGuestChats = findViewById(R.id.tabGuestChats);

        tabIssueTickets.setBackground(roundedBg("#B85032", 9));
        tabGuestChats.setBackground(roundedBg("#00000000", 9));

        tabIssueTickets.setOnClickListener(v -> {
            if (showingGuestChats) {
                showingGuestChats = false;
                tabIssueTickets.setBackground(roundedBg("#B85032", 9));
                tabIssueTickets.setTextColor(Color.WHITE);
                tabGuestChats.setBackground(roundedBg("#00000000", 9));
                tabGuestChats.setTextColor(Color.parseColor("#6D635E"));
                loadReports();
            }
        });

        tabGuestChats.setOnClickListener(v -> {
            if (!showingGuestChats) {
                showingGuestChats = true;
                tabGuestChats.setBackground(roundedBg("#B85032", 9));
                tabGuestChats.setTextColor(Color.WHITE);
                tabIssueTickets.setBackground(roundedBg("#00000000", 9));
                tabIssueTickets.setTextColor(Color.parseColor("#6D635E"));
                loadGuestSupportChats();
            }
        });

        loadReports();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (showingGuestChats) {
            loadGuestSupportChats();
        } else {
            loadReports();
        }
        refreshTabCounts();
    }

    private String formatReportDateTime(String rawIso) {
        if (rawIso == null || rawIso.trim().isEmpty() || "null".equalsIgnoreCase(rawIso.trim())) {
            return "Recent";
        }
        try {
            String normalized = rawIso.trim();
            if (normalized.contains(".")) {
                int dotIdx = normalized.indexOf('.');
                int plusIdx = normalized.indexOf('+', dotIdx);
                int zIdx = normalized.indexOf('Z', dotIdx);
                int endIdx = plusIdx != -1 ? plusIdx : (zIdx != -1 ? zIdx : normalized.length());
                normalized = normalized.substring(0, dotIdx) + normalized.substring(endIdx);
            }
            if (normalized.endsWith("+00:00") || normalized.endsWith("-00:00")) {
                normalized = normalized.substring(0, normalized.length() - 6) + "Z";
            }
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(normalized);
            if (date != null) {
                SimpleDateFormat outFmt = new SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.US);
                outFmt.setTimeZone(TimeZone.getDefault());
                return outFmt.format(date);
            }
        } catch (Exception ignored) {
        }
        String fallback = rawIso.replace("T", " ");
        return fallback.length() > 16 ? fallback.substring(0, 16) : fallback;
    }

    private String formatReportDateSearchable(String rawIso) {
        if (rawIso == null || rawIso.trim().isEmpty() || "null".equalsIgnoreCase(rawIso.trim())) {
            return "";
        }
        return (formatReportDateTime(rawIso) + " " + rawIso).toLowerCase(Locale.ROOT);
    }

    private void loadReports() {
        String token = SupabaseHelper.getAccessToken(this);

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.please_log_in_again, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (progressReports != null) {
            progressReports.setVisibility(View.VISIBLE);
        }
        if (tvEmptyReports != null) {
            tvEmptyReports.setVisibility(View.GONE);
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection("/rest/v1/reports?select=*&status=in.(OPEN,open,PENDING,pending)&order=created_at.desc", "GET", token);
                int code = conn.getResponseCode();
                String response = SupabaseHelper.readResponse(conn);

                if (code >= 200 && code < 300) {
                    cachedReportsArray = new JSONArray(response);
                    runOnUiThread(() -> {
                        if (progressReports != null) progressReports.setVisibility(View.GONE);
                        renderFilteredReports();
                    });
                } else {
                    runOnUiThread(() -> {
                        if (progressReports != null) progressReports.setVisibility(View.GONE);
                        renderFilteredReports();
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (progressReports != null) progressReports.setVisibility(View.GONE);
                    renderFilteredReports();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void renderFilteredReports() {
        if (reportsContainer == null) return;
        // Always keep the top XML tvEmptyReports hidden so it never pushes the filter bar to the bottom!
        if (tvEmptyReports != null) {
            tvEmptyReports.setVisibility(View.GONE);
        }
        reportsContainer.removeAllViews();

        // 1. Search Bar Row (Search by User, Email, Date, Time, or Issue + Calendar Picker)
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(roundedStrokeBg("#FFFFFF", "#E2D9D0", 12));
        searchRow.setPadding(dp(12), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams searchRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchRowLp.bottomMargin = dp(10);
        searchRow.setLayoutParams(searchRowLp);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Search user, date (YYYY-MM-DD), time (HH:MM)...");
        etSearch.setHintTextColor(Color.parseColor("#9C8F86"));
        etSearch.setTextColor(Color.parseColor("#1F1614"));
        etSearch.setTextSize(13f);
        etSearch.setSingleLine(true);
        etSearch.setBackground(null);
        etSearch.setPadding(dp(4), dp(8), dp(8), dp(8));
        etSearch.setText(currentSearchQuery);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnClearSearch = new TextView(this);
        btnClearSearch.setText("✕");
        btnClearSearch.setTextSize(13f);
        btnClearSearch.setTypeface(null, Typeface.BOLD);
        btnClearSearch.setTextColor(Color.parseColor("#8C7A70"));
        btnClearSearch.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);

        TextView btnPickDate = new TextView(this);
        btnPickDate.setText(R.string.btn_pick_date);
        btnPickDate.setTextSize(11f);
        btnPickDate.setTypeface(null, Typeface.BOLD);
        btnPickDate.setTextColor(Color.parseColor("#B85032"));
        btnPickDate.setBackground(roundedBg("#FDE8E1", 8));
        btnPickDate.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnPickDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String pickedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                currentSearchQuery = pickedDate;
                etSearch.setText(pickedDate);
                etSearch.setSelection(pickedDate.length());
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnClearSearch.setOnClickListener(v -> {
            currentSearchQuery = "";
            etSearch.setText("");
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                populateTicketItemsOnly();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchRow.addView(etSearch);
        searchRow.addView(btnClearSearch);
        searchRow.addView(btnPickDate);
        reportsContainer.addView(searchRow);

        // 2. Separate Top Toolbar Row: Category Label on Left + Segmented List/Cards Switcher on Right
        LinearLayout topToolbar = new LinearLayout(this);
        topToolbar.setOrientation(LinearLayout.HORIZONTAL);
        topToolbar.setGravity(Gravity.CENTER_VERTICAL);
        topToolbar.setPadding(dp(2), dp(2), dp(2), dp(8));

        TextView tvFilterLabel = new TextView(this);
        tvFilterLabel.setText("FILTER BY CATEGORY");
        tvFilterLabel.setTextSize(11f);
        tvFilterLabel.setLetterSpacing(0.05f);
        tvFilterLabel.setTypeface(null, Typeface.BOLD);
        tvFilterLabel.setTextColor(Color.parseColor("#8C7A70"));
        topToolbar.addView(tvFilterLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout viewSwitcher = new LinearLayout(this);
        viewSwitcher.setOrientation(LinearLayout.HORIZONTAL);
        viewSwitcher.setPadding(dp(3), dp(3), dp(3), dp(3));
        viewSwitcher.setBackground(roundedBg("#EBE5DF", 10));

        TextView btnListMode = new TextView(this);
        btnListMode.setText("☰ List");
        btnListMode.setTextSize(11f);
        btnListMode.setTypeface(null, Typeface.BOLD);
        btnListMode.setPadding(dp(12), dp(5), dp(12), dp(5));
        if (isCompactListView) {
            btnListMode.setTextColor(Color.WHITE);
            btnListMode.setBackground(roundedBg("#B85032", 8));
        } else {
            btnListMode.setTextColor(Color.parseColor("#5C534E"));
        }
        btnListMode.setOnClickListener(v -> {
            if (!isCompactListView) {
                isCompactListView = true;
                renderFilteredReports();
            }
        });

        TextView btnCardMode = new TextView(this);
        btnCardMode.setText("⊞ Cards");
        btnCardMode.setTextSize(11f);
        btnCardMode.setTypeface(null, Typeface.BOLD);
        btnCardMode.setPadding(dp(12), dp(5), dp(12), dp(5));
        if (!isCompactListView) {
            btnCardMode.setTextColor(Color.WHITE);
            btnCardMode.setBackground(roundedBg("#B85032", 8));
        } else {
            btnCardMode.setTextColor(Color.parseColor("#5C534E"));
        }
        btnCardMode.setOnClickListener(v -> {
            if (isCompactListView) {
                isCompactListView = false;
                renderFilteredReports();
            }
        });

        viewSwitcher.addView(btnListMode);
        viewSwitcher.addView(btnCardMode);
        topToolbar.addView(viewSwitcher);
        reportsContainer.addView(topToolbar);

        // 3. Dedicated Full-Width Horizontal Filter Chips Row (Never blocked by the List/Cards button!)
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hsvLp.bottomMargin = dp(12);
        hsv.setLayoutParams(hsvLp);

        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setPadding(dp(2), dp(2), dp(2), dp(2));

        String[] filters = {"ALL", "BUGS & ERRORS", "DELETIONS", "OTHER"};
        for (String f : filters) {
            TextView pill = new TextView(this);
            pill.setText(f);
            pill.setTextSize(12f);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = dp(8);
            pill.setLayoutParams(lp);

            if (f.equals(activeIssueFilter)) {
                pill.setTextColor(Color.WHITE);
                pill.setBackground(roundedBg("#B85032", 18));
            } else {
                pill.setTextColor(Color.parseColor("#6E625C"));
                pill.setBackground(roundedStrokeBg("#FFFFFF", "#E2D9D0", 18));
            }

            pill.setOnClickListener(v -> {
                activeIssueFilter = f;
                renderFilteredReports();
            });
            filterBar.addView(pill);
        }
        hsv.addView(filterBar);
        reportsContainer.addView(hsv);

        // 4. Child Container for Ticket Items (and Empty State below the filters)
        ticketsListContainer = new LinearLayout(this);
        ticketsListContainer.setOrientation(LinearLayout.VERTICAL);
        ticketsListContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        reportsContainer.addView(ticketsListContainer);

        populateTicketItemsOnly();
    }

    private void populateTicketItemsOnly() {
        if (ticketsListContainer == null) return;
        ticketsListContainer.removeAllViews();

        String q = currentSearchQuery != null ? currentSearchQuery.toLowerCase(Locale.ROOT).trim() : "";
        int count = 0;

        for (int i = 0; i < cachedReportsArray.length(); i++) {
            try {
                JSONObject obj = cachedReportsArray.getJSONObject(i);
                String issueType = obj.optString("issue_type", "").toUpperCase(Locale.ROOT);
                String reason = obj.optString("reason", "").toUpperCase(Locale.ROOT);

                boolean isBug = issueType.contains("BUG") || issueType.contains("ERROR") || issueType.contains("CRASH") || issueType.contains("TECHNICAL")
                        || reason.contains("BUG") || reason.contains("ERROR") || reason.contains("CRASH") || reason.contains("TECHNICAL");
                boolean isDel = issueType.contains("DELET") || reason.contains("DELET");

                boolean matchesCategory = false;
                if ("ALL".equals(activeIssueFilter)) matchesCategory = true;
                else if ("BUGS & ERRORS".equals(activeIssueFilter) && isBug) matchesCategory = true;
                else if ("DELETIONS".equals(activeIssueFilter) && isDel) matchesCategory = true;
                else if ("OTHER".equals(activeIssueFilter) && !isBug && !isDel) matchesCategory = true;

                if (!matchesCategory) continue;

                if (!q.isEmpty()) {
                    String rName = obj.optString("reporter_name", "").toLowerCase(Locale.ROOT);
                    String rEmail = obj.optString("reporter_email", "").toLowerCase(Locale.ROOT);
                    String rRole = obj.optString("reporter_role", "").toLowerCase(Locale.ROOT);
                    String rUid = obj.optString("reporter_uid", "").toLowerCase(Locale.ROOT);
                    String desc = obj.optString("description", "").toLowerCase(Locale.ROOT);
                    String dateStr = formatReportDateSearchable(obj.optString("created_at", ""));

                    boolean matchesSearch = rName.contains(q)
                            || rEmail.contains(q)
                            || rRole.contains(q)
                            || rUid.contains(q)
                            || desc.contains(q)
                            || issueType.toLowerCase(Locale.ROOT).contains(q)
                            || reason.toLowerCase(Locale.ROOT).contains(q)
                            || dateStr.contains(q);

                    if (!matchesSearch) continue;
                }

                if (isCompactListView) {
                    addReportCompactRow(obj, i);
                } else {
                    addReportCard(obj, i);
                }
                count++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (count == 0) {
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            emptyBox.setPadding(dp(24), dp(40), dp(24), dp(40));

            TextView tvEmptyTitle = new TextView(this);
            tvEmptyTitle.setText(getString(R.string.no_matching_tickets_in, activeIssueFilter));
            tvEmptyTitle.setTextSize(15f);
            tvEmptyTitle.setTypeface(null, Typeface.BOLD);
            tvEmptyTitle.setTextColor(Color.parseColor("#5C534E"));
            tvEmptyTitle.setGravity(Gravity.CENTER);
            emptyBox.addView(tvEmptyTitle);

            TextView tvEmptySub = new TextView(this);
            tvEmptySub.setText(q.isEmpty()
                    ? "Switch to another category tab above to view other reports."
                    : "No tickets matched \"" + currentSearchQuery + "\". Try clearing the search or date filter.");
            tvEmptySub.setTextSize(13f);
            tvEmptySub.setTextColor(Color.parseColor("#8C7A70"));
            tvEmptySub.setGravity(Gravity.CENTER);
            tvEmptySub.setPadding(0, dp(6), 0, 0);
            emptyBox.addView(tvEmptySub);

            ticketsListContainer.addView(emptyBox);
        }
    }

    private void addReportCompactRow(JSONObject obj, int position) {
        String reporterEmail = obj.optString("reporter_email", "").trim();
        if (reporterEmail.equalsIgnoreCase("null") || reporterEmail.equalsIgnoreCase("undefined")) reporterEmail = "";

        String reporterName = obj.optString("reporter_name", "").trim();
        if (reporterName.equalsIgnoreCase("null") || reporterName.isEmpty()) {
            reporterName = reporterEmail.isEmpty() ? "User" : reporterEmail;
        }
        String reporterRole = obj.optString("reporter_role", "Member").toUpperCase(Locale.ROOT);
        String reason = obj.optString("reason", "Support Ticket");
        String issueType = obj.optString("issue_type", "").trim();
        if (issueType.isEmpty() || issueType.equalsIgnoreCase("null")) issueType = reason;
        String description = obj.optString("description", "No description provided").replace("\n", " ");
        String dateTimeText = formatReportDateTime(obj.optString("created_at", ""));

        boolean isAccountDeletion = issueType.toUpperCase(Locale.ROOT).contains("DELET") || reason.toUpperCase(Locale.ROOT).contains("DELET");
        boolean isBug = issueType.toUpperCase(Locale.ROOT).contains("BUG") || issueType.toUpperCase(Locale.ROOT).contains("ERROR") || issueType.toUpperCase(Locale.ROOT).contains("TECHNICAL");

        CardView row = new CardView(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);
        row.setRadius(dp(12));
        row.setCardElevation(2f);
        row.setCardBackgroundColor(Color.WHITE);

        LinearLayout outerRow = new LinearLayout(this);
        outerRow.setOrientation(LinearLayout.HORIZONTAL);
        outerRow.setGravity(Gravity.CENTER_VERTICAL);

        View accentBar = new View(this);
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT);
        accentBar.setLayoutParams(accentLp);
        accentBar.setBackgroundColor(Color.parseColor(isAccountDeletion ? "#DC2626" : (isBug ? "#D97706" : "#B85032")));
        outerRow.addView(accentBar);

        LinearLayout contentRow = new LinearLayout(this);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);
        contentRow.setPadding(dp(12), dp(12), dp(12), dp(12));
        contentRow.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout topMetaRow = new LinearLayout(this);
        topMetaRow.setOrientation(LinearLayout.HORIZONTAL);
        topMetaRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setTextSize(9f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(7), dp(2), dp(7), dp(2));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.rightMargin = dp(8);
        badge.setLayoutParams(badgeLp);

        if (isAccountDeletion) {
            badge.setText("DELETED");
            badge.setTextColor(Color.parseColor("#B91C1C"));
            badge.setBackground(roundedBg("#FEE2E2", 6));
        } else if (isBug) {
            badge.setText("BUG");
            badge.setTextColor(Color.parseColor("#B45309"));
            badge.setBackground(roundedBg("#FEF3C7", 6));
        } else {
            badge.setText("TICKET");
            badge.setTextColor(Color.parseColor("#B85032"));
            badge.setBackground(roundedBg("#FDE8E1", 6));
        }
        topMetaRow.addView(badge);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(reporterName + " • " + reporterRole);
        tvTitle.setTextColor(Color.parseColor("#1F1614"));
        tvTitle.setTextSize(13f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topMetaRow.addView(tvTitle);

        textCol.addView(topMetaRow);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(description);
        tvDesc.setTextColor(Color.parseColor("#5C534E"));
        tvDesc.setTextSize(12f);
        tvDesc.setPadding(0, dp(4), 0, dp(2));
        tvDesc.setSingleLine(true);
        tvDesc.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(tvDesc);

        TextView tvDate = new TextView(this);
        tvDate.setText("🕒 " + dateTimeText);
        tvDate.setTextColor(Color.parseColor("#9C8F86"));
        tvDate.setTextSize(11f);
        textCol.addView(tvDate);

        contentRow.addView(textCol);

        TextView btnInspect = new TextView(this);
        btnInspect.setText("Details ›");
        btnInspect.setTextColor(Color.parseColor("#B85032"));
        btnInspect.setBackground(roundedBg("#F9F4F0", 8));
        btnInspect.setTextSize(11f);
        btnInspect.setTypeface(null, Typeface.BOLD);
        btnInspect.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams inspectLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inspectLp.leftMargin = dp(8);
        btnInspect.setLayoutParams(inspectLp);
        contentRow.addView(btnInspect);

        outerRow.addView(contentRow);
        row.addView(outerRow);
        row.setOnClickListener(v -> showTicketDetailsDialog(obj, row));
        ticketsListContainer.addView(row);
    }

    private void showTicketDetailsDialog(JSONObject obj, View rowView) {
        String ticketId = obj.optString("id", "");
        String reporterUid = obj.optString("reporter_uid", "");
        String reporterId = obj.optString("reporter_id", reporterUid);
        if (reporterId.equalsIgnoreCase("null")) reporterId = "";
        String shortId = reporterId.length() > 8 ? reporterId.substring(0, 8) + "..." : (reporterId.isEmpty() ? "N/A" : reporterId);

        String reporterEmail = obj.optString("reporter_email", "").trim();
        if (reporterEmail.equalsIgnoreCase("null") || reporterEmail.equalsIgnoreCase("undefined") || reporterEmail.isEmpty()) {
            reporterEmail = "Not provided";
        }
        String reporterName = obj.optString("reporter_name", "User").trim();
        if (reporterName.equalsIgnoreCase("null") || reporterName.isEmpty()) {
            reporterName = reporterEmail.contains("@") ? reporterEmail : "User";
        }
        String reporterRole = obj.optString("reporter_role", "Member").toUpperCase(Locale.ROOT);
        String reason = obj.optString("reason", "Support Ticket");
        String issueType = obj.optString("issue_type", "").trim();
        if (issueType.isEmpty() || issueType.equalsIgnoreCase("null")) issueType = reason;
        String rawDescription = obj.optString("description", "No description provided");
        String status = obj.optString("status", "OPEN").toUpperCase(Locale.ROOT);
        String dateTimeText = formatReportDateTime(obj.optString("created_at", ""));

        String mainDesc = rawDescription;
        String diagInfo = "";
        int diagIdx = rawDescription.indexOf("[Diagnostics:");
        if (diagIdx != -1) {
            mainDesc = rawDescription.substring(0, diagIdx).trim();
            diagInfo = rawDescription.substring(diagIdx).replace("[Diagnostics:", "").replace("]", "").trim();
        }

        boolean isAccountDeletion = issueType.toUpperCase(Locale.ROOT).contains("DELET") || reason.toUpperCase(Locale.ROOT).contains("DELET");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        CardView modalCard = new CardView(this);
        modalCard.setRadius(dp(18));
        modalCard.setCardBackgroundColor(Color.parseColor("#FAF7F4"));
        modalCard.setCardElevation(dp(8));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        // Top Header with Badge & Close Button
        LinearLayout topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.HORIZONTAL);
        topHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText(isAccountDeletion ? "ACCOUNT DELETED" : issueType.toUpperCase(Locale.ROOT));
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setTextColor(Color.parseColor(isAccountDeletion ? "#B91C1C" : "#B85032"));
        badge.setBackground(roundedBg(isAccountDeletion ? "#FEE2E2" : "#FDE8E1", 8));
        topHeader.addView(badge);

        View spacer = new View(this);
        topHeader.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextSize(16f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setTextColor(Color.parseColor("#6E625C"));
        btnClose.setPadding(dp(8), dp(2), dp(8), dp(2));
        topHeader.addView(btnClose);
        content.addView(topHeader);

        // Metadata Card
        CardView metaCard = new CardView(this);
        metaCard.setRadius(dp(12));
        metaCard.setCardElevation(0f);
        metaCard.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(14);
        metaCard.setLayoutParams(metaLp);

        LinearLayout metaLayout = new LinearLayout(this);
        metaLayout.setOrientation(LinearLayout.VERTICAL);
        metaLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
        metaLayout.addView(buildMetaRow("Reporter", reporterName + " (" + reporterRole + ")"));
        metaLayout.addView(buildMetaRow("Email", reporterEmail));
        metaLayout.addView(buildMetaRow("User ID", shortId));
        metaLayout.addView(buildMetaRow("Date & Time", dateTimeText));
        metaLayout.addView(buildMetaRow("Status", status));
        if (!diagInfo.isEmpty()) {
            metaLayout.addView(buildMetaRow("Device", diagInfo));
        }
        metaCard.addView(metaLayout);
        content.addView(metaCard);

        // Description Box
        TextView tvDescTitle = new TextView(this);
        tvDescTitle.setText(isAccountDeletion ? "Deletion Reason / Audit Note" : "Issue Description");
        tvDescTitle.setTextColor(Color.parseColor("#6E625C"));
        tvDescTitle.setTextSize(12f);
        tvDescTitle.setTypeface(null, Typeface.BOLD);
        tvDescTitle.setPadding(0, dp(16), 0, dp(6));
        content.addView(tvDescTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(mainDesc);
        tvDesc.setTextColor(Color.parseColor("#1F1614"));
        tvDesc.setTextSize(14f);
        tvDesc.setLineSpacing(0, 1.25f);
        tvDesc.setPadding(dp(14), dp(12), dp(14), dp(12));
        tvDesc.setBackground(roundedBg("#FFFFFF", 10));
        content.addView(tvDesc);

        // Action Buttons
        LinearLayout actionCol = new LinearLayout(this);
        actionCol.setOrientation(LinearLayout.VERTICAL);
        actionCol.setPadding(0, dp(20), 0, 0);

        scroll.addView(content);
        modalCard.addView(scroll);
        builder.setView(modalCard);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());

        if (isAccountDeletion) {
            Button btnAck = new Button(this);
            btnAck.setText("✓ Acknowledge & Archive Log");
            btnAck.setAllCaps(false);
            btnAck.setTextSize(14f);
            btnAck.setTypeface(null, Typeface.BOLD);
            btnAck.setTextColor(Color.WHITE);
            btnAck.setBackground(roundedBg("#1F1614", 10));
            btnAck.setOnClickListener(v -> {
                dialog.dismiss();
                updateTicketStatus(ticketId, "", reason, "RESOLVED", "Account deletion log acknowledged by Admin", rowView);
            });
            actionCol.addView(btnAck);
        } else {
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            Button btnResolve = new Button(this);
            btnResolve.setText("Resolve");
            btnResolve.setAllCaps(false);
            btnResolve.setTextColor(Color.WHITE);
            btnResolve.setTypeface(null, Typeface.BOLD);
            btnResolve.setBackground(roundedBg("#B85032", 10));
            LinearLayout.LayoutParams resLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            resLp.rightMargin = dp(8);
            btnResolve.setLayoutParams(resLp);
            btnResolve.setOnClickListener(v -> {
                dialog.dismiss();
                showResponseDialog(ticketId, reporterUid, reason, "RESOLVED", "Resolution Message", rowView);
            });

            Button btnDismiss = new Button(this);
            btnDismiss.setText("Dismiss");
            btnDismiss.setAllCaps(false);
            btnDismiss.setTextColor(Color.parseColor("#495057"));
            btnDismiss.setTypeface(null, Typeface.BOLD);
            android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
            dBg.setColor(Color.WHITE);
            dBg.setCornerRadius(dp(10));
            dBg.setStroke(dp(1), Color.parseColor("#D6CBC2"));
            btnDismiss.setBackground(dBg);
            LinearLayout.LayoutParams disLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            btnDismiss.setLayoutParams(disLp);
            btnDismiss.setOnClickListener(v -> {
                dialog.dismiss();
                showResponseDialog(ticketId, reporterUid, reason, "DISMISSED", "Reason for Dismissal", rowView);
            });

            btnRow.addView(btnResolve);
            btnRow.addView(btnDismiss);
            actionCol.addView(btnRow);
        }

        content.addView(actionCol);
        dialog.show();
    }

    private LinearLayout buildMetaRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#8C7A70"));
        tvLabel.setTextSize(12f);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(dp(85), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(Color.parseColor("#1F1614"));
        tvVal.setTextSize(12f);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);
        row.addView(tvVal);
        return row;
    }

    private void addReportCard(JSONObject obj, int position) {
        String ticketId = obj.optString("id", "");
        String reporterUid = obj.optString("reporter_uid", "");
        String reporterEmail = obj.optString("reporter_email", "").trim();
        if (reporterEmail.equalsIgnoreCase("null") || reporterEmail.equalsIgnoreCase("undefined")) reporterEmail = "";

        String reporterName = obj.optString("reporter_name", "").trim();
        if (reporterName.equalsIgnoreCase("null") || reporterName.isEmpty()) {
            reporterName = reporterEmail.isEmpty() ? "User" : reporterEmail;
        }
        String reporterRole = obj.optString("reporter_role", "Member").toUpperCase(Locale.ROOT);
        String reason = obj.optString("reason", "Support Ticket");
        String issueType = obj.optString("issue_type", "").trim();
        if (issueType.isEmpty() || issueType.equalsIgnoreCase("null")) issueType = reason;

        String description = obj.optString("description", "No description provided");
        String status = obj.optString("status", "OPEN").toUpperCase(Locale.ROOT);
        String dateTimeText = formatReportDateTime(obj.optString("created_at", ""));

        String reporterId = obj.optString("reporter_id", reporterUid);
        if (reporterId.equalsIgnoreCase("null")) reporterId = "";
        String shortId = reporterId.length() > 8 ? reporterId.substring(0, 8) : reporterId;

        boolean isAccountDeletion = issueType.toUpperCase(Locale.ROOT).contains("DELET") || reason.toUpperCase(Locale.ROOT).contains("DELET");

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(14));
        card.setCardElevation(3f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvReason = new TextView(this);
        tvReason.setText(isAccountDeletion ? "ACCOUNT DELETED" : issueType.toUpperCase(Locale.ROOT));
        tvReason.setTextSize(11f);
        tvReason.setTypeface(null, Typeface.BOLD);
        tvReason.setPadding(dp(10), dp(4), dp(10), dp(4));
        if (isAccountDeletion) {
            tvReason.setTextColor(Color.parseColor("#B91C1C"));
            tvReason.setBackground(roundedBg("#FEE2E2", 6));
        } else {
            tvReason.setTextColor(Color.parseColor("#B85032"));
            tvReason.setBackground(roundedBg("#FDE8E1", 6));
        }
        badgeRow.addView(tvReason);

        View spacer = new View(this);
        badgeRow.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        TextView tvStatus = new TextView(this);
        tvStatus.setText("🕒 " + dateTimeText + "  •  ● " + status);
        tvStatus.setTextSize(11f);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setTextColor(Color.parseColor("#2DCE89"));
        badgeRow.addView(tvStatus);
        layout.addView(badgeRow);

        TextView tvReporter = new TextView(this);
        String metaLine = "By: " + reporterName + " (" + reporterRole + ")";
        if (!shortId.isEmpty()) metaLine += " • ID: " + shortId;
        tvReporter.setText(metaLine);
        tvReporter.setTextSize(12f);
        tvReporter.setPadding(0, dp(8), 0, 0);
        tvReporter.setTextColor(Color.parseColor("#8C7A70"));
        layout.addView(tvReporter);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(description);
        tvDesc.setTextSize(14f);
        tvDesc.setTextColor(Color.parseColor("#1F1614"));
        tvDesc.setPadding(0, dp(10), 0, dp(14));
        layout.addView(tvDesc);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        if (isAccountDeletion) {
            Button btnAck = new Button(this);
            btnAck.setText("✓ Acknowledge & Archive Log");
            btnAck.setAllCaps(false);
            btnAck.setTextSize(13f);
            btnAck.setTypeface(null, Typeface.BOLD);
            btnAck.setTextColor(Color.WHITE);
            btnAck.setBackground(roundedBg("#1F1614", 10));
            btnAck.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
            btnAck.setOnClickListener(v -> updateTicketStatus(ticketId, "", reason, "RESOLVED", "Account deletion log acknowledged by Admin", card));
            actionRow.addView(btnAck);
        } else {
            Button btnResolve = new Button(this);
            btnResolve.setText("Resolve");
            btnResolve.setAllCaps(false);
            btnResolve.setTextSize(13f);
            btnResolve.setTypeface(null, Typeface.BOLD);
            btnResolve.setTextColor(Color.WHITE);
            btnResolve.setBackground(roundedBg("#B85032", 10));
            LinearLayout.LayoutParams resLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            resLp.rightMargin = dp(8);
            btnResolve.setLayoutParams(resLp);
            btnResolve.setOnClickListener(v -> showResponseDialog(ticketId, reporterUid, reason, "RESOLVED", "Resolution Message", card));
            actionRow.addView(btnResolve);

            Button btnDismiss = new Button(this);
            btnDismiss.setText("Dismiss");
            btnDismiss.setAllCaps(false);
            btnDismiss.setTextSize(13f);
            btnDismiss.setTypeface(null, Typeface.BOLD);
            btnDismiss.setTextColor(Color.parseColor("#495057"));
            android.graphics.drawable.GradientDrawable disBg = new android.graphics.drawable.GradientDrawable();
            disBg.setColor(Color.parseColor("#F5F1EC"));
            disBg.setCornerRadius(dp(10));
            disBg.setStroke(dp(1), Color.parseColor("#DFD6CE"));
            btnDismiss.setBackground(disBg);
            LinearLayout.LayoutParams disLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            btnDismiss.setLayoutParams(disLp);
            btnDismiss.setOnClickListener(v -> showResponseDialog(ticketId, reporterUid, reason, "DISMISSED", "Reason for Dismissal", card));
            actionRow.addView(btnDismiss);
        }

        layout.addView(actionRow);
        card.addView(layout);
        ticketsListContainer.addView(card);
    }

    private void refreshTabCounts() {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || token.isEmpty()) return;

        executor.execute(() -> {
            try {
                HttpURLConnection connRpt = SupabaseHelper.createConnection(
                        "/rest/v1/reports?select=id,status&status=in.(OPEN,open,PENDING,pending)",
                        "GET", token);
                int rptCount = 0;
                if (connRpt.getResponseCode() >= 200 && connRpt.getResponseCode() < 300) {
                    rptCount = new JSONArray(SupabaseHelper.readResponse(connRpt)).length();
                }
                connRpt.disconnect();

                HttpURLConnection connChats = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?select=guest_id",
                        "GET", token);
                connChats.setRequestProperty("x-admin-panel", "true");
                HashSet<String> uniqueGuests = new HashSet<>();
                if (connChats.getResponseCode() >= 200 && connChats.getResponseCode() < 300) {
                    JSONArray arr = new JSONArray(SupabaseHelper.readResponse(connChats));
                    for (int i = 0; i < arr.length(); i++) {
                        String gId = arr.getJSONObject(i).optString("guest_id", "");
                        if (!gId.isEmpty()) uniqueGuests.add(gId);
                    }
                }
                connChats.disconnect();

                final int fRptCount = rptCount;
                final int fGuestCount = uniqueGuests.size();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (tabIssueTickets != null) tabIssueTickets.setText("Issue Tickets (" + fRptCount + ")");
                    if (tabGuestChats != null) tabGuestChats.setText("Guest Chats (" + fGuestCount + ")");
                });
            } catch (Exception ignored) {}
        });
    }

    private void showResponseDialog(String ticketId, String reporterUid, String ticketReason, String newStatus, String prompt, View cardView) {
        EditText input = new EditText(this);
        input.setHint("Enter response to the user");
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle(prompt)
                .setView(input)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String responseText = input.getText().toString().trim();
                    updateTicketStatus(ticketId, reporterUid, ticketReason, newStatus, responseText, cardView);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTicketStatus(String ticketId, String reporterUid, String ticketReason, String newStatus, String adminResponse, View cardView) {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || ticketId.isEmpty()) return;

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject patchBody = new JSONObject();
                patchBody.put("status", newStatus);
                patchBody.put("admin_response", adminResponse);

                conn = SupabaseHelper.createConnection(
                        "/rest/v1/reports?id=eq." + ticketId,
                        "PATCH",
                        token
                );
                conn.setRequestProperty("Prefer", "return=minimal");
                SupabaseHelper.writeJson(conn, patchBody);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    if (!reporterUid.isEmpty()) {
                        SupabaseHelper.createNotification(
                                reporterUid,
                                "Support Ticket Updated",
                                "An admin has responded to your ticket regarding: " + ticketReason + ".",
                                token
                        );
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this, getString(R.string.msg_ticket_marked_as_prefix) + newStatus, Toast.LENGTH_SHORT).show();
                        for (int i = 0; i < cachedReportsArray.length(); i++) {
                            try {
                                if (ticketId.equals(cachedReportsArray.getJSONObject(i).optString("id"))) {
                                    cachedReportsArray.remove(i);
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                        populateTicketItemsOnly();
                        refreshTabCounts();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, getString(R.string.error_failed_update_ticket_http) + code + ")", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.password_connection_error, Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void loadGuestSupportChats() {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || token.isEmpty()) return;

        progressReports.setVisibility(View.VISIBLE);
        tvEmptyReports.setVisibility(View.GONE);
        reportsContainer.removeAllViews();

        executor.execute(() -> {
            try {
                HttpURLConnection conn = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?select=guest_id,guest_hint,sender_name,message_text,created_at&order=created_at.desc",
                        "GET", token);
                conn.setRequestProperty("x-admin-panel", "true");

                int code = conn.getResponseCode();
                String res = SupabaseHelper.readResponse(conn);
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONArray(res);
                    HashSet<String> seenGuests = new HashSet<>();
                    final JSONArray uniqueChats = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject msg = arr.getJSONObject(i);
                        String gId = msg.optString("guest_id", "");
                        if (!gId.isEmpty() && !seenGuests.contains(gId)) {
                            seenGuests.add(gId);
                            uniqueChats.put(msg);
                        }
                    }

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progressReports.setVisibility(View.GONE);
                        tabGuestChats.setText("Guest Chats (" + uniqueChats.length() + ")");

                        if (uniqueChats.length() == 0) {
                            tvEmptyReports.setText("No active guest support chats found.");
                            tvEmptyReports.setVisibility(View.VISIBLE);
                        } else {
                            for (int i = 0; i < uniqueChats.length(); i++) {
                                JSONObject msg = uniqueChats.optJSONObject(i);
                                if (msg != null) addGuestChatCard(msg);
                            }
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        progressReports.setVisibility(View.GONE);
                        Toast.makeText(this, "Failed to load chats.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressReports.setVisibility(View.GONE);
                    Toast.makeText(this, "Error fetching chats.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addGuestChatCard(JSONObject msg) {
        String gId = msg.optString("guest_id", "");
        String gHint = msg.optString("guest_hint", "");
        if (gHint.isEmpty() || gHint.startsWith("guest_")) {
            gHint = msg.optString("sender_name", "");
        }
        if (gHint.isEmpty() || gHint.startsWith("guest_")) {
            gHint = "Guest (" + gId.substring(0, Math.min(12, gId.length())) + ")";
        }

        String latestText = msg.optString("message_text", "Sent a message");
        if (latestText.startsWith("[IMAGE_BASE64]:") || latestText.startsWith("[IMAGE")) {
            latestText = "📷 Sent a photo";
        } else if (latestText.startsWith("[PDF_FILE]:")) {
            String[] parts = latestText.substring("[PDF_FILE]:".length()).split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : "Document.pdf";
            latestText = "📄 Sent a PDF document (" + fileName + ")";
        }

        String senderName = msg.optString("sender_name", "");

        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.setCardElevation(4f);
        card.setRadius(dp(14));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView tvBadge = new TextView(this);
        tvBadge.setText("PRE-LOGIN GUEST SUPPORT");
        tvBadge.setTextColor(Color.parseColor("#B85032"));
        tvBadge.setTextSize(10f);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setBackground(roundedBg("#FDE8E1", 6));
        tvBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tvBadge.setLayoutParams(badgeParams);
        layout.addView(tvBadge);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(gHint);
        tvTitle.setTextColor(Color.parseColor("#212529"));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, 8, 0, 8);
        layout.addView(tvTitle);

        TextView tvBody = new TextView(this);
        tvBody.setText((senderName.equals(gHint) ? "" : senderName + ": ") + latestText);
        tvBody.setTextColor(Color.parseColor("#495057"));
        tvBody.setTextSize(14f);
        tvBody.setMaxLines(3);
        tvBody.setEllipsize(TextUtils.TruncateAt.END);
        layout.addView(tvBody);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(16), 0, 0);
        actionRow.setLayoutParams(rowParams);

        Button btnReply = new Button(this);
        LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(
                0,
                dp(42),
                1.2f
        );
        replyParams.setMargins(0, 0, dp(8), 0);
        btnReply.setLayoutParams(replyParams);
        btnReply.setText("Open Chat & Reply");
        btnReply.setAllCaps(false);
        btnReply.setTextSize(13f);
        btnReply.setBackground(roundedBg("#B85032", 10));
        btnReply.setTextColor(Color.WHITE);
        btnReply.setTypeface(null, Typeface.BOLD);

        final String finalGHint = gHint;
        final String finalLatestText = latestText;
        btnReply.setOnClickListener(v -> {
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("IS_ADMIN_SUPPORT", true);
            chatIntent.putExtra("GUEST_ID", gId);
            chatIntent.putExtra("SENDER_ID", "ADMIN_SUPPORT");
            chatIntent.putExtra("SENDER_NAME", "iKhaya Admin Support");
            chatIntent.putExtra("SENDER_ROLE", "ADMIN");
            chatIntent.putExtra("RECEIVER_ID", gId);
            chatIntent.putExtra("RECEIVER_NAME", finalGHint);
            startActivity(chatIntent);
        });

        Button btnResolveChat = new Button(this);
        LinearLayout.LayoutParams resolveParams = new LinearLayout.LayoutParams(
                0,
                dp(42),
                0.8f
        );
        btnResolveChat.setLayoutParams(resolveParams);
        btnResolveChat.setText("Resolve");
        btnResolveChat.setAllCaps(false);
        btnResolveChat.setTextSize(13f);
        btnResolveChat.setBackground(roundedBg("#15803D", 10));
        btnResolveChat.setTextColor(Color.WHITE);
        btnResolveChat.setTypeface(null, Typeface.BOLD);

        btnResolveChat.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Resolve Guest Support Chat")
                    .setMessage("Mark " + finalGHint + "'s support request as resolved? This will close the active guest thread.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Mark Resolved", (dialog, which) -> {
                        btnResolveChat.setEnabled(false);
                        resolveGuestSupportChat(gId, finalGHint, finalLatestText, card, btnResolveChat);
                    })
                    .show();
        });

        actionRow.addView(btnReply);
        actionRow.addView(btnResolveChat);
        layout.addView(actionRow);
        card.addView(layout);
        reportsContainer.addView(card);
    }

    private void resolveGuestSupportChat(String guestId, String guestHint, String lastMessage, View cardView, Button btnResolve) {
        String token = SupabaseHelper.getAccessToken(this);
        if (token == null || token.isEmpty() || guestId == null || guestId.isEmpty()) {
            if (btnResolve != null) btnResolve.setEnabled(true);
            return;
        }

        executor.execute(() -> {
            HttpURLConnection delConn = null;
            try {
                try {
                    HttpURLConnection archiveConn = SupabaseHelper.createConnection("/rest/v1/reports", "POST", token);
                    archiveConn.setRequestProperty("Prefer", "return=minimal");
                    JSONObject archiveBody = new JSONObject();
                    archiveBody.put("reporter_uid", guestId);
                    archiveBody.put("reporter_id", guestId);
                    archiveBody.put("reporter_name", guestHint);
                    archiveBody.put("reporter_role", "GUEST");
                    archiveBody.put("reason", "GUEST CHAT RESOLVED (" + guestHint + ")");
                    archiveBody.put("issue_type", "GUEST CHAT RESOLVED");
                    archiveBody.put("description", "Resolved guest support thread. Last message: " + lastMessage);
                    archiveBody.put("status", "RESOLVED");
                    SupabaseHelper.writeJson(archiveConn, archiveBody);
                    archiveConn.getResponseCode();
                    archiveConn.disconnect();
                } catch (Exception ignored) {}

                String encodedGuestId = java.net.URLEncoder.encode(guestId, "UTF-8");
                delConn = SupabaseHelper.createConnection(
                        "/rest/v1/guest_support_messages?guest_id=eq." + encodedGuestId,
                        "DELETE",
                        token
                );
                delConn.setRequestProperty("x-admin-panel", "true");
                delConn.setRequestProperty("x-guest-id", guestId);

                int code = delConn.getResponseCode();
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, "Guest support chat marked as resolved.", Toast.LENGTH_SHORT).show();
                        if (reportsContainer != null) {
                            reportsContainer.removeView(cardView);
                            if (reportsContainer.getChildCount() == 0 && tvEmptyReports != null) {
                                tvEmptyReports.setText("No active guest support chats found.");
                                tvEmptyReports.setVisibility(View.VISIBLE);
                            }
                        }
                        refreshTabCounts();
                    });
                } else {
                    runOnUiThread(() -> {
                        if (btnResolve != null) btnResolve.setEnabled(true);
                        Toast.makeText(this, "Could not mark chat as resolved. Please try again.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (btnResolve != null) btnResolve.setEnabled(true);
                    Toast.makeText(this, "Error resolving guest chat.", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (delConn != null) delConn.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
