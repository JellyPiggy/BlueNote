package com.bluenote.content.file.infrastructure.storage;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.content.config.ContentStorageConfiguration.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorageClient implements ObjectStorageClient {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioObjectStorageClient(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public PresignedUpload createPresignedPutUrl(
            String bucket,
            String objectKey,
            String mimeType,
            int expiresSeconds
    ) {
        try {
            ensureBucket(bucket);
            Map<String, String> headers = Map.of("Content-Type", mimeType);
            String uploadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expiresSeconds)
                    .extraHeaders(headers)
                    .build());
            return new PresignedUpload(
                    uploadUrl,
                    headers,
                    OffsetDateTime.now(CHINA_ZONE).plusSeconds(expiresSeconds)
            );
        } catch (Exception exception) {
            throw new BusinessException(ApiErrorCode.OBJECT_STORAGE_FAILED);
        }
    }

    @Override
    public StoredObjectInfo statObject(String bucket, String objectKey) {
        try {
            StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return new StoredObjectInfo(response.size(), normalizeEtag(response.etag()));
        } catch (ErrorResponseException exception) {
            return null;
        } catch (Exception exception) {
            throw new BusinessException(ApiErrorCode.OBJECT_STORAGE_FAILED);
        }
    }

    @Override
    public String publicUrl(String bucket, String objectKey) {
        return trimTrailingSlash(properties.getPublicEndpoint()) + "/" + bucket + "/" + objectKey;
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int expiresSeconds) {
        try {
            ensureBucket(bucket);
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expiresSeconds)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ApiErrorCode.OBJECT_STORAGE_FAILED);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket)
                    .build());
        }
    }

    private String trimTrailingSlash(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String normalizeEtag(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.replace("\"", "");
    }
}

