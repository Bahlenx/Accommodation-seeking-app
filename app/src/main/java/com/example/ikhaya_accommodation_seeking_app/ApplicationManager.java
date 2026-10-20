
package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApplicationManager {

    private static final String PREF_NAME = "ikhaya_applications_pref";
    private static final String KEY_APPS = "applications_json_list";
    private static final String KEY_SEEN_APPROVED =
            "seen_approved_application_ids";

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
        public String status;

        public RentalApplication(
                String id,
                String propertyTitle,
                String applicantName,
                String phone,
                String emergencyContact,
                String occupation,
                String moveInDate,
                String leaseDuration,
                String occupants,
                String rentalPrice,
                String note,
                String status) {

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
            JSONObject obj = new JSONObject();

            try {
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
            } catch (Exception ignored) {
            }

            return obj;
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

    /**
     * Returns all rental applications saved on this device.
     */
    public static List<RentalApplication> getApplications(Context context) {
        List<RentalApplication> applications = new ArrayList<>();

        SharedPreferences prefs = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );

        String raw = prefs.getString(KEY_APPS, "[]");

        try {
            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);

                if (obj != null) {
                    applications.add(RentalApplication.fromJson(obj));
                }
            }
        } catch (Exception ignored) {
        }

        return applications;
    }

    /**
     * Saves a new rental application at the beginning of the list.
     */
    public static void addApplication(
            Context context,
            RentalApplication application) {

        if (application == null) {
            return;
        }

        List<RentalApplication> applications =
                getApplications(context);

        applications.add(0, application);
        saveList(context, applications);
    }

    /**
     * Updates an application's status without removing its other details.
     * Example statuses: PENDING, APPROVED, DECLINED.
     */
    public static void updateStatus(
            Context context,
            String id,
            String newStatus) {

        if (id == null || newStatus == null) {
            return;
        }

        List<RentalApplication> applications =
                getApplications(context);

        for (RentalApplication application : applications) {
            if (application.id != null
                    && application.id.equals(id)) {

                application.status = newStatus.trim().toUpperCase();
                break;
            }
        }

        saveList(context, applications);
    }

    /**
     * Counts applications that are still waiting for the landlord's decision.
     */
    public static int getPendingCount(Context context) {
        int count = 0;

        for (RentalApplication application : getApplications(context)) {
            if ("PENDING".equalsIgnoreCase(application.status)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts approved applications that the resident has not yet viewed
     * on this device.
     */
    public static int getUnseenApprovedCount(Context context) {
        Set<String> seenIds = getSeenApprovedIds(context);
        int count = 0;

        for (RentalApplication application : getApplications(context)) {
            if ("APPROVED".equalsIgnoreCase(application.status)
                    && hasValidId(application.id)
                    && !seenIds.contains(application.id)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Marks all currently approved applications as seen.
     */
    public static void markApprovedApplicationsSeen(Context context) {
        Set<String> seenIds = getSeenApprovedIds(context);

        for (RentalApplication application : getApplications(context)) {
            if ("APPROVED".equalsIgnoreCase(application.status)
                    && hasValidId(application.id)) {
                seenIds.add(application.id);
            }
        }

        context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putStringSet(KEY_SEEN_APPROVED, seenIds)
                .apply();
    }

    /**
     * Clears the approved-application viewed state.
     * Use only if the app intentionally needs to reset these notifications.
     */
    public static void clearSeenApprovedApplications(Context context) {
        context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .edit()
                .remove(KEY_SEEN_APPROVED)
                .apply();
    }

    private static Set<String> getSeenApprovedIds(Context context) {
        Set<String> saved = context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .getStringSet(KEY_SEEN_APPROVED, null);

        if (saved == null) {
            return new HashSet<>();
        }

        // Copy the saved set before changing it.
        return new HashSet<>(saved);
    }

    private static boolean hasValidId(String id) {
        return id != null && !id.trim().isEmpty();
    }

    private static void saveList(
            Context context,
            List<RentalApplication> applications) {

        JSONArray array = new JSONArray();

        for (RentalApplication application : applications) {
            array.put(application.toJson());
        }

        context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putString(KEY_APPS, array.toString())
                .apply();
    }
}
