package com.bluenote.member.user.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.SecurityHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserRelationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String relationUri;

    public UserRelationClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.relation-uri}") String relationUri
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.relationUri = trimTrailingSlash(relationUri);
    }

    public String followStatus(String currentUserId, String targetUserId) {
        try {
            String response = restClient.get()
                    .uri(relationUri + "/api/relations/following/{targetUserId}/status", targetUserId)
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .header(SecurityHeaders.USER_ID, currentUserId)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            String followStatus = root.path("data").path("followStatus").asText("UNKNOWN");
            if ("FOLLOWING".equals(followStatus) || "NOT_FOLLOWING".equals(followStatus)) {
                return followStatus;
            }
            return "UNKNOWN";
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
