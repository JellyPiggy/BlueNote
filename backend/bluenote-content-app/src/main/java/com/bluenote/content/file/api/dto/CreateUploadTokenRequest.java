package com.bluenote.content.file.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateUploadTokenRequest(
        @NotBlank String scene,
        @NotBlank String filename,
        @NotBlank String mimeType,
        @NotNull @Positive Long fileSize
) {
}

