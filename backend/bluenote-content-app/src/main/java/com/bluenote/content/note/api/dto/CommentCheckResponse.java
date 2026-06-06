package com.bluenote.content.note.api.dto;

public record CommentCheckResponse(
        boolean exists,
        boolean commentAllowed,
        String authorId,
        String noteStatus,
        String visibility
) {
}

