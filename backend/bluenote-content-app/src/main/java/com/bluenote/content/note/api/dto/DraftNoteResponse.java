package com.bluenote.content.note.api.dto;

public record DraftNoteResponse(
        String noteId,
        String noteStatus,
        Integer latestVersion,
        String updatedAt
) {
}

