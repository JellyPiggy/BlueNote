package com.bluenote.content.file.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InternalBatchBindFileRequest(
        @NotBlank String ownerId,
        @NotBlank String bindType,
        @NotBlank String bindId,
        @NotEmpty List<String> fileIds
) {
}

