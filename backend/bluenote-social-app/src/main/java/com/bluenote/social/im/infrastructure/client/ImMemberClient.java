package com.bluenote.social.im.infrastructure.client;

import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
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
public class ImMemberClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String memberUri;

    public ImMemberClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.member-uri}") String memberUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.memberUri = trimTrailingSlash(memberUri);
    }

    public Map<String, UserSummary> batchSummary(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
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
                return Map.of();
            }
            JsonNode users = root.path("data").path("users");
            if (!users.isArray()) {
                return Map.of();
            }
            Map<String, UserSummary> summaries = new LinkedHashMap<>();
            for (JsonNode item : users) {
                String userId = item.path("userId").asText();
                if (!"FOUND".equals(item.path("status").asText())) {
                    continue;
                }
                summaries.put(userId, new UserSummary(
                        userId,
                        textOrNull(item, "nickname"),
                        textOrNull(item, "avatarUrl"),
                        textOrNull(item, "userStatus")
                ));
            }
            return summaries;
        } catch (RestClientException | java.io.IOException exception) {
            return Map.of();
        }
    }

    public boolean sendAllowed(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        try {
            String response = restClient.post()
                    .uri(memberUri + "/internal/users/status-check")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("userIds", List.of(userId), "scene", "SEND_IM"))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                return false;
            }
            JsonNode results = root.path("data").path("results");
            if (!results.isArray() || results.isEmpty()) {
                return false;
            }
            JsonNode item = results.get(0);
            return item.path("exists").asBoolean(false) && item.path("allowed").asBoolean(false);
        } catch (RestClientException | java.io.IOException exception) {
            return false;
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

    public record UserSummary(String userId, String nickname, String avatarUrl, String userStatus) {
    }
}
