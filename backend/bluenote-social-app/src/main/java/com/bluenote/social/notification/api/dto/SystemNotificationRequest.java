package com.bluenote.social.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record SystemNotificationRequest(
        @NotBlank
        String requestId,
        @NotBlank
        String notificationType,
        @NotEmpty
        @Size(max = 100)
        List<String> receiverIds,
        @NotBlank
        @Size(max = 128)
        String title,
        @NotBlank
        @Size(max = 512)
        String content,
        Map<String, Object> jump,
        Boolean pushRequired,
        String expireAt
) {
}
