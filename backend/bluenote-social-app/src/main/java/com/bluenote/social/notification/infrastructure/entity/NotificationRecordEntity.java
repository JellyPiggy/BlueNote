package com.bluenote.social.notification.infrastructure.entity;

import java.time.LocalDateTime;

public class NotificationRecordEntity {

    private Long notificationId;
    private Long receiverId;
    private Long actorId;
    private String category;
    private String notificationType;
    private String targetType;
    private String targetId;
    private String sourceType;
    private String sourceId;
    private Integer aggregate;
    private String aggregateKey;
    private String aggregateUnreadKey;
    private Integer actorCount;
    private String title;
    private String content;
    private String snapshotJson;
    private String jumpJson;
    private Integer readStatus;
    private Integer visibleStatus;
    private LocalDateTime lastEventAt;
    private LocalDateTime readAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Integer getAggregate() {
        return aggregate;
    }

    public void setAggregate(Integer aggregate) {
        this.aggregate = aggregate;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public void setAggregateKey(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    public String getAggregateUnreadKey() {
        return aggregateUnreadKey;
    }

    public void setAggregateUnreadKey(String aggregateUnreadKey) {
        this.aggregateUnreadKey = aggregateUnreadKey;
    }

    public Integer getActorCount() {
        return actorCount;
    }

    public void setActorCount(Integer actorCount) {
        this.actorCount = actorCount;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getJumpJson() {
        return jumpJson;
    }

    public void setJumpJson(String jumpJson) {
        this.jumpJson = jumpJson;
    }

    public Integer getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Integer readStatus) {
        this.readStatus = readStatus;
    }

    public Integer getVisibleStatus() {
        return visibleStatus;
    }

    public void setVisibleStatus(Integer visibleStatus) {
        this.visibleStatus = visibleStatus;
    }

    public LocalDateTime getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(LocalDateTime lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
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
