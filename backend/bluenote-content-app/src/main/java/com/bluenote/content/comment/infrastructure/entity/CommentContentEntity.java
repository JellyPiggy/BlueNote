package com.bluenote.content.comment.infrastructure.entity;

import java.time.LocalDateTime;

public class CommentContentEntity {

    private Long commentId;
    private String content;
    private String contentPreview;
    private String auditStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentPreview() { return contentPreview; }
    public void setContentPreview(String contentPreview) { this.contentPreview = contentPreview; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
