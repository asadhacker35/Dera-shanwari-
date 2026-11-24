package com.derashanwari.rider.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Order implements Serializable {
    private String orderId;
    private String customerId;
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private String notes;
    private List<OrderItem> items = new ArrayList<>();
    private double subtotal;
    private double deliveryFee;
    private double discountAmount;
    private double totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;
    private String riderId;
    private String riderName;
    private long createdAt;
    private long updatedAt;
    private int pointsEarned;
    private Map<String, Long> timestamps = new HashMap<>();
    private List<OrderEvent> timeline = new ArrayList<>();
    private int printCount;
    private long lastPrintedAt;
    private String lastPrintedBy;

    public Order() {}

    public long getTimestampForKey(String key) {
        if (timestamps == null || key == null) return 0L;
        Long val = timestamps.get(key);
        return val != null ? val : 0L;
    }

    public boolean hasTimestamp(String key) { return getTimestampForKey(key) > 0L; }

    public void putTimestampIfAbsent(String key, long utcMillis) {
        if (key == null || key.isEmpty() || utcMillis <= 0L) return;
        if (timestamps == null) timestamps = new HashMap<>();
        if (!timestamps.containsKey(key) || timestamps.get(key) == null || timestamps.get(key) <= 0L) {
            timestamps.put(key, utcMillis);
        }
    }

    public long getPlacedTimestamp() {
        long ts = getTimestampForKey(OrderEvent.TS_PLACED);
        return ts > 0L ? ts : createdAt;
    }

    public long getLatestStageTimestamp() {
        long latest = getPlacedTimestamp();
        if (timestamps != null) {
            for (Long v : timestamps.values()) if (v != null && v > latest) latest = v;
        }
        return latest > 0L ? latest : updatedAt;
    }

    public void addTimelineEventIfAbsent(OrderEvent event) {
        if (event == null || event.getEventType() == null) return;
        if (timeline == null) timeline = new ArrayList<>();
        for (OrderEvent existing : timeline) {
            if (event.getEventType().equals(existing.getEventType())) return;
        }
        timeline.add(event);
        String key = OrderEvent.timestampKeyForEvent(event.getEventType());
        if (!key.isEmpty()) putTimestampIfAbsent(key, event.getTimestamp());
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = (items != null) ? items : new ArrayList<>(); }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(double deliveryFee) { this.deliveryFee = deliveryFee; }
    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = Math.max(0.0, discountAmount); }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }
    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
    public Map<String, Long> getTimestamps() { if (timestamps == null) timestamps = new HashMap<>(); return timestamps; }
    public void setTimestamps(Map<String, Long> timestamps) { this.timestamps = timestamps != null ? timestamps : new HashMap<>(); }
    public List<OrderEvent> getTimeline() { if (timeline == null) timeline = new ArrayList<>(); return timeline; }
    public void setTimeline(List<OrderEvent> timeline) { this.timeline = timeline != null ? timeline : new ArrayList<>(); }
    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = printCount; }
    public long getLastPrintedAt() { return lastPrintedAt; }
    public void setLastPrintedAt(long lastPrintedAt) { this.lastPrintedAt = lastPrintedAt; }
    public String getLastPrintedBy() { return lastPrintedBy != null ? lastPrintedBy : ""; }
    public void setLastPrintedBy(String lastPrintedBy) { this.lastPrintedBy = lastPrintedBy != null ? lastPrintedBy : ""; }
}
