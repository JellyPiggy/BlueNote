package com.bluenote.content.file.infrastructure.entity;

import java.time.LocalDateTime;

public class FileUploadSessionEntity {

    private Long uploadId;
    private Long fileId;
    private Long ownerId;
    private String uploadMethod;
    private String uploadStatus;
    private Long expectedSize;
    private String expectedMimeType;
    private LocalDateTime uploadUrlExpireAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUploadId() {
        return uploadId;
    }

    public void setUploadId(Long uploadId) {
        this.uploadId = uploadId;
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

    public String getUploadMethod() {
        return uploadMethod;
    }

    public void setUploadMethod(String uploadMethod) {
        this.uploadMethod = uploadMethod;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public Long getExpectedSize() {
        return expectedSize;
    }

    public void setExpectedSize(Long expectedSize) {
        this.expectedSize = expectedSize;
    }

    public String getExpectedMimeType() {
        return expectedMimeType;
    }

    public void setExpectedMimeType(String expectedMimeType) {
        this.expectedMimeType = expectedMimeType;
    }

    public LocalDateTime getUploadUrlExpireAt() {
        return uploadUrlExpireAt;
    }

    public void setUploadUrlExpireAt(LocalDateTime uploadUrlExpireAt) {
        this.uploadUrlExpireAt = uploadUrlExpireAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
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

