package com.bluenote.social.im.api.dto;

public record ImPushPolicyResponse(
        String conversationId,
        String userId,
        Boolean mute,
        Boolean pushAllowed
) {
}
