package com.bluenote.social.push.api.dto;

public record PushConsumeEventResponse(
        String eventId,
        String eventType,
        String status,
        String requestId
) {
}
