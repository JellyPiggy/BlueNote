package com.bluenote.social.im.api.dto;

public record ImUserSummary(
        String userId,
        String nickname,
        String avatarUrl
) {
}
