package com.bluenote.social.im.api.dto;

public record ImBatchSummaryItem(
        String conversationId,
        String conversationType,
        Long lastConversationSeq,
        ImMessageItem lastMessage,
        String status
) {
}
