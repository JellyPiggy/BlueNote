package com.bluenote.content.comment.api.dto;

public record CommentHotRebuildResponse(
        String noteId,
        int hotCount,
        int timeCount,
        String rebuiltAt
) {
}
