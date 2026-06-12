package com.bluenote.content.comment.api.dto;

public record CreateCommentResponse(
        String commentId,
        String noteId,
        String rootId,
        String parentCommentId,
        int level,
        String commentStatus,
        String createdAt
) {
}
