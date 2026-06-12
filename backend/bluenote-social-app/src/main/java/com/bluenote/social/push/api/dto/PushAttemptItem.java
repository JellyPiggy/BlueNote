package com.bluenote.social.push.api.dto;

public record PushAttemptItem(
        String attemptId,
        String deviceId,
        String channel,
        String attemptStatus,
        String skipReason,
        String errorMessage,
        String attemptedAt,
        String ackedAt
) {
}
