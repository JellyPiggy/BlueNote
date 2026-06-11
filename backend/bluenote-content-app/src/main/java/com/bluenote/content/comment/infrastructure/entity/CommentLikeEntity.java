package com.bluenote.content.comment.infrastructure.entity;

import java.time.LocalDateTime;

public class CommentLikeEntity {

    private Long id;
    private Long commentId;
    private Long commentUserId;
    private Long userId;
    private String likeStatus;
    private LocalDateTime likedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getCommentUserId() { return commentUserId; }
    public void setCommentUserId(Long commentUserId) { this.commentUserId = commentUserId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLikeStatus() { return likeStatus; }
    public void setLikeStatus(String likeStatus) { this.likeStatus = likeStatus; }
    public LocalDateTime getLikedAt() { return likedAt; }
    public void setLikedAt(LocalDateTime likedAt) { this.likedAt = likedAt; }
    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime canceledAt) { this.canceledAt = canceledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
