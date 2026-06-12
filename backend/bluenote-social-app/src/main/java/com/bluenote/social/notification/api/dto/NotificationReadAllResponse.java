package com.bluenote.social.notification.api.dto;

import java.util.Map;

public record NotificationReadAllResponse(
        int updatedCount,
        long totalUnread,
        Map<String, Long> categories
) {
}
