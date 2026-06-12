package com.bluenote.social.push.api.dto;

public record PushOnlineDeviceItem(
        String deviceId,
        String connectionId,
        String connectedAt,
        String lastSeenAt
) {
}
