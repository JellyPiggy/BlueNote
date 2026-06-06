package com.bluenote.member.user.api.dto;

public record StatusCheckItem(
        String userId,
        boolean exists,
        String userStatus,
        boolean allowed,
        String reason
) {
}
