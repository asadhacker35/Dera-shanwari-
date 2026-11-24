package com.derashanwari.rider.model;

import java.io.Serializable;

public class Customer implements Serializable {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private boolean active;
    private int loyaltyPoints;
    private long createdAt;

    public Customer() {}

    public Customer(String id, String name, String phone, String address,
                    boolean active, int loyaltyPoints, long createdAt) {
        this(id, name, "", phone, address, active, loyaltyPoints, createdAt);
    }

    public Customer(String id, String name, String email, String phone, String address,
                    boolean active, int loyaltyPoints, long createdAt) {
        this.id = id;
        this.name = name;
        this.email = email != null ? email : "";
        this.phone = phone != null ? phone : "";
        this.address = address != null ? address : "";
        this.active = active;
        this.loyaltyPoints = loyaltyPoints;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
