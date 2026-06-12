package com.bluenote.social.notification.api.dto;

public record NotificationReadResponse(
        String notificationId,
        boolean read,
        long totalUnread
) {
}
