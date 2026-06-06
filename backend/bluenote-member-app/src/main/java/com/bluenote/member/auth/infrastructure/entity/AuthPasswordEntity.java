package com.bluenote.member.auth.infrastructure.entity;

import java.time.LocalDateTime;

public class AuthPasswordEntity {

    private Long id;
    private Long userId;
    private String passwordHash;
    private String passwordAlgo;
    private Integer passwordVersion;
    private LocalDateTime passwordUpdatedAt;
    private Integer needReset;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordAlgo() {
        return passwordAlgo;
    }

    public void setPasswordAlgo(String passwordAlgo) {
        this.passwordAlgo = passwordAlgo;
    }

    public Integer getPasswordVersion() {
        return passwordVersion;
    }

    public void setPasswordVersion(Integer passwordVersion) {
        this.passwordVersion = passwordVersion;
    }

    public LocalDateTime getPasswordUpdatedAt() {
        return passwordUpdatedAt;
    }

    public void setPasswordUpdatedAt(LocalDateTime passwordUpdatedAt) {
        this.passwordUpdatedAt = passwordUpdatedAt;
    }

    public Integer getNeedReset() {
        return needReset;
    }

    public void setNeedReset(Integer needReset) {
        this.needReset = needReset;
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
