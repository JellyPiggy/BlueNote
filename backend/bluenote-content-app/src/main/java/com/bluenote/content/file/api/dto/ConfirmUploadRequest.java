package com.bluenote.content.file.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

public record ConfirmUploadRequest(
        String etag,
        @NotNull @Positive Long fileSize
) {
}
