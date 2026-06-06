package com.bluenote.content.file.api.dto;

public record ValidatedFileResponse(
        String fileId,
        String ownerId,
        String scene,
        String mimeType,
        Long fileSize,
        String fileStatus,
        String accessUrl
) {
}

