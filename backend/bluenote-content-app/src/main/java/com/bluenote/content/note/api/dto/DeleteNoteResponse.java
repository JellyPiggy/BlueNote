package com.bluenote.content.note.api.dto;

public record DeleteNoteResponse(
        String noteId,
        String noteStatus,
        String deletedAt
) {
}

