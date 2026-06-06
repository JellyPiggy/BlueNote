package com.bluenote.content.note.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpsertNoteRequest(
        String clientRequestId,
        @Size(max = 128) String title,
        String content,
        String visibility,
        Boolean commentEnabled,
        @Valid List<NoteMediaInput> mediaFiles,
        List<@Size(max = 64) String> topics
) {
}

