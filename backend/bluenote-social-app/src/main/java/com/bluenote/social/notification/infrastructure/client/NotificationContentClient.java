package com.bluenote.social.notification.infrastructure.client;

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
public class NotificationContentClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String contentUri;

    public NotificationContentClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${bluenote.internal.content-uri}") String contentUri
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.contentUri = trimTrailingSlash(contentUri);
    }

    public Map<String, NoteSummary> batchNoteSummary(List<String> noteIds, String viewerId, boolean includeInvisible) {
        if (noteIds.isEmpty()) {
            return Map.of();
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
                return Map.of();
            }
            JsonNode notesNode = root.path("data").path("notes");
            if (!notesNode.isArray()) {
                return Map.of();
            }
            Map<String, NoteSummary> notes = new LinkedHashMap<>();
            for (JsonNode noteNode : notesNode) {
                String noteId = noteNode.path("noteId").asText();
                notes.put(noteId, new NoteSummary(
                        noteId,
                        textOrNull(noteNode, "authorId"),
                        textOrNull(noteNode, "title"),
                        textOrNull(noteNode, "contentPreview"),
                        textOrNull(noteNode, "coverUrl")
                ));
            }
            return notes;
        } catch (RestClientException | java.io.IOException exception) {
            return Map.of();
        }
    }

    public Map<String, CommentSummary> batchCommentSummary(List<String> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        try {
            String response = restClient.post()
                    .uri(contentUri + "/internal/comments/batch-summary")
                    .header(TraceConstants.TRACE_ID_HEADER, TraceIdHolder.currentOrNew())
                    .body(Map.of("commentIds", commentIds))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                return Map.of();
            }
            JsonNode commentsNode = root.path("data").path("comments");
            if (!commentsNode.isArray()) {
                return Map.of();
            }
            Map<String, CommentSummary> comments = new LinkedHashMap<>();
            for (JsonNode commentNode : commentsNode) {
                String commentId = commentNode.path("commentId").asText();
                comments.put(commentId, new CommentSummary(
                        commentId,
                        textOrNull(commentNode, "noteId"),
                        textOrNull(commentNode, "userId"),
                        textOrNull(commentNode, "contentPreview"),
                        textOrNull(commentNode, "commentStatus")
                ));
            }
            return comments;
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

    public record NoteSummary(
            String noteId,
            String authorId,
            String title,
            String contentPreview,
            String coverUrl
    ) {
    }

    public record CommentSummary(
            String commentId,
            String noteId,
            String userId,
            String contentPreview,
            String commentStatus
    ) {
    }
}
