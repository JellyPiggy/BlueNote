package com.bluenote.social.push.api.dto;

public record PushKickRequest(
        String deviceId,
        String reason
) {
}
