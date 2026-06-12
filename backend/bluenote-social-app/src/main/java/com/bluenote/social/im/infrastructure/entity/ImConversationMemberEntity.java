package com.bluenote.social.im.infrastructure.entity;

import java.time.LocalDateTime;

public class ImConversationMemberEntity {

    private Long id;
    private Long conversationId;
    private Long userId;
    private Long peerUserId;
    private String memberRole;
    private String memberStatus;
    private Long lastReadSeq;
    private Long lastReceivedSeq;
    private Integer unreadCount;
    private Integer pinned;
    private Integer mute;
    private Integer hidden;
    private Long lastVisibleSeq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long currentSeq;
    private Long lastMessageId;
    private LocalDateTime lastMessageAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(Long peerUserId) {
        this.peerUserId = peerUserId;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole;
    }

    public String getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
    }

    public Long getLastReadSeq() {
        return lastReadSeq;
    }

    public void setLastReadSeq(Long lastReadSeq) {
        this.lastReadSeq = lastReadSeq;
    }

    public Long getLastReceivedSeq() {
        return lastReceivedSeq;
    }

    public void setLastReceivedSeq(Long lastReceivedSeq) {
        this.lastReceivedSeq = lastReceivedSeq;
    }

    public Integer getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Integer getPinned() {
        return pinned;
    }

    public void setPinned(Integer pinned) {
        this.pinned = pinned;
    }

    public Integer getMute() {
        return mute;
    }

    public void setMute(Integer mute) {
        this.mute = mute;
    }

    public Integer getHidden() {
        return hidden;
    }

    public void setHidden(Integer hidden) {
        this.hidden = hidden;
    }

    public Long getLastVisibleSeq() {
        return lastVisibleSeq;
    }

    public void setLastVisibleSeq(Long lastVisibleSeq) {
        this.lastVisibleSeq = lastVisibleSeq;
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
}
