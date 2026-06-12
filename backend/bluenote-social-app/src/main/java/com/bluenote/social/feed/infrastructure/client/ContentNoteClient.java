package com.bluenote.social.feed.infrastructure.client;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ContentNoteClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String contentUri;

    public ContentNoteClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.content-uri}") String contentUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.contentUri = trimTrailingSlash(contentUri);
    }

    public List<AuthorRecentNotes> authorRecentNotes(
            List<String> authorIds,
            int limitPerAuthor,
            String publishedAfter
    ) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("authorIds", authorIds);
            request.put("limitPerAuthor", limitPerAuthor);
            if (publishedAfter != null && !publishedAfter.isBlank()) {
                request.put("publishedAfter", publishedAfter);
            }
            String response = restClient.post()
                    .uri(contentUri + "/internal/notes/authors/recent")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
            }
            JsonNode authorsNode = root.path("data").path("authors");
            if (!authorsNode.isArray()) {
                return List.of();
            }
            List<AuthorRecentNotes> authors = new ArrayList<>();
            for (JsonNode authorNode : authorsNode) {
                List<NoteSummary> notes = new ArrayList<>();
                JsonNode notesNode = authorNode.path("notes");
                if (notesNode.isArray()) {
                    for (JsonNode noteNode : notesNode) {
                        notes.add(new NoteSummary(
                                noteNode.path("noteId").asText(),
                                noteNode.path("authorId").asText(),
                                textOrNull(noteNode, "title"),
                                textOrNull(noteNode, "contentPreview"),
                                textOrNull(noteNode, "coverUrl"),
                                textOrNull(noteNode, "noteStatus"),
                                textOrNull(noteNode, "visibility"),
                                textOrNull(noteNode, "publishedAt")
                        ));
                    }
                }
                authors.add(new AuthorRecentNotes(authorNode.path("authorId").asText(), notes));
            }
            return authors;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
        }
    }

    public List<NoteSummary> batchSummary(List<String> noteIds, String viewerId, boolean includeInvisible) {
        if (noteIds.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("noteIds", noteIds);
            request.put("viewerId", viewerId);
            request.put("includeInvisible", includeInvisible);
            String response = restClient.post()
                    .uri(contentUri + "/internal/notes/batch-summary")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
            }
            JsonNode notesNode = root.path("data").path("notes");
            if (!notesNode.isArray()) {
                return List.of();
            }
            List<NoteSummary> notes = new ArrayList<>();
            for (JsonNode noteNode : notesNode) {
                notes.add(new NoteSummary(
                        noteNode.path("noteId").asText(),
                        noteNode.path("authorId").asText(),
                        textOrNull(noteNode, "title"),
                        textOrNull(noteNode, "contentPreview"),
                        textOrNull(noteNode, "coverUrl"),
                        textOrNull(noteNode, "noteStatus"),
                        textOrNull(noteNode, "visibility"),
                        textOrNull(noteNode, "publishedAt")
                ));
            }
            return notes;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
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

    public record AuthorRecentNotes(String authorId, List<NoteSummary> notes) {
    }

    public record NoteSummary(
            String noteId,
            String authorId,
            String title,
            String contentPreview,
            String coverUrl,
            String noteStatus,
            String visibility,
            String publishedAt
    ) {
    }
}
