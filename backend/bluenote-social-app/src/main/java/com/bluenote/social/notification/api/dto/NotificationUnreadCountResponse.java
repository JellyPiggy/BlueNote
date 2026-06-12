package com.bluenote.social.notification.api.dto;

import java.util.Map;

public record NotificationUnreadCountResponse(
        long totalUnread,
        Map<String, Long> categories
) {
}
