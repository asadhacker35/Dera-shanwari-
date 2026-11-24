package com.derashanwari.rider.model;

import java.io.Serializable;

public class GroupChatMember implements Serializable {
    private String uid;
    private String name;
    private String role;
    private String profileImageUrl;
    private boolean online;
    private long lastSeenAt;
    private long lastReadAt;

    public GroupChatMember() {}

    public GroupChatMember(String uid, String name, String role, String profileImageUrl,
                           boolean online, long lastSeenAt, long lastReadAt) {
        this.uid = uid;
        this.name = name != null ? name : "Member";
        this.role = role != null ? role : "RIDER";
        this.profileImageUrl = profileImageUrl != null ? profileImageUrl : "";
        this.online = online;
        this.lastSeenAt = lastSeenAt;
        this.lastReadAt = lastReadAt;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name != null ? name : "Member"; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role != null ? role : "RIDER"; }
    public void setRole(String role) { this.role = role; }
    public String getProfileImageUrl() { return profileImageUrl != null ? profileImageUrl : ""; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl != null ? profileImageUrl : ""; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public long getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(long lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public long getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(long lastReadAt) { this.lastReadAt = lastReadAt; }
}
