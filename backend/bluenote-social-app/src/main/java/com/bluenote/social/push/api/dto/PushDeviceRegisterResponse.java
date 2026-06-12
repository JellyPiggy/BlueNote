package com.bluenote.social.push.api.dto;

public record PushDeviceRegisterResponse(
        String deviceId,
        String userId,
        String platform,
        String pushProvider,
        String deviceStatus,
        boolean realtimeEnabled,
        String websocketUrl,
        String registeredAt,
        String lastActiveAt
) {
}
