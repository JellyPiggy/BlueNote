package com.bluenote.content.file.api.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalBindFileRequest(
        @NotBlank String fileId,
        @NotBlank String ownerId,
        @NotBlank String bindType,
        @NotBlank String bindId
) {
}

