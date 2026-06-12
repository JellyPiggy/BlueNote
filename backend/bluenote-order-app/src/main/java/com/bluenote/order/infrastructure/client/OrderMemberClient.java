package com.bluenote.order.infrastructure.client;

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
public class OrderMemberClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String memberUri;

    public OrderMemberClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.member-uri}") String memberUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.memberUri = trimTrailingSlash(memberUri);
    }

    public boolean orderAllowed(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        try {
            String response = restClient.post()
                    .uri(memberUri + "/internal/users/status-check")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("userIds", List.of(userId), "scene", "ORDER"))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                return true;
            }
            JsonNode results = root.path("data").path("results");
            if (!results.isArray() || results.isEmpty()) {
                return true;
            }
            JsonNode item = results.get(0);
            return item.path("exists").asBoolean(false) && item.path("allowed").asBoolean(false);
        } catch (RestClientException | java.io.IOException exception) {
            return true;
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
