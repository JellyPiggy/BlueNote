package com.bluenote.social.im.infrastructure.entity;

import java.time.LocalDateTime;

public class ImConversationEntity {

    private Long conversationId;
    private String conversationType;
    private String singleKey;
    private Long currentSeq;
    private Long lastMessageId;
    private LocalDateTime lastMessageAt;
    private String conversationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationType() {
        return conversationType;
    }

    public void setConversationType(String conversationType) {
        this.conversationType = conversationType;
    }

    public String getSingleKey() {
        return singleKey;
    }

    public void setSingleKey(String singleKey) {
        this.singleKey = singleKey;
    }

    public Long getCurrentSeq() {
        return currentSeq;
    }

    public void setCurrentSeq(Long currentSeq) {
        this.currentSeq = currentSeq;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(Long lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getConversationStatus() {
        return conversationStatus;
    }

    public void setConversationStatus(String conversationStatus) {
        this.conversationStatus = conversationStatus;
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
