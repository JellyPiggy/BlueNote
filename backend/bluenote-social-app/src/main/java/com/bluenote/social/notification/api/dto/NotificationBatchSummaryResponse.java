package com.bluenote.social.notification.api.dto;

import java.util.List;

public record NotificationBatchSummaryResponse(
        List<NotificationBatchSummaryItem> notifications
) {
}
