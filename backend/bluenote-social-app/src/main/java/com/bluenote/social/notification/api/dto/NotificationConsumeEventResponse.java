package com.bluenote.social.notification.api.dto;

public record NotificationConsumeEventResponse(
        String eventId,
        String eventType,
        String status,
        String notificationId
) {
}
