package com.bluenote.social.notification.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationDeleteBatchRequest(
        @NotEmpty
        @Size(max = 50)
        List<String> notificationIds
) {
}
