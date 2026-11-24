package com.derashanwari.admin.model;

import java.io.Serializable;

public class Category implements Serializable {
    private String id;
    private String name;
    private String imageUrl;
    private String imageFileId;
    private String localDrawableName;
    private boolean available;
    private int sortOrder;

    public Category() {}

    public Category(String id, String name, String imageUrl, String localDrawableName,
                    boolean available, int sortOrder) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.imageFileId = "";
        this.localDrawableName = localDrawableName;
        this.available = available;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImageFileId() { return imageFileId != null ? imageFileId : ""; }
    public void setImageFileId(String imageFileId) { this.imageFileId = imageFileId != null ? imageFileId : ""; }

    public String getLocalDrawableName() { return localDrawableName; }
    public void setLocalDrawableName(String localDrawableName) { this.localDrawableName = localDrawableName; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
