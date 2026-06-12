package com.bluenote.social.counter.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
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
public class ContentCounterSourceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String contentUri;

    public ContentCounterSourceClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.content-uri}") String contentUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.contentUri = trimTrailingSlash(contentUri);
    }

    public Map<String, Long> noteCounts(String targetType, String targetId, List<String> fields) {
        return sourceCounts("/internal/notes/counter-source", targetType, targetId, fields);
    }

    public Map<String, Long> commentCounts(String targetType, String targetId, List<String> fields) {
        return sourceCounts("/internal/comments/counter-source", targetType, targetId, fields);
    }

    private Map<String, Long> sourceCounts(String path, String targetType, String targetId, List<String> fields) {
        try {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("targetType", targetType);
            target.put("targetId", targetId);
            target.put("fields", fields);
            String response = restClient.post()
                    .uri(contentUri + path)
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("targets", List.of(target)))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.COUNTER_SOURCE_UNAVAILABLE);
            }
            JsonNode items = root.path("data").path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new BusinessException(ApiErrorCode.COUNTER_SOURCE_UNAVAILABLE);
            }
            return countsOf(items.get(0));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.COUNTER_SOURCE_UNAVAILABLE);
        }
    }

    private Map<String, Long> countsOf(JsonNode item) {
        Map<String, Long> counts = new LinkedHashMap<>();
        JsonNode countNode = item.path("counts");
        if (!countNode.isObject()) {
            return counts;
        }
        countNode.fields().forEachRemaining(entry -> counts.put(entry.getKey(), entry.getValue().asLong(0L)));
        return counts;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
