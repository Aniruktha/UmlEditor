package com.umlcollab.server.models;

import java.time.LocalDateTime;

public class UMLDiagram {
    private int diagramId;
    private String diagramName;
    private Integer projectId;
    private Integer ownerId;
    private String mongoId;
    private String content;
    private LocalDateTime lastModified;
    private String accessRole;

    public UMLDiagram() {}

    public UMLDiagram(String diagramName, Integer projectId, Integer ownerId, String mongoId, String content) {
        this.diagramName = diagramName;
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.mongoId = mongoId;
        this.content = content;
    }

    public UMLDiagram(int diagramId, String diagramName, Integer projectId, Integer ownerId, String mongoId, String content, LocalDateTime lastModified) {
        this.diagramId = diagramId;
        this.diagramName = diagramName;
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.mongoId = mongoId;
        this.content = content;
        this.lastModified = lastModified;
    }

    public int getDiagramId() { return diagramId; }
    public void setDiagramId(int diagramId) { this.diagramId = diagramId; }

    public String getDiagramName() { return diagramName; }
    public void setDiagramName(String diagramName) { this.diagramName = diagramName; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public Integer getOwnerId() { return ownerId; }
    public void setOwnerId(Integer ownerId) { this.ownerId = ownerId; }

    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

    public String getAccessRole() { return accessRole; }
    public void setAccessRole(String accessRole) { this.accessRole = accessRole; }

    @Override
    public String toString() {
        return "UMLDiagram{" +
                "diagramId=" + diagramId +
                ", diagramName='" + diagramName + '\'' +
                ", projectId=" + projectId +
                ", ownerId=" + ownerId +
                ", mongoId='" + mongoId + '\'' +
                ", content=" + (content == null ? "null" : content.length() + " chars") +
                ", lastModified=" + lastModified +
                ", accessRole='" + accessRole + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UMLDiagram that = (UMLDiagram) o;
        return diagramId == that.diagramId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(diagramId);
    }
}