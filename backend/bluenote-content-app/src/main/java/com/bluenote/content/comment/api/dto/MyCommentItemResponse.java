package com.bluenote.content.comment.api.dto;

public record MyCommentItemResponse(
        String commentId,
        String noteId,
        String content,
        String commentStatus,
        String createdAt,
        MyCommentNoteResponse note
) {
}
