package com.derashanwari.rider.model;

import java.io.Serializable;

public class OrderItem implements Serializable {
    private String productId;
    private String productName;
    private String variantId;
    private String variantName;
    private double unitPrice;
    private int quantity;
    private String imageUrl;
    private String localDrawableName;

    public OrderItem() {}

    public OrderItem(String productId, String productName, double unitPrice, int quantity,
                     String imageUrl, String localDrawableName) {
        this(productId, productName, "", "", unitPrice, quantity, imageUrl, localDrawableName);
    }

    public OrderItem(String productId, String productName, String variantId, String variantName,
                     double unitPrice, int quantity, String imageUrl, String localDrawableName) {
        this.productId = productId;
        this.productName = productName;
        this.variantId = variantId != null ? variantId : "";
        this.variantName = variantName != null ? variantName : "";
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.localDrawableName = localDrawableName;
    }

    public double getLineTotal() { return unitPrice * quantity; }
    public String getDisplayName() {
        if (variantName != null && !variantName.trim().isEmpty()) {
            return productName + " (" + variantName.trim() + ")";
        }
        return productName != null ? productName : "";
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getVariantId() { return variantId != null ? variantId : ""; }
    public void setVariantId(String variantId) { this.variantId = variantId != null ? variantId : ""; }
    public String getVariantName() { return variantName != null ? variantName : ""; }
    public void setVariantName(String variantName) { this.variantName = variantName != null ? variantName : ""; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getLocalDrawableName() { return localDrawableName; }
    public void setLocalDrawableName(String localDrawableName) { this.localDrawableName = localDrawableName; }
}
