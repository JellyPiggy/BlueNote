package com.bluenote.social.push.api.dto;

import java.util.Map;

public record PushSendRequest(
        String requestId,
        String sourceService,
        String sourceBizType,
        String sourceBizId,
        String scene,
        String targetUserId,
        String targetDevicePolicy,
        String deliveryStrategy,
        Integer priority,
        String title,
        String body,
        Map<String, Object> data,
        String expireAt
) {
}
