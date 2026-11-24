package com.derashanwari.rider.model;

import java.io.Serializable;

public class Banner implements Serializable {
    private String id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String imageFileId;
    private String localDrawableName;
    private boolean active;
    private int sortOrder;

    public Banner() {}

    public Banner(String id, String title, String subtitle, String imageUrl,
                  String localDrawableName, boolean active, int sortOrder) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.imageUrl = imageUrl;
        this.imageFileId = "";
        this.localDrawableName = localDrawableName;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageFileId() { return imageFileId != null ? imageFileId : ""; }
    public void setImageFileId(String imageFileId) { this.imageFileId = imageFileId != null ? imageFileId : ""; }
    public String getLocalDrawableName() { return localDrawableName; }
    public void setLocalDrawableName(String localDrawableName) { this.localDrawableName = localDrawableName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
