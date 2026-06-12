package com.bluenote.social.counter.infrastructure.entity;

import java.time.LocalDateTime;

public class CounterDeltaLogEntity {

    private String deltaId;
    private String sourceEventId;
    private String sourceEventType;
    private String targetType;
    private Long targetId;
    private String counterField;
    private Long deltaValue;
    private String applyStatus;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getDeltaId() {
        return deltaId;
    }

    public void setDeltaId(String deltaId) {
        this.deltaId = deltaId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getSourceEventType() {
        return sourceEventType;
    }

    public void setSourceEventType(String sourceEventType) {
        this.sourceEventType = sourceEventType;
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

    public Long getDeltaValue() {
        return deltaValue;
    }

    public void setDeltaValue(Long deltaValue) {
        this.deltaValue = deltaValue;
    }

    public String getApplyStatus() {
        return applyStatus;
    }

    public void setApplyStatus(String applyStatus) {
        this.applyStatus = applyStatus;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
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
