package com.bluenote.social.notification.api.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItem> items,
        String nextCursor,
        boolean hasMore
) {
}
