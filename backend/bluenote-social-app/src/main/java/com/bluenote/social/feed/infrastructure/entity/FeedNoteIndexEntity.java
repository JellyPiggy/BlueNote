package com.bluenote.social.feed.infrastructure.entity;

import java.time.LocalDateTime;

public class FeedNoteIndexEntity {

    private Long noteId;
    private Long authorId;
    private String titleSnapshot;
    private String contentPreviewSnapshot;
    private String coverUrlSnapshot;
    private String visibility;
    private String noteStatus;
    private String itemStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public void setTitleSnapshot(String titleSnapshot) {
        this.titleSnapshot = titleSnapshot;
    }

    public String getContentPreviewSnapshot() {
        return contentPreviewSnapshot;
    }

    public void setContentPreviewSnapshot(String contentPreviewSnapshot) {
        this.contentPreviewSnapshot = contentPreviewSnapshot;
    }

    public String getCoverUrlSnapshot() {
        return coverUrlSnapshot;
    }

    public void setCoverUrlSnapshot(String coverUrlSnapshot) {
        this.coverUrlSnapshot = coverUrlSnapshot;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getNoteStatus() {
        return noteStatus;
    }

    public void setNoteStatus(String noteStatus) {
        this.noteStatus = noteStatus;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
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
