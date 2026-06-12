package com.bluenote.social.counter.infrastructure.entity;

import java.time.LocalDateTime;

public class CounterRebuildTaskEntity {

    private String taskId;
    private String taskType;
    private String targetType;
    private Long targetId;
    private String fieldsJson;
    private String taskStatus;
    private String progressJson;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
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

    public String getFieldsJson() {
        return fieldsJson;
    }

    public void setFieldsJson(String fieldsJson) {
        this.fieldsJson = fieldsJson;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getProgressJson() {
        return progressJson;
    }

    public void setProgressJson(String progressJson) {
        this.progressJson = progressJson;
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
