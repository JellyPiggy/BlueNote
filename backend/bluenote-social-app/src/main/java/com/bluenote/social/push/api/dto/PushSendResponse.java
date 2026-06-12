package com.bluenote.social.push.api.dto;

public record PushSendResponse(
        String requestId,
        String requestStatus,
        int deliveredDeviceCount,
        String filteredReason
) {
}
