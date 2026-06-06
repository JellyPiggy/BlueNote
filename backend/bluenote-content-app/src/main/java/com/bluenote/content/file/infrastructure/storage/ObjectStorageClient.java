package com.bluenote.content.file.infrastructure.storage;

import java.time.OffsetDateTime;
import java.util.Map;

public interface ObjectStorageClient {

    PresignedUpload createPresignedPutUrl(String bucket, String objectKey, String mimeType, int expiresSeconds);

    StoredObjectInfo statObject(String bucket, String objectKey);

    String publicUrl(String bucket, String objectKey);

    String presignedGetUrl(String bucket, String objectKey, int expiresSeconds);

    record PresignedUpload(
            String uploadUrl,
            Map<String, String> headers,
            OffsetDateTime expireAt
    ) {
    }

    record StoredObjectInfo(
            long size,
            String etag
    ) {
    }
}

