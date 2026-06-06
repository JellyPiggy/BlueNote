package com.bluenote.content.file.api.dto;

public record ConfirmUploadResponse(
        String fileId,
        String fileStatus,
        String scene,
        String accessUrl
) {
}

