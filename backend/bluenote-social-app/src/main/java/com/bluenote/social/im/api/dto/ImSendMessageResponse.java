package com.bluenote.social.im.api.dto;

public record ImSendMessageResponse(
        ImMessageItem message,
        ImConversationItem conversation
) {
}
