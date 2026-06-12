package com.bluenote.social.counter.infrastructure.entity;

import java.time.LocalDateTime;

public class CounterSnapshotEntity {

    private Long id;
    private String targetType;
    private Long targetId;
    private String counterField;
    private Long counterValue;
    private Long snapshotVersion;
    private LocalDateTime flushedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getCounterField() {
        return counterField;
    }

    public void setCounterField(String counterField) {
        this.counterField = counterField;
    }

    public Long getCounterValue() {
        return counterValue;
    }

    public void setCounterValue(Long counterValue) {
        this.counterValue = counterValue;
    }

    public Long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public LocalDateTime getFlushedAt() {
        return flushedAt;
    }

    public void setFlushedAt(LocalDateTime flushedAt) {
        this.flushedAt = flushedAt;
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
