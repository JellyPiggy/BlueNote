package com.bluenote.content.file.api.dto;

public record InternalValidateFileResponse(
        boolean valid,
        ValidatedFileResponse file
) {
}

