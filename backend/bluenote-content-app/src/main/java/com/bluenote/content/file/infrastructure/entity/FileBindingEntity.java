package com.bluenote.content.file.infrastructure.entity;

import java.time.LocalDateTime;

public class FileBindingEntity {

    private Long id;
    private Long fileId;
    private Long ownerId;
    private String bindType;
    private String bindId;
    private String bindStatus;
    private Long bindVersion;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getBindType() {
        return bindType;
    }

    public void setBindType(String bindType) {
        this.bindType = bindType;
    }

    public String getBindId() {
        return bindId;
    }

    public void setBindId(String bindId) {
        this.bindId = bindId;
    }

    public String getBindStatus() {
        return bindStatus;
    }

    public void setBindStatus(String bindStatus) {
        this.bindStatus = bindStatus;
    }

    public Long getBindVersion() {
        return bindVersion;
    }

    public void setBindVersion(Long bindVersion) {
        this.bindVersion = bindVersion;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(LocalDateTime boundAt) {
        this.boundAt = boundAt;
    }

    public LocalDateTime getUnboundAt() {
        return unboundAt;
    }

    public void setUnboundAt(LocalDateTime unboundAt) {
        this.unboundAt = unboundAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

