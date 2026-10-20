
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ListingManager {

    private static final String PREF_NAME = "ikhaya_listings_pref";
    private static final String KEY_LISTINGS = "listings_json_list";

    public static class Listing {
        public String id;
        public String title;
        public String location;
        public String price;
        public String status;

        public Listing(String id, String title, String location,
                       String price, String status) {
            this.id = id;
            this.title = title;
            this.location = location;
            this.price = price;
            this.status = status;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("title", title);
                obj.put("location", location);
                obj.put("price", price);
                obj.put("status", status);
            } catch (Exception ignored) {
            }
            return obj;
        }

        public static Listing fromJson(JSONObject obj) {
            return new Listing(
                    obj.optString("id", ""),
                    obj.optString("title", "Room Listing"),
                    obj.optString("location", "Location not specified"),
                    obj.optString("price", "Price not specified"),
                    obj.optString("status", "Available")
            );
        }
    }

    public static List<Listing> getListings(Context context) {
        List<Listing> listings = new ArrayList<>();

        SharedPreferences prefs = context.getSharedPreferences(
                PREF_NAME, Context.MODE_PRIVATE);

        String raw = prefs.getString(KEY_LISTINGS, "[]");

        try {
            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    listings.add(Listing.fromJson(object));
                }
            }
        } catch (Exception ignored) {
        }

        return listings;
    }

    public static void addListing(Context context, Listing listing) {
        if (listing == null) return;

        List<Listing> current = getListings(context);
        current.add(0, listing);
        saveList(context, current);
    }

    public static void updateListing(Context context, Listing updatedListing) {
        if (updatedListing == null) return;

        List<Listing> current = getListings(context);

        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).id != null
                    && current.get(i).id.equals(updatedListing.id)) {
                current.set(i, updatedListing);
                saveList(context, current);
                return;
            }
        }

        current.add(0, updatedListing);
        saveList(context, current);
    }

    public static void deleteListing(Context context, String listingId) {
        List<Listing> current = getListings(context);

        for (int i = current.size() - 1; i >= 0; i--) {
            Listing item = current.get(i);
            if (item.id != null && item.id.equals(listingId)) {
                current.remove(i);
            }
        }

        saveList(context, current);
    }

    private static void saveList(Context context, List<Listing> listings) {
        JSONArray array = new JSONArray();

        for (Listing listing : listings) {
            array.put(listing.toJson());
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LISTINGS, array.toString())
                .apply();
    }
}
