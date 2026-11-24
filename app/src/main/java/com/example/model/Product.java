package com.example.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Product implements Serializable {
    private String id;
    private String name;
    private String description;
    private double price;
    private double discountPrice;
    private String imageUrl;
    private String imageFileId;
    private String localDrawableName;
    private String categoryId;
    private String categoryName;
    private boolean available;
    private boolean featured;
    private boolean popular;
    private List<ProductVariant> variants = new ArrayList<>();
    private long createdAt;

    public Product() {}

    public Product(String id, String name, String description, double price, double discountPrice,
                   String imageUrl, String localDrawableName, String categoryId, String categoryName,
                   boolean available, boolean featured, boolean popular, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.discountPrice = discountPrice;
        this.imageUrl = imageUrl;
        this.imageFileId = "";
        this.localDrawableName = localDrawableName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.available = available;
        this.featured = featured;
        this.popular = popular;
        this.createdAt = createdAt;
    }

    public boolean hasVariants() {
        return variants != null && !variants.isEmpty();
    }

    public List<ProductVariant> getAvailableVariants() {
        List<ProductVariant> list = new ArrayList<>();
        if (variants != null) {
            for (ProductVariant v : variants) {
                if (v != null && v.isAvailable()) {
                    list.add(v);
                }
            }
        }
        return list;
    }

    public ProductVariant getDefaultVariant() {
        if (variants == null || variants.isEmpty()) return null;
        for (ProductVariant v : variants) {
            if (v != null && v.isAvailable()) return v;
        }
        return variants.get(0);
    }

    public ProductVariant findVariantById(String variantId) {
        if (variants == null || variantId == null || variantId.isEmpty()) return null;
        for (ProductVariant v : variants) {
            if (v != null && variantId.equals(v.getId())) return v;
        }
        return null;
    }

    public double getStartingPrice() {
        if (!hasVariants()) return getEffectivePrice();
        double min = Double.MAX_VALUE;
        for (ProductVariant v : variants) {
            if (v != null && v.isAvailable() && v.getEffectivePrice() > 0) {
                min = Math.min(min, v.getEffectivePrice());
            }
        }
        if (min == Double.MAX_VALUE) {
            for (ProductVariant v : variants) {
                if (v != null && v.getEffectivePrice() > 0) {
                    min = Math.min(min, v.getEffectivePrice());
                }
            }
        }
        return min == Double.MAX_VALUE ? getEffectivePrice() : min;
    }

    public double getEffectivePrice() {
        return (discountPrice > 0 && discountPrice < price) ? discountPrice : price;
    }

    public boolean hasDiscount() {
        if (hasVariants()) {
            for (ProductVariant v : variants) {
                if (v != null && v.hasDiscount()) return true;
            }
        }
        return discountPrice > 0 && discountPrice < price;
    }

    public int getDiscountPercent() {
        if (discountPrice > 0 && discountPrice < price && price > 0) {
            return (int) Math.round(((price - discountPrice) / price) * 100.0);
        }
        if (hasVariants()) {
            int best = 0;
            for (ProductVariant v : variants) {
                if (v != null && v.getDiscountPercent() > best) {
                    best = v.getDiscountPercent();
                }
            }
            return best;
        }
        return 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(double discountPrice) { this.discountPrice = discountPrice; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImageFileId() { return imageFileId != null ? imageFileId : ""; }
    public void setImageFileId(String imageFileId) { this.imageFileId = imageFileId != null ? imageFileId : ""; }

    public String getLocalDrawableName() { return localDrawableName; }
    public void setLocalDrawableName(String localDrawableName) { this.localDrawableName = localDrawableName; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public boolean isPopular() { return popular; }
    public void setPopular(boolean popular) { this.popular = popular; }

    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants != null ? variants : new ArrayList<>();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
