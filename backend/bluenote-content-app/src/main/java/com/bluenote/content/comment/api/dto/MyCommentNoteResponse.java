package com.bluenote.content.comment.api.dto;

public record MyCommentNoteResponse(
        String noteId,
        String title,
        String coverUrl
) {
}
