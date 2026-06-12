package com.bluenote.social.notification.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationBatchSummaryRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> notificationIds
) {
}
