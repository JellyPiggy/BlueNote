package com.bluenote.member.user.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserFileClient {

    private static final List<String> USABLE_STATUSES = List.of("UPLOADED", "BOUND");
    private static final List<String> IMAGE_MIME_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024L;
    private static final long HOME_COVER_MAX_SIZE = 10 * 1024 * 1024L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String fileUri;

    public UserFileClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.file-uri}") String fileUri
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.fileUri = trimTrailingSlash(fileUri);
    }

    public ValidatedUserFile validateAvatar(String userId, String fileId) {
        return validateFile(userId, fileId, "USER_AVATAR", AVATAR_MAX_SIZE, ApiErrorCode.AVATAR_FILE_INVALID);
    }

    public ValidatedUserFile validateHomeCover(String userId, String fileId) {
        return validateFile(userId, fileId, "USER_HOME_COVER", HOME_COVER_MAX_SIZE, ApiErrorCode.HOME_COVER_FILE_INVALID);
    }

    public void bindAvatar(String userId, String fileId) {
        bindFile(userId, fileId, "USER_AVATAR", ApiErrorCode.AVATAR_FILE_INVALID);
    }

    public void bindHomeCover(String userId, String fileId) {
        bindFile(userId, fileId, "USER_HOME_COVER", ApiErrorCode.HOME_COVER_FILE_INVALID);
    }

    private ValidatedUserFile validateFile(
            String userId,
            String fileId,
            String scene,
            long maxSize,
            ApiErrorCode errorCode
    ) {
        try {
            String response = restClient.post()
                    .uri(fileUri + "/internal/files/validate")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of(
                            "fileId", fileId,
                            "ownerId", userId,
                            "scene", scene,
                            "requireStatus", USABLE_STATUSES,
                            "maxSize", maxSize,
                            "allowedMimeTypes", IMAGE_MIME_TYPES
                    ))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0 || !root.path("data").path("valid").asBoolean(false)) {
                throw new BusinessException(errorCode);
            }
            JsonNode file = root.path("data").path("file");
            String accessUrl = file.path("accessUrl").asText(null);
            if (accessUrl == null || accessUrl.isBlank()) {
                throw new BusinessException(errorCode);
            }
            return new ValidatedUserFile(fileId, accessUrl);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private void bindFile(String userId, String fileId, String bindType, ApiErrorCode errorCode) {
        try {
            String response = restClient.post()
                    .uri(fileUri + "/internal/files/bind")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of(
                            "fileId", fileId,
                            "ownerId", userId,
                            "bindType", bindType,
                            "bindId", userId
                    ))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(errorCode);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record ValidatedUserFile(String fileId, String accessUrl) {
    }
}
