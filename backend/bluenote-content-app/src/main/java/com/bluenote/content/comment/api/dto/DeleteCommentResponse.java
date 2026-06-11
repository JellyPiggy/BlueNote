package com.bluenote.content.comment.api.dto;

public record DeleteCommentResponse(
        String commentId,
        String commentStatus,
        String deletedAt
) {
}
