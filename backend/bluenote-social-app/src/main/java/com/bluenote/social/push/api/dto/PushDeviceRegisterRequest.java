package com.bluenote.social.push.api.dto;

public record PushDeviceRegisterRequest(
        String deviceId,
        String platform,
        String pushProvider,
        String providerClientId,
        String appVersion,
        String osVersion,
        String deviceModel
) {
}
