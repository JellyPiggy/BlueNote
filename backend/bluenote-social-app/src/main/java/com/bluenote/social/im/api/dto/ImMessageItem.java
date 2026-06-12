package com.bluenote.social.im.api.dto;

import java.util.Map;

public record ImMessageItem(
        String messageId,
        String conversationId,
        Long conversationSeq,
        String senderId,
        String receiverId,
        String messageType,
        Map<String, Object> content,
        String summary,
        String messageStatus,
        Boolean mine,
        String sentAt
) {
}
