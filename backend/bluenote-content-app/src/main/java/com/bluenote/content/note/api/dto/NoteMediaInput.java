package com.bluenote.content.note.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoteMediaInput(
        @NotBlank String fileId,
        @NotBlank String mediaType,
        @NotNull @Min(1) Integer sortOrder,
        @NotNull Boolean cover
) {
}

