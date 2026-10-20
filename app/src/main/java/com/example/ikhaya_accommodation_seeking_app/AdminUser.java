package com.example.ikhaya_accommodation_seeking_app;

public class AdminUser {
    private String userId;
    private String name;
    private String role;
    private boolean isSuspended;

    public AdminUser(String userId, String name, String role, boolean isSuspended) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.isSuspended = isSuspended;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public boolean isSuspended() { return isSuspended; }

    public void setSuspended(boolean suspended) { this.isSuspended = suspended; }
}