package com.bluenote.social.notification.api.dto;

public record NotificationRebuildUnreadResponse(
        String userId,
        boolean rebuilt,
        long totalUnread
) {
}
