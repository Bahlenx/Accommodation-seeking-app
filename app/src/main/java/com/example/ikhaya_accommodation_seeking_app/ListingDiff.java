package com.example.ikhaya_accommodation_seeking_app;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ListingDiff {
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final boolean isHighRisk;

    public ListingDiff(String fieldName, String oldValue, String newValue, boolean isHighRisk) {
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.isHighRisk = isHighRisk;
    }

    public String getFieldName() { return fieldName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public boolean isHighRisk() { return isHighRisk; }

    public String getFormattedDiff() {
        String oldStr = (oldValue == null || oldValue.trim().isEmpty()) ? "[None]" : oldValue.trim();
        String newStr = (newValue == null || newValue.trim().isEmpty()) ? "[None]" : newValue.trim();
        return fieldName + ": " + oldStr + " -> " + newStr;
    }

    public static List<ListingDiff> computeDiffs(JSONObject original, JSONObject pending) {
        List<ListingDiff> diffs = new ArrayList<>();
        if (original == null || pending == null) return diffs;

        // Address / Location (High Risk)
        String oldAddr = original.optString("location", original.optString("address", ""));
        String newAddr = pending.optString("location", pending.optString("address", ""));
        if (!oldAddr.equalsIgnoreCase(newAddr) && !newAddr.isEmpty()) {
            diffs.add(new ListingDiff("Address / Location", oldAddr, newAddr, true));
        }

        // Authority Document Type (High Risk)
        String oldDocType = AddListingActivity.getDocTypeLabel(original.optString("authority_doc_type", ""));
        String newDocType = AddListingActivity.getDocTypeLabel(pending.optString("authority_doc_type", ""));
        if (!oldDocType.equalsIgnoreCase(newDocType) && !pending.optString("authority_doc_type", "").isEmpty()) {
            diffs.add(new ListingDiff("Authority Document Type", oldDocType, newDocType, true));
        }

        // Authority Document File (High Risk)
        String oldDocUrl = original.optString("authority_doc_url", "");
        String newDocUrl = pending.optString("authority_doc_url", "");
        if (!oldDocUrl.equals(newDocUrl) && !newDocUrl.isEmpty()) {
            diffs.add(new ListingDiff("Authority Document File", oldDocUrl.isEmpty() ? "[None]" : "Previous File", "New Uploaded File", true));
        }

        // Price (Low Risk)
        double oldPrice = original.optDouble("price", 0.0);
        double newPrice = pending.optDouble("price", 0.0);
        if (Double.compare(oldPrice, newPrice) != 0 && newPrice > 0) {
            diffs.add(new ListingDiff("Price", String.format(Locale.getDefault(), "R %.2f", oldPrice), String.format(Locale.getDefault(), "R %.2f", newPrice), false));
        }

        // Description (Low Risk)
        String oldDesc = original.optString("description", "");
        String newDesc = pending.optString("description", "");
        if (!oldDesc.equalsIgnoreCase(newDesc) && !newDesc.isEmpty()) {
            diffs.add(new ListingDiff("Description", oldDesc, newDesc, false));
        }

        // Property Name / Title (Low Risk)
        String oldTitle = original.optString("property_name", original.optString("title", ""));
        String newTitle = pending.optString("property_name", pending.optString("title", ""));
        if (!oldTitle.equalsIgnoreCase(newTitle) && !newTitle.isEmpty()) {
            diffs.add(new ListingDiff("Title", oldTitle, newTitle, false));
        }

        return diffs;
    }
}
