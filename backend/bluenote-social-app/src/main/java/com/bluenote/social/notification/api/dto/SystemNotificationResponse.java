package com.bluenote.social.notification.api.dto;

import java.util.List;

public record SystemNotificationResponse(
        String requestId,
        int createdCount,
        List<String> notificationIds
) {
}
