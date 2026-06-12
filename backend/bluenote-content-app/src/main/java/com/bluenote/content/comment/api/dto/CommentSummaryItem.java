package com.bluenote.content.comment.api.dto;

public record CommentSummaryItem(
        String commentId,
        String noteId,
        String userId,
        String rootId,
        String contentPreview,
        String commentStatus,
        String createdAt
) {
}
