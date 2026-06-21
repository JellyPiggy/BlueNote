package com.bluenote.content.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CommentConsumeEventRequest(
        @NotBlank
        String topic,

        @NotBlank
        String consumerGroup,

        @NotBlank
        String eventId,

        @NotBlank
        String eventType,

        Integer eventVersion,

        @NotBlank
        String occurredAt,

        String traceId,

        String producer,

        String bizKey,

        @NotNull
        Map<String, Object> payload
) {
}
