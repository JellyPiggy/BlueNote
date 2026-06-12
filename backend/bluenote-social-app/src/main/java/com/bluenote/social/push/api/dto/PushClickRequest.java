package com.bluenote.social.push.api.dto;

import java.util.Map;

public record PushClickRequest(
        String requestId,
        String deviceId,
        String clickedAt,
        Map<String, Object> data
) {
}
