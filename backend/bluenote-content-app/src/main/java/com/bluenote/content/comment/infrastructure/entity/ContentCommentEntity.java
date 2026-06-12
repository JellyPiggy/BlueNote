package com.bluenote.content.comment.infrastructure.entity;

import java.time.LocalDateTime;

public class ContentCommentEntity {

    private Long commentId;
    private Long noteId;
    private Long noteAuthorId;
    private Long userId;
    private Long rootId;
    private Long parentCommentId;
    private Long replyToUserId;
    private Integer level;
    private String commentStatus;
    private Long likeCountSnapshot;
    private Long replyCountSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getNoteAuthorId() { return noteAuthorId; }
    public void setNoteAuthorId(Long noteAuthorId) { this.noteAuthorId = noteAuthorId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRootId() { return rootId; }
    public void setRootId(Long rootId) { this.rootId = rootId; }
    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getCommentStatus() { return commentStatus; }
    public void setCommentStatus(String commentStatus) { this.commentStatus = commentStatus; }
    public Long getLikeCountSnapshot() { return likeCountSnapshot; }
    public void setLikeCountSnapshot(Long likeCountSnapshot) { this.likeCountSnapshot = likeCountSnapshot; }
    public Long getReplyCountSnapshot() { return replyCountSnapshot; }
    public void setReplyCountSnapshot(Long replyCountSnapshot) { this.replyCountSnapshot = replyCountSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
