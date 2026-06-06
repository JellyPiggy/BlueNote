package com.bluenote.content.note.api.dto;

public record PublishNoteResponse(
        String noteId,
        String noteStatus,
        String visibility,
        Integer currentVersion,
        String publishedAt
) {
}

