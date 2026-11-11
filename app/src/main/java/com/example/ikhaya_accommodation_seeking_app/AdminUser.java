package com.example.ikhaya_accommodation_seeking_app;

public class AdminUser {
    private String profileId;
    private String userId;
    private String name;
    private String role;
    private boolean isSuspended;
    private String identityStatus;
    private String saIdNumber;
    private String idDocumentUrl;
    private String verificationSelfieUrl;

    public AdminUser(String profileId, String userId, String name, String role, boolean isSuspended,
                     String identityStatus, String saIdNumber, String idDocumentUrl, String verificationSelfieUrl) {
        this.profileId = profileId;
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.isSuspended = isSuspended;
        this.identityStatus = identityStatus != null ? identityStatus : "UNVERIFIED";
        this.saIdNumber = saIdNumber != null ? saIdNumber : "";
        this.idDocumentUrl = idDocumentUrl != null ? idDocumentUrl : "";
        this.verificationSelfieUrl = verificationSelfieUrl != null ? verificationSelfieUrl : "";
    }

    public String getProfileId() { return profileId; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public boolean isSuspended() { return isSuspended; }
    public String getIdentityStatus() { return identityStatus; }
    public String getSaIdNumber() { return saIdNumber; }
    public String getIdDocumentUrl() { return idDocumentUrl; }
    public String getVerificationSelfieUrl() { return verificationSelfieUrl; }

    public void setSuspended(boolean suspended) { this.isSuspended = suspended; }
    public void setIdentityStatus(String identityStatus) { this.identityStatus = identityStatus; }
}
