package com.bluenote.content.note.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCheckRequest(
        @NotBlank String noteId,
        String viewerId
) {
}

