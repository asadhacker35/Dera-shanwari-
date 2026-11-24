package com.derashanwari.rider.model;

import java.io.Serializable;

public class NotificationItem implements Serializable {
    private String id;
    private String targetRole; // "ADMIN", "CUSTOMER", "RIDER"
    private String targetUid;
    private String senderId;
    private String orderId;
    private String title;
    private String body;
    private String type;
    private boolean read;
    private long createdAt;

    public NotificationItem() {}

    public NotificationItem(String id, String targetRole, String targetUid, String senderId,
                            String orderId, String title, String body, String type,
                            boolean read, long createdAt) {
        this.id = id;
        this.targetRole = targetRole;
        this.targetUid = targetUid != null ? targetUid : "";
        this.senderId = senderId;
        this.orderId = orderId != null ? orderId : "";
        this.title = title;
        this.body = body;
        this.type = type;
        this.read = read;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public String getTargetUid() { return targetUid; }
    public void setTargetUid(String targetUid) { this.targetUid = targetUid; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
