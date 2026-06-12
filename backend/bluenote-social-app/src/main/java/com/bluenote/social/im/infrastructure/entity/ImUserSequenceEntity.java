package com.bluenote.social.im.infrastructure.entity;

import java.time.LocalDateTime;

public class ImUserSequenceEntity {

    private Long userId;
    private Long currentSeq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCurrentSeq() {
        return currentSeq;
    }

    public void setCurrentSeq(Long currentSeq) {
        this.currentSeq = currentSeq;
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
