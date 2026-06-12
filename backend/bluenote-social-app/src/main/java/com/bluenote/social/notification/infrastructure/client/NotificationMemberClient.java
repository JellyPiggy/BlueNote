package com.bluenote.social.notification.infrastructure.client;

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
public class NotificationMemberClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String memberUri;

    public NotificationMemberClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.member-uri}") String memberUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.memberUri = trimTrailingSlash(memberUri);
    }

    public Map<String, UserSummary> batchSummary(List<String> userIds) {
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
                        textOrNull(item, "avatarUrl")
                ));
            }
            return summaries;
        } catch (RestClientException | java.io.IOException exception) {
            return Map.of();
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

    public record UserSummary(String userId, String nickname, String avatarUrl) {
    }
}
