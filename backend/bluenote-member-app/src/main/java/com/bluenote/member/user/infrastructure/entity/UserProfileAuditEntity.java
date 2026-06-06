package com.bluenote.member.user.infrastructure.entity;

import java.time.LocalDateTime;

public class UserProfileAuditEntity {

    private Long id;
    private Long userId;
    private String fieldName;
    private String oldValueMask;
    private String newValueMask;
    private Long operatorId;
    private String operatorType;
    private String traceId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValueMask() {
        return oldValueMask;
    }

    public void setOldValueMask(String oldValueMask) {
        this.oldValueMask = oldValueMask;
    }

    public String getNewValueMask() {
        return newValueMask;
    }

    public void setNewValueMask(String newValueMask) {
        this.newValueMask = newValueMask;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
