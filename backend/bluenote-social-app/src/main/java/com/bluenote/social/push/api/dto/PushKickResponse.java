package com.bluenote.social.push.api.dto;

public record PushKickResponse(
        String userId,
        String deviceId,
        boolean kicked
) {
}
