package com.derashanwari.user.model;

import java.io.Serializable;

public class RestaurantSettings implements Serializable {
    private String restaurantName;
    private String logoUrl;
    private String phone;
    private String whatsappNumber;
    private String address;
    private String openingHours;
    private String closingHours;
    private double deliveryCharges;
    private double minimumOrderAmount;
    private int pointsPerHundredRs;
    private String imagekitPublicKey;
    private String imagekitAuthEndpoint;

    public RestaurantSettings() {}

    public RestaurantSettings(String restaurantName, String logoUrl, String phone,
                              String whatsappNumber, String address, String openingHours,
                              String closingHours, double deliveryCharges, double minimumOrderAmount,
                              int pointsPerHundredRs) {
        this.restaurantName = restaurantName;
        this.logoUrl = logoUrl;
        this.phone = phone;
        this.whatsappNumber = whatsappNumber;
        this.address = address;
        this.openingHours = openingHours;
        this.closingHours = closingHours;
        this.deliveryCharges = deliveryCharges;
        this.minimumOrderAmount = minimumOrderAmount;
        this.pointsPerHundredRs = pointsPerHundredRs;
        this.imagekitPublicKey = "";
        this.imagekitAuthEndpoint = "";
    }

    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public String getClosingHours() { return closingHours; }
    public void setClosingHours(String closingHours) { this.closingHours = closingHours; }

    public double getDeliveryCharges() { return deliveryCharges; }
    public void setDeliveryCharges(double deliveryCharges) { this.deliveryCharges = deliveryCharges; }

    public double getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(double minimumOrderAmount) { this.minimumOrderAmount = minimumOrderAmount; }

    public int getPointsPerHundredRs() { return pointsPerHundredRs; }
    public void setPointsPerHundredRs(int pointsPerHundredRs) { this.pointsPerHundredRs = pointsPerHundredRs; }

    public String getImagekitPublicKey() { return imagekitPublicKey != null ? imagekitPublicKey : ""; }
    public void setImagekitPublicKey(String imagekitPublicKey) {
        this.imagekitPublicKey = imagekitPublicKey != null ? imagekitPublicKey : "";
    }

    public String getImagekitAuthEndpoint() { return imagekitAuthEndpoint != null ? imagekitAuthEndpoint : ""; }
    public void setImagekitAuthEndpoint(String imagekitAuthEndpoint) {
        this.imagekitAuthEndpoint = imagekitAuthEndpoint != null ? imagekitAuthEndpoint : "";
    }
}
