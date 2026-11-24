package com.derashanwari.admin.model;

import java.io.Serializable;

public class GroupChatMessage implements Serializable {
    private String id;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String senderAvatarUrl;
    private String message;
    private String imageUrl;
    private long createdAt;

    public GroupChatMessage() {}

    public GroupChatMessage(String id, String senderId, String senderName, String senderRole,
                            String senderAvatarUrl, String message, String imageUrl, long createdAt) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName != null ? senderName : "User";
        this.senderRole = senderRole != null ? senderRole : "CUSTOMER";
        this.senderAvatarUrl = senderAvatarUrl != null ? senderAvatarUrl : "";
        this.message = message != null ? message : "";
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.createdAt = createdAt;
    }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.trim().isEmpty();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId != null ? senderId : ""; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName != null ? senderName : "User"; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole != null ? senderRole : "CUSTOMER"; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getSenderAvatarUrl() { return senderAvatarUrl != null ? senderAvatarUrl : ""; }
    public void setSenderAvatarUrl(String senderAvatarUrl) {
        this.senderAvatarUrl = senderAvatarUrl != null ? senderAvatarUrl : "";
    }

    public String getMessage() { return message != null ? message : ""; }
    public void setMessage(String message) { this.message = message != null ? message : ""; }

    public String getImageUrl() { return imageUrl != null ? imageUrl : ""; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl != null ? imageUrl : ""; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
