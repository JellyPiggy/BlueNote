package com.bluenote.content.file.api.dto;

import java.util.List;

public record InternalBatchValidateFileResponse(
        boolean valid,
        List<BatchValidatedFileResponse> files
) {
}

