package com.bluenote.social.push.infrastructure.entity;

import java.time.LocalDateTime;

public class PushDeliveryRequestEntity {

    private String requestId;
    private String sourceService;
    private String sourceBizType;
    private String sourceBizId;
    private String scene;
    private Long targetUserId;
    private String targetDevicePolicy;
    private String deliveryStrategy;
    private Integer priority;
    private String title;
    private String body;
    private String dataJson;
    private String requestStatus;
    private String filteredReason;
    private Integer deliveredDeviceCount;
    private LocalDateTime expireAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getSourceBizType() {
        return sourceBizType;
    }

    public void setSourceBizType(String sourceBizType) {
        this.sourceBizType = sourceBizType;
    }

    public String getSourceBizId() {
        return sourceBizId;
    }

    public void setSourceBizId(String sourceBizId) {
        this.sourceBizId = sourceBizId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetDevicePolicy() {
        return targetDevicePolicy;
    }

    public void setTargetDevicePolicy(String targetDevicePolicy) {
        this.targetDevicePolicy = targetDevicePolicy;
    }

    public String getDeliveryStrategy() {
        return deliveryStrategy;
    }

    public void setDeliveryStrategy(String deliveryStrategy) {
        this.deliveryStrategy = deliveryStrategy;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getDataJson() {
        return dataJson;
    }

    public void setDataJson(String dataJson) {
        this.dataJson = dataJson;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getFilteredReason() {
        return filteredReason;
    }

    public void setFilteredReason(String filteredReason) {
        this.filteredReason = filteredReason;
    }

    public Integer getDeliveredDeviceCount() {
        return deliveredDeviceCount;
    }

    public void setDeliveredDeviceCount(Integer deliveredDeviceCount) {
        this.deliveredDeviceCount = deliveredDeviceCount;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
