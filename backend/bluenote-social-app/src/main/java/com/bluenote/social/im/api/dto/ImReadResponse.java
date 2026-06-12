package com.bluenote.social.im.api.dto;

public record ImReadResponse(
        String conversationId,
        Long lastReadSeq,
        Integer unreadCount,
        Long totalUnread
) {
}
