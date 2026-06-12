package com.bluenote.social.im.api.dto;

import java.util.Map;

public record ImSendMessageRequest(
        String conversationId,
        String targetUserId,
        String clientMsgId,
        String messageType,
        Map<String, Object> content
) {
}
