package com.bluenote.social.push.api.dto;

import java.util.List;

public record PushOnlineStateResponse(
        String userId,
        boolean online,
        List<PushOnlineDeviceItem> devices
) {
}
