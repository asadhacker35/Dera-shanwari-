package com.derashanwari.admin.model;

import java.io.Serializable;

public class Rider implements Serializable {
    private String id;
    private String linkedUid;
    private String name;
    private String email;
    private String phone;
    private String vehicleInfo;
    private String profileImageUrl;
    private boolean active;
    private int totalDeliveries;
    private double totalEarnings;
    private long createdAt;

    public Rider() {}

    public Rider(String id, String name, String phone, String vehicleInfo, String profileImageUrl,
                 boolean active, int totalDeliveries, double totalEarnings) {
        this(id, id, name, "", phone, vehicleInfo, profileImageUrl, active, totalDeliveries, totalEarnings, System.currentTimeMillis());
    }

    public Rider(String id, String linkedUid, String name, String email, String phone, String vehicleInfo,
                 String profileImageUrl, boolean active, int totalDeliveries, double totalEarnings, long createdAt) {
        this.id = id;
        this.linkedUid = linkedUid != null ? linkedUid : id;
        this.name = name;
        this.email = email != null ? email : "";
        this.phone = phone != null ? phone : "";
        this.vehicleInfo = vehicleInfo != null ? vehicleInfo : "";
        this.profileImageUrl = profileImageUrl != null ? profileImageUrl : "";
        this.active = active;
        this.totalDeliveries = totalDeliveries;
        this.totalEarnings = totalEarnings;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLinkedUid() { return linkedUid; }
    public void setLinkedUid(String linkedUid) { this.linkedUid = linkedUid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVehicleInfo() { return vehicleInfo; }
    public void setVehicleInfo(String vehicleInfo) { this.vehicleInfo = vehicleInfo; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getTotalDeliveries() { return totalDeliveries; }
    public void setTotalDeliveries(int totalDeliveries) { this.totalDeliveries = totalDeliveries; }

    public double getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(double totalEarnings) { this.totalEarnings = totalEarnings; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
