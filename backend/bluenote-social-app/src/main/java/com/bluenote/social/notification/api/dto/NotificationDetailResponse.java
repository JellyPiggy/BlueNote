package com.bluenote.social.notification.api.dto;

import java.util.Map;

public record NotificationDetailResponse(
        String notificationId,
        String category,
        String notificationType,
        String title,
        String content,
        boolean read,
        Map<String, Object> snapshot,
        Map<String, Object> jump
) {
}
