package com.bluenote.social.relation.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.relation.api.dto.RelationUserSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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

    public void ensureActorAllowed(String userId) {
        JsonNode item = firstDataItem("/internal/users/status-check", "results", Map.of(
                "userIds",
                List.of(userId),
                "scene",
                "FOLLOW"
        ));
        if (!item.path("exists").asBoolean(false)) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        if (!item.path("allowed").asBoolean(false)) {
            throw new BusinessException(ApiErrorCode.USER_DISABLED);
        }
    }

    public void ensureTargetFollowable(String userId) {
        JsonNode item = firstDataItem("/internal/users/status-check", "results", Map.of(
                "userIds",
                List.of(userId),
                "scene",
                "FOLLOW"
        ));
        if (!item.path("exists").asBoolean(false)) {
            throw new BusinessException(ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        }
        if (!item.path("allowed").asBoolean(false)) {
            throw new BusinessException(ApiErrorCode.RELATION_TARGET_DISABLED);
        }
    }

    public Map<String, RelationUserSummary> batchSummary(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            String response = restClient.post()
                    .uri(memberUri + "/internal/users/batch-summary")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("userIds", userIds))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            Map<String, RelationUserSummary> summaries = new LinkedHashMap<>();
            JsonNode users = root.path("data").path("users");
            if (!users.isArray()) {
                return summaries;
            }
            for (JsonNode item : users) {
                String userId = item.path("userId").asText();
                if (!"FOUND".equals(item.path("status").asText())) {
                    continue;
                }
                summaries.put(userId, new RelationUserSummary(
                        userId,
                        textOrNull(item, "bluenoteNo"),
                        textOrNull(item, "nickname"),
                        textOrNull(item, "avatarUrl"),
                        null,
                        textOrNull(item, "userStatus"),
                        item.hasNonNull("profileVersion") ? item.path("profileVersion").asLong() : null
                ));
            }
            return summaries;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
        }
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
