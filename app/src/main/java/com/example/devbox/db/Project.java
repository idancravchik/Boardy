package com.example.devbox.db;

public class Project {
    private String id;
    private String title;
    private String description;
    private String blocksData;
    private String port1Sensor;
    private String port2Sensor;
    private boolean isPublic;
    private String userId;
    private String userName;
    private String school;
    private long createdAt;
    private long updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBlocksData() {
        return blocksData;
    }

    public void setBlocksData(String blocksData) {
        this.blocksData = blocksData;
    }

    public String getPort1Sensor() {
        return port1Sensor;
    }

    public void setPort1Sensor(String port1Sensor) {
        this.port1Sensor = port1Sensor;
    }

    public String getPort2Sensor() {
        return port2Sensor;
    }

    public void setPort2Sensor(String port2Sensor) {
        this.port2Sensor = port2Sensor;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
