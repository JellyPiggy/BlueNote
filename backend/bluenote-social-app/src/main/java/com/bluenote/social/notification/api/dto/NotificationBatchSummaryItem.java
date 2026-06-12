package com.bluenote.social.notification.api.dto;

public record NotificationBatchSummaryItem(
        String notificationId,
        String receiverId,
        String category,
        String notificationType,
        String title,
        String content,
        String status
) {
}
