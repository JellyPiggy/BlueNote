package com.bluenote.social.im.api.dto;

public record ImRebuildUnreadResponse(
        String userId,
        Long totalUnread,
        String rebuiltAt
) {
}
