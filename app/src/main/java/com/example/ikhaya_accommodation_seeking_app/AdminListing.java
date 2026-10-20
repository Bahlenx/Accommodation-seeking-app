package com.example.ikhaya_accommodation_seeking_app;

public class AdminListing {
    private String id;
    private String title;
    private String details;
    private String landlordUid;
    private String landlordName;
    private String status;
    private String imageUrl;

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