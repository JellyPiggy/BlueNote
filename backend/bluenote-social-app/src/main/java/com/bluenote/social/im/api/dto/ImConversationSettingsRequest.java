package com.bluenote.social.im.api.dto;

public record ImConversationSettingsRequest(
        Boolean pinned,
        Boolean mute
) {
}
