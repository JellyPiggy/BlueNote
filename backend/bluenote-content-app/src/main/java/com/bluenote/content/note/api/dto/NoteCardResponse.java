package com.bluenote.content.note.api.dto;

public record NoteCardResponse(
        String noteId,
        String title,
        String coverUrl,
        String authorId,
        Long likeCount,
        Long collectCount,
        String publishedAt
) {
}

