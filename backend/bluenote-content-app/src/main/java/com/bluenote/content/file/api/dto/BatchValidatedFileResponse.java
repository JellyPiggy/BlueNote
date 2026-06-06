package com.bluenote.content.file.api.dto;

public record BatchValidatedFileResponse(
        String fileId,
        String mimeType,
        Long fileSize,
        String accessUrl
) {
}

