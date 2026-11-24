package com.derashanwari.rider.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class OrderEvent implements Serializable {
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String RIDER_ASSIGNED = "RIDER_ASSIGNED";
    public static final String RIDER_ACCEPTED = "RIDER_ACCEPTED";
    public static final String SENT_TO_KITCHEN = "SENT_TO_KITCHEN";
    public static final String PREPARATION_STARTED = "PREPARATION_STARTED";
    public static final String PREPARATION_COMPLETED = "PREPARATION_COMPLETED";
    public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    public static final String TS_PLACED = "orderPlacedAt";
    public static final String TS_CONFIRMED = "orderConfirmedAt";
    public static final String TS_RIDER_ASSIGNED = "riderAssignedAt";
    public static final String TS_RIDER_ACCEPTED = "riderAcceptedAt";
    public static final String TS_SENT_TO_KITCHEN = "sentToKitchenAt";
    public static final String TS_PREP_STARTED = "preparationStartedAt";
    public static final String TS_PREP_COMPLETED = "preparationCompletedAt";
    public static final String TS_OUT_FOR_DELIVERY = "outForDeliveryAt";
    public static final String TS_DELIVERED = "deliveredAt";
    public static final String TS_CANCELLED = "cancelledAt";

    private String id;
    private String orderId;
    private String eventType;
    private long timestamp;
    private String userId;
    private String userRole;
    private String userName;
    private String notes;

    public OrderEvent() {}

    public OrderEvent(String id, String orderId, String eventType, long timestamp,
                      String userId, String userRole, String userName, String notes) {
        this.id = id;
        this.orderId = orderId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.userId = userId;
        this.userRole = userRole;
        this.userName = userName;
        this.notes = notes != null ? notes : "";
    }

    public static List<String> orderedStages() {
        return Arrays.asList(
                ORDER_PLACED, ORDER_CONFIRMED, RIDER_ASSIGNED, RIDER_ACCEPTED,
                SENT_TO_KITCHEN, PREPARATION_STARTED, PREPARATION_COMPLETED, OUT_FOR_DELIVERY, DELIVERED
        );
    }

    public static String timestampKeyForEvent(String eventType) {
        if (eventType == null) return "";
        switch (eventType) {
            case ORDER_PLACED: return TS_PLACED;
            case ORDER_CONFIRMED: return TS_CONFIRMED;
            case RIDER_ASSIGNED: return TS_RIDER_ASSIGNED;
            case RIDER_ACCEPTED: return TS_RIDER_ACCEPTED;
            case SENT_TO_KITCHEN: return TS_SENT_TO_KITCHEN;
            case PREPARATION_STARTED: return TS_PREP_STARTED;
            case PREPARATION_COMPLETED: return TS_PREP_COMPLETED;
            case OUT_FOR_DELIVERY: return TS_OUT_FOR_DELIVERY;
            case DELIVERED: return TS_DELIVERED;
            case CANCELLED: return TS_CANCELLED;
            default: return "";
        }
    }

    public static String displayLabel(String eventType) {
        if (eventType == null) return "Event";
        switch (eventType) {
            case ORDER_PLACED: return "Order Placed";
            case ORDER_CONFIRMED: return "Order Confirmed";
            case RIDER_ASSIGNED: return "Rider Assigned";
            case RIDER_ACCEPTED: return "Rider Accepted";
            case SENT_TO_KITCHEN: return "Sent to Kitchen";
            case PREPARATION_STARTED: return "Kitchen Preparation Started";
            case PREPARATION_COMPLETED: return "Kitchen Preparation Completed";
            case OUT_FOR_DELIVERY: return "Out for Delivery";
            case DELIVERED: return "Delivered";
            case CANCELLED: return "Cancelled";
            default: return eventType;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes != null ? notes : ""; }
}
