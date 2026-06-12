package com.bluenote.social.push.api.dto;

public record PushDeviceUnbindResponse(
        String deviceId,
        String deviceStatus,
        String unboundAt
) {
}
