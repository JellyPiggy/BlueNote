package com.bluenote.content.note.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NoteCounterClient {

    private static final List<String> NOTE_FIELDS = List.of("like_count", "collect_count", "comment_count");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String counterUri;

    public NoteCounterClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.counter-uri}") String counterUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.counterUri = trimTrailingSlash(counterUri);
    }

    public NoteCounterValues noteCounts(Long noteId) {
        if (noteId == null) {
            return null;
        }
        return noteCounts(List.of(noteId)).get(noteId);
    }

    public Map<Long, NoteCounterValues> noteCounts(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedIds = noteIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        try {
            List<Map<String, Object>> targets = normalizedIds.stream()
                    .map(this::noteTarget)
                    .toList();
            String response = restClient.post()
                    .uri(counterUri + "/internal/counters/batch")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("targets", targets))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
            }

            Map<Long, NoteCounterValues> result = new LinkedHashMap<>();
            for (JsonNode item : items) {
                if (!"NOTE".equals(item.path("targetType").asText())) {
                    continue;
                }
                Long targetId = parseTargetId(item.path("targetId").asText(null));
                if (targetId == null) {
                    continue;
                }
                JsonNode counts = item.path("counts");
                result.put(targetId, new NoteCounterValues(
                        counts.path("like_count").asLong(0L),
                        counts.path("collect_count").asLong(0L),
                        counts.path("comment_count").asLong(0L),
                        item.path("degraded").asBoolean(false)
                ));
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.DOWNSTREAM_FAILED);
        }
    }

    private Map<String, Object> noteTarget(Long noteId) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "NOTE");
        target.put("targetId", String.valueOf(noteId));
        target.put("fields", NOTE_FIELDS);
        return target;
    }

    private Long parseTargetId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record NoteCounterValues(
            long likeCount,
            long collectCount,
            long commentCount,
            boolean degraded
    ) {
    }
}
