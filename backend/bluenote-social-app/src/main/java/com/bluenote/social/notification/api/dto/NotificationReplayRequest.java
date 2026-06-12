package com.bluenote.social.notification.api.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationReplayRequest(
        @NotBlank
        String eventId,
        @NotBlank
        String topic
) {
}
