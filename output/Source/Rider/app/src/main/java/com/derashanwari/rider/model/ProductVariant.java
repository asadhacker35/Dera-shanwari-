package com.derashanwari.rider.model;

import java.io.Serializable;

public class ProductVariant implements Serializable {
    private String id;
    private String name;
    private double price;
    private double discountPrice;
    private boolean available = true;
    private String imageUrl;
    private String description;

    public ProductVariant() {}

    public ProductVariant(String id, String name, double price, double discountPrice,
                          boolean available, String imageUrl, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.discountPrice = discountPrice;
        this.available = available;
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.description = description != null ? description : "";
    }

    public double getEffectivePrice() {
        return (discountPrice > 0 && discountPrice < price) ? discountPrice : price;
    }

    public boolean hasDiscount() {
        return discountPrice > 0 && discountPrice < price;
    }

    public int getDiscountPercent() {
        if (!hasDiscount() || price <= 0) return 0;
        return (int) Math.round(((price - discountPrice) / price) * 100.0);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(double discountPrice) { this.discountPrice = discountPrice; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
