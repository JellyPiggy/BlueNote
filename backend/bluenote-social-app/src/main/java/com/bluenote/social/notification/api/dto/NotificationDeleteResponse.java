package com.bluenote.social.notification.api.dto;

public record NotificationDeleteResponse(
        String notificationId,
        boolean deleted
) {
}
