package com.bluenote.content.file.api.dto;

public record FileAccessUrlResponse(
        String fileId,
        String accessUrl,
        String expireAt,
        String accessLevel
) {
}

