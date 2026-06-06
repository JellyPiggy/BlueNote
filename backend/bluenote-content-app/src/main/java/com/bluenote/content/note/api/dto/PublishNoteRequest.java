package com.bluenote.content.note.api.dto;

public record PublishNoteRequest(
        String clientRequestId,
        String visibility
) {
}

