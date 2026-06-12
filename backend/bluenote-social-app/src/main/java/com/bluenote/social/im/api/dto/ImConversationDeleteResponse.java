package com.bluenote.social.im.api.dto;

public record ImConversationDeleteResponse(
        String conversationId,
        Boolean deleted
) {
}
