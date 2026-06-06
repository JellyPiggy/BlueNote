package com.bluenote.content.note.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.note.api.dto.NoteAuthorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MemberInternalClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String memberUri;

    public MemberInternalClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.member-uri}") String memberUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.memberUri = trimTrailingSlash(memberUri);
    }

    public void ensureUserAllowed(String userId) {
        JsonNode item = firstDataItem("/internal/users/status-check", "results", Map.of(
                "userIds",
                List.of(userId),
                "scene",
                "NOTE_PUBLISH"
        ));
        if (!item.path("exists").asBoolean(false)) {
            throw new BusinessException(ApiErrorCode.USER_NOT_FOUND);
        }
        if (!item.path("allowed").asBoolean(false)) {
            throw new BusinessException(ApiErrorCode.USER_DISABLED);
        }
    }

    public NoteAuthorResponse authorSummary(String userId) {
        JsonNode item = firstDataItem("/internal/users/batch-summary", "users", Map.of("userIds", List.of(userId)));
        if (!"FOUND".equals(item.path("status").asText())) {
            throw new BusinessException(ApiErrorCode.USER_NOT_FOUND);
        }
        return new NoteAuthorResponse(
                item.path("userId").asText(userId),
                textOrNull(item, "nickname"),
                textOrNull(item, "avatarUrl"),
                textOrNull(item, "userStatus")
        );
    }

    private JsonNode firstDataItem(String path, String dataArrayField, Map<String, ?> requestBody) {
        try {
            String response = restClient.post()
                    .uri(memberUri + path)
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            JsonNode items = root.path("data").path(dataArrayField);
            if (!items.isArray() || items.isEmpty()) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            return items.get(0);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
