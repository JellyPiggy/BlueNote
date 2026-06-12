package com.bluenote.social.im.api.dto;

public record ImConversationItem(
        String conversationId,
        String conversationType,
        ImUserSummary peerUser,
        ImMessageItem lastMessage,
        Long lastConversationSeq,
        Long lastReadSeq,
        Long lastReceivedSeq,
        Integer unreadCount,
        Boolean pinned,
        Boolean mute,
        Boolean hidden,
        String updatedAt
) {
}
