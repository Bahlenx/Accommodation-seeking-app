package com.example.ikhaya_accommodation_seeking_app;

public class AdminListing {
    private String id;
    private String title;
    private String details;
    private String landlordName;
    private String status; // PENDING, APPROVED, REJECTED (Soft deletion)

    public AdminListing(String id, String title, String details, String landlordName, String status) {
        this.id = id;
        this.title = title;
        this.details = details;
        this.landlordName = landlordName;
        this.status = status;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public String getLandlordName() { return landlordName; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
}