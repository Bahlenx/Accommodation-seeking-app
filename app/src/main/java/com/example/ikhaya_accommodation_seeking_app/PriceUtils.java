package com.example.ikhaya_accommodation_seeking_app;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class PriceUtils {

    /**
     * Determines whether a listing is for sale based on its offer_type, property_type, and property_name.
     */
    public static boolean isSaleProperty(String offerType, String propertyType, String propertyName) {
        if (offerType != null && "sale".equalsIgnoreCase(offerType.trim())) {
            return true;
        }
        String type = propertyType != null ? propertyType.toLowerCase() : "";
        if (type.contains("land") || type.contains("plot") || type.contains("farm") || type.contains("sale") || type.contains("stand")) {
            return true;
        }
        String name = propertyName != null ? propertyName.toLowerCase() : "";
        if (name.contains("land for sale") || name.contains("plot for sale") || name.contains("farm for sale")) {
            return true;
        }
        return false;
    }

    public static boolean isSaleProperty(JSONObject listing) {
        if (listing == null) return false;
        String offerType = listing.optString("offer_type", "rent");
        String propertyType = listing.optString("property_type", "");
        String propertyName = listing.optString("property_name", listing.optString("title", ""));
        return isSaleProperty(offerType, propertyType, propertyName);
    }

    /**
     * Formats a raw numeric price string or value into a readable number string with spaces as thousand separators.
     * e.g., "500000.0" -> "500 000"
     *       "2500.0" -> "2 500"
     *       "500" -> "500"
     */
    public static String formatNumberWithSpaces(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(' ');
        if (amount % 1 == 0) {
            long longVal = (long) amount;
            DecimalFormat formatter = new DecimalFormat("#,###", symbols);
            return formatter.format(longVal);
        } else {
            DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
            return formatter.format(amount);
        }
    }

    public static String formatNumberWithSpaces(String rawPrice) {
        if (rawPrice == null || rawPrice.trim().isEmpty() || "null".equalsIgnoreCase(rawPrice.trim())) {
            return "0";
        }
        try {
            String clean = rawPrice.replaceAll("[^0-9.]", "");
            if (clean.isEmpty()) return rawPrice;
            double parsed = Double.parseDouble(clean);
            return formatNumberWithSpaces(parsed);
        } catch (Exception e) {
            return rawPrice;
        }
    }

    /**
     * Returns full formatted price string, adding " / mo" for rent and no suffix for sale.
     * e.g., "R 500 000" (for sale)
     *       "R 2 500 / mo" (for rent)
     */
    public static String formatDisplayPrice(JSONObject listing) {
        if (listing == null) return "R 0";
        String rawPrice = listing.optString("price", listing.optString("monthly_price", listing.optString("rental_price", "0")));
        boolean isSale = isSaleProperty(listing);
        return formatDisplayPrice(rawPrice, isSale);
    }

    public static String formatDisplayPrice(String rawPrice, boolean isSale) {
        String formattedNum = formatNumberWithSpaces(rawPrice);
        if (isSale) {
            return "R " + formattedNum;
        } else {
            return "R " + formattedNum + " / mo";
        }
    }
}
