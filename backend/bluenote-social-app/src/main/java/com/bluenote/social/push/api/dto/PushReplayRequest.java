package com.bluenote.social.push.api.dto;

public record PushReplayRequest(
        String topic,
        String eventId
) {
}
