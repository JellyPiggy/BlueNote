package com.bluenote.content.file.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InternalValidateFileRequest(
        @NotBlank String fileId,
        @NotBlank String ownerId,
        @NotBlank String scene,
        @NotEmpty List<String> requireStatus,
        Long maxSize,
        List<String> allowedMimeTypes
) {
}

