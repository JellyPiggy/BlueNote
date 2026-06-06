package com.bluenote.content.file.api.dto;

import java.util.Map;

public record UploadTokenResponse(
        String fileId,
        String uploadMethod,
        String uploadUrl,
        Map<String, String> headers,
        String expireAt,
        String objectKey
) {
}

