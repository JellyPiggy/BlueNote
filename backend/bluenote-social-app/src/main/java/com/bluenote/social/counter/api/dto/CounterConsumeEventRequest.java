package com.bluenote.social.counter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CounterConsumeEventRequest(
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
