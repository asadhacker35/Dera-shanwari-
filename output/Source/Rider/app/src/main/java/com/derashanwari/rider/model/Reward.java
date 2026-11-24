package com.derashanwari.rider.model;

import java.io.Serializable;

public class Reward implements Serializable {
    private String id;
    private String customerId;
    private String title;
    private String description;
    private int points;
    private String type; // "EARNED" or "REDEEMABLE_OFFER" or "REDEEMED"
    private boolean redeemable;
    private int minPointsRequired;
    private String orderId;
    private long createdAt;

    public Reward() {}

    public Reward(String id, String customerId, String title, String description, int points,
                  String type, boolean redeemable, int minPointsRequired, String orderId, long createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.title = title;
        this.description = description;
        this.points = points;
        this.type = type;
        this.redeemable = redeemable;
        this.minPointsRequired = minPointsRequired;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRedeemable() { return redeemable; }
    public void setRedeemable(boolean redeemable) { this.redeemable = redeemable; }

    public int getMinPointsRequired() { return minPointsRequired; }
    public void setMinPointsRequired(int minPointsRequired) { this.minPointsRequired = minPointsRequired; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
