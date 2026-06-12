package com.bluenote.social.notification.api.dto;

import java.util.Map;

public record NotificationConsumeEventRequest(
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
