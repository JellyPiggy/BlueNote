package com.bluenote.social.push.api.dto;

import java.util.Map;

public record PushConsumeEventRequest(
        String topic,
        String consumerGroup,
        String eventId,
        String eventType,
        Integer eventVersion,
        String occurredAt,
        String traceId,
        String producer,
        String bizKey,
        Map<String, Object> payload,
        String envelopeJson
) {
}
