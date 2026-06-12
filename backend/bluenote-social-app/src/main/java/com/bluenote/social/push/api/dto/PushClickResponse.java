package com.bluenote.social.push.api.dto;

public record PushClickResponse(
        String requestId,
        boolean recorded
) {
}
