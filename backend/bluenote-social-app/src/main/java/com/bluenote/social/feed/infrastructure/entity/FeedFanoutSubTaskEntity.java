package com.bluenote.social.feed.infrastructure.entity;

import java.time.LocalDateTime;

public class FeedFanoutSubTaskEntity {

    private String subTaskId;
    private String taskId;
    private Long noteId;
    private Long authorId;
    private LocalDateTime publishedAt;
    private String targetUserIdsJson;
    private Long progressUserId;
    private String subTaskStatus;
    private String messageStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getSubTaskId() {
        return subTaskId;
    }

    public void setSubTaskId(String subTaskId) {
        this.subTaskId = subTaskId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getTargetUserIdsJson() {
        return targetUserIdsJson;
    }

    public void setTargetUserIdsJson(String targetUserIdsJson) {
        this.targetUserIdsJson = targetUserIdsJson;
    }

    public Long getProgressUserId() {
        return progressUserId;
    }

    public void setProgressUserId(Long progressUserId) {
        this.progressUserId = progressUserId;
    }

    public String getSubTaskStatus() {
        return subTaskStatus;
    }

    public void setSubTaskStatus(String subTaskStatus) {
        this.subTaskStatus = subTaskStatus;
    }

    public String getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(String messageStatus) {
        this.messageStatus = messageStatus;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
