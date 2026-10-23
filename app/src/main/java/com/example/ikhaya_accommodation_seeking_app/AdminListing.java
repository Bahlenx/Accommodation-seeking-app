package com.example.ikhaya_accommodation_seeking_app;

public class AdminListing {
    private final String id;
    private final String title;
    private final String details;
    private final String landlordUid;
    private final String landlordName;
    private String status;
    private final String imageUrl;

    public AdminListing(String id, String title, String details, String landlordUid, String landlordName, String status, String imageUrl) {
        this.id = id;
        this.title = title;
        this.details = details;
        this.landlordUid = landlordUid;
        this.landlordName = landlordName;
        this.status = status;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public String getLandlordUid() { return landlordUid; }
    public String getLandlordName() { return landlordName; }
    public String getStatus() { return status; }
    public String getImageUrl() { return imageUrl; }

    public void setStatus(String status) { this.status = status; }
}