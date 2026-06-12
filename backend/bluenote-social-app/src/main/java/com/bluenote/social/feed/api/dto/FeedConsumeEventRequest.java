package com.bluenote.social.feed.api.dto;

import java.util.Map;

public record FeedConsumeEventRequest(
        String topic,
        String consumerGroup,
        String eventId,
        String eventType,
        Integer eventVersion,
        String occurredAt,
        String traceId,
        String producer,
        String bizKey,
        Map<String, Object> payload
) {
}
