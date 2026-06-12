package com.bluenote.social.push.api.dto;

import java.util.List;
import java.util.Map;

public record PushRequestDetailResponse(
        String requestId,
        String requestStatus,
        String targetUserId,
        String sourceBizType,
        String sourceBizId,
        String scene,
        String title,
        String body,
        String deliveryStrategy,
        Map<String, Object> data,
        List<PushAttemptItem> attempts,
        String createdAt,
        String completedAt
) {
}
