package com.bluenote.social.notification.api.dto;

public record NotificationActorSummary(
        String userId,
        String nickname,
        String avatarUrl
) {
}
