package com.bluenote.content.note.api.dto;

public record NoteMediaResponse(
        String fileId,
        String mediaType,
        Integer sortOrder,
        Boolean cover,
        String accessUrl
) {
}

