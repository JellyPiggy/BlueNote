package com.bluenote.content.comment.infrastructure.entity;

import java.time.LocalDateTime;

public class UserCommentEntity {

    private Long id;
    private Long commentId;
    private Long userId;
    private Long noteId;
    private Long rootId;
    private Long parentCommentId;
    private String commentStatus;
    private String contentPreview;
    private String noteTitleSnapshot;
    private String noteCoverUrlSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getRootId() { return rootId; }
    public void setRootId(Long rootId) { this.rootId = rootId; }
    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }
    public String getCommentStatus() { return commentStatus; }
    public void setCommentStatus(String commentStatus) { this.commentStatus = commentStatus; }
    public String getContentPreview() { return contentPreview; }
    public void setContentPreview(String contentPreview) { this.contentPreview = contentPreview; }
    public String getNoteTitleSnapshot() { return noteTitleSnapshot; }
    public void setNoteTitleSnapshot(String noteTitleSnapshot) { this.noteTitleSnapshot = noteTitleSnapshot; }
    public String getNoteCoverUrlSnapshot() { return noteCoverUrlSnapshot; }
    public void setNoteCoverUrlSnapshot(String noteCoverUrlSnapshot) { this.noteCoverUrlSnapshot = noteCoverUrlSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
