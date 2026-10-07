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
        public String status; // "Available", "Occupied"

        public Listing(String id, String title, String location, String price, String status) {
            this.id = id;
            this.title = title;
            this.location = location;
            this.price = price;
            this.status = status;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("title", title);
                obj.put("location", location);
                obj.put("price", price);
                obj.put("status", status);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static Listing fromJson(JSONObject obj) {
            return new Listing(
                    obj.optString("id", ""),
                    obj.optString("title", "Room Listing"),
                    obj.optString("location", "Mthatha"),
                    obj.optString("price", "R950/month"),
                    obj.optString("status", "Available")
            );
        }
    }

    public static List<Listing> getListings(Context context) {
        List<Listing> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LISTINGS, null);

        // Pre-populate with default Mthatha listing if empty
        if (raw == null || raw.equals("[]")) {
            list.add(new Listing("LIST-101", "Mthatha Student Accommodation", "Mthatha Central", "R950/month", "Available"));
            saveList(context, list);
            return list;
        }

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Listing.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addListing(Context context, Listing listing) {
        List<Listing> current = getListings(context);
        current.add(0, listing);
        saveList(context, current);
    }

    private static void saveList(Context context, List<Listing> list) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        JSONArray arr = new JSONArray();
        for (Listing item : list) {
            arr.put(item.toJson());
        }
        editor.putString(KEY_LISTINGS, arr.toString()).commit();
    }
}