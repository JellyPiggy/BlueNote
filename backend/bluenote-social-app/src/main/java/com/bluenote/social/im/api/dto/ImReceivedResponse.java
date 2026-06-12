package com.bluenote.social.im.api.dto;

public record ImReceivedResponse(
        String conversationId,
        Long lastReceivedSeq
) {
}
