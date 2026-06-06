package com.bluenote.content.file.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record InternalBatchValidateFileRequest(
        @NotBlank String ownerId,
        @NotBlank String scene,
        @NotEmpty List<String> fileIds,
        @Positive Integer maxCount
) {
}

