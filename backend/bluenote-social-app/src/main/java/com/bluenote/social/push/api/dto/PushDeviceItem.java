package com.bluenote.social.push.api.dto;

public record PushDeviceItem(
        String deviceId,
        String platform,
        String pushProvider,
        String deviceStatus,
        String appVersion,
        String lastActiveAt
) {
}
