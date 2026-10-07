package com.example.ikhaya_accommodation_seeking_app;

import java.util.ArrayList;
import java.util.List;

public class ApplicationManager {

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
    }

    private static final List<RentalApplication> applications = new ArrayList<>();

    public static List<RentalApplication> getApplications() {
        return applications;
    }

    public static void addApplication(RentalApplication app) {
        applications.add(0, app);
    }

    public static void updateStatus(String id, String newStatus) {
        for (RentalApplication app : applications) {
            if (app.id.equals(id)) {
                app.status = newStatus;
                break;
            }
        }
    }

    public static int getPendingCount() {
        int count = 0;
        for (RentalApplication a : applications) {
            if ("PENDING".equalsIgnoreCase(a.status)) count++;
        }
        return count;
    }
}