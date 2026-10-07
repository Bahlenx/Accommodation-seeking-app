package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ApplicationManager {

    private static final String PREF_NAME = "ikhaya_applications_pref";
    private static final String KEY_APPS = "applications_json_list";

    public static class RentalApplication {
        public String id;
        public String propertyTitle;
        public String applicantName;
        public String phone;
        public String emergencyContact;
        public String occupation;
        public String moveInDate;
        public String leaseDuration;
        public String occupants;
        public String rentalPrice;
        public String note;
        public String status; // "PENDING", "APPROVED", "DECLINED"

        public RentalApplication(String id, String propertyTitle, String applicantName,
                                 String phone, String emergencyContact, String occupation,
                                 String moveInDate, String leaseDuration, String occupants,
                                 String rentalPrice, String note, String status) {
            this.id = id;
            this.propertyTitle = propertyTitle;
            this.applicantName = applicantName;
            this.phone = phone;
            this.emergencyContact = emergencyContact;
            this.occupation = occupation;
            this.moveInDate = moveInDate;
            this.leaseDuration = leaseDuration;
            this.occupants = occupants;
            this.rentalPrice = rentalPrice;
            this.note = note;
            this.status = status;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("propertyTitle", propertyTitle);
                obj.put("applicantName", applicantName);
                obj.put("phone", phone);
                obj.put("emergencyContact", emergencyContact);
                obj.put("occupation", occupation);
                obj.put("moveInDate", moveInDate);
                obj.put("leaseDuration", leaseDuration);
                obj.put("occupants", occupants);
                obj.put("rentalPrice", rentalPrice);
                obj.put("note", note);
                obj.put("status", status);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static RentalApplication fromJson(JSONObject obj) {
            return new RentalApplication(
                    obj.optString("id", ""),
                    obj.optString("propertyTitle", "Property"),
                    obj.optString("applicantName", "Tenant"),
                    obj.optString("phone", ""),
                    obj.optString("emergencyContact", ""),
                    obj.optString("occupation", ""),
                    obj.optString("moveInDate", ""),
                    obj.optString("leaseDuration", ""),
                    obj.optString("occupants", "1"),
                    obj.optString("rentalPrice", "R0"),
                    obj.optString("note", ""),
                    obj.optString("status", "PENDING")
            );
        }
    }

    public static List<RentalApplication> getApplications(Context context) {
        List<RentalApplication> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_APPS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(RentalApplication.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addApplication(Context context, RentalApplication app) {
        List<RentalApplication> current = getApplications(context);
        current.add(0, app);
        saveList(context, current);
    }

    public static void updateStatus(Context context, String id, String newStatus) {
        List<RentalApplication> current = getApplications(context);
        for (RentalApplication a : current) {
            if (a.id.equals(id)) {
                a.status = newStatus;
                break;
            }
        }
        saveList(context, current);
    }

    public static int getPendingCount(Context context) {
        int count = 0;
        for (RentalApplication a : getApplications(context)) {
            if ("PENDING".equalsIgnoreCase(a.status)) count++;
        }
        return count;
    }

    private static void saveList(Context context, List<RentalApplication> list) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        JSONArray arr = new JSONArray();
        for (RentalApplication app : list) {
            arr.put(app.toJson());
        }
        editor.putString(KEY_APPS, arr.toString()).commit();
    }
}