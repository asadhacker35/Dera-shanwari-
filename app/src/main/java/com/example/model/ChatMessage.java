package com.example.model;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private String id;
    private String orderId;
    private String senderId;
    private String customerId;
    private String riderId;
    private String senderRole; // "CUSTOMER", "ADMIN", "RIDER"
    private String senderName;
    private String message;
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String id, String orderId, String senderId, String senderRole,
                       String senderName, String message, long timestamp) {
        this(id, orderId, senderId, "", "", senderRole, senderName, message, timestamp);
    }

    public ChatMessage(String id, String orderId, String senderId, String customerId, String riderId,
                       String senderRole, String senderName, String message, long timestamp) {
        this.id = id;
        this.orderId = orderId;
        this.senderId = senderId;
        this.customerId = customerId != null ? customerId : "";
        this.riderId = riderId != null ? riderId : "";
        this.senderRole = senderRole;
        this.senderName = senderName;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
