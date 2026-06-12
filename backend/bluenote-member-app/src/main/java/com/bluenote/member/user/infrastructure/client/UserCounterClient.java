package com.bluenote.member.user.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.member.user.api.dto.UserCountsResponse;
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
public class UserCounterClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String counterUri;

    public UserCounterClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.counter-uri}") String counterUri
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.counterUri = trimTrailingSlash(counterUri);
    }

    public UserCounterResult userCounts(String userId) {
        try {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("targetType", "USER");
            target.put("targetId", userId);
            target.put("fields", List.of("following_count", "follower_count", "note_count", "liked_count"));
            String response = restClient.post()
                    .uri(counterUri + "/internal/counters/batch")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("targets", List.of(target)))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            JsonNode item = root.path("data").path("items").path(0);
            if (item.isMissingNode()) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            JsonNode counts = item.path("counts");
            return new UserCounterResult(
                    new UserCountsResponse(
                            counts.path("following_count").asLong(0L),
                            counts.path("follower_count").asLong(0L),
                            counts.path("note_count").asLong(0L),
                            counts.path("liked_count").asLong(0L)
                    ),
                    item.path("degraded").asBoolean(false)
            );
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

    public record UserCounterResult(UserCountsResponse counts, boolean degraded) {
    }
}
