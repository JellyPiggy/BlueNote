package com.bluenote.social.notification.api.dto;

import java.util.List;
import java.util.Map;

public record NotificationItem(
        String notificationId,
        String category,
        String notificationType,
        boolean aggregate,
        int actorCount,
        String title,
        String content,
        boolean read,
        List<NotificationActorSummary> actors,
        Map<String, Object> target,
        Map<String, Object> jump,
        String createdAt,
        String lastEventAt
) {
}
