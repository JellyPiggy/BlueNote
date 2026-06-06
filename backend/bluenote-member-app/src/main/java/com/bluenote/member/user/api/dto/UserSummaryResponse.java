package com.bluenote.member.user.api.dto;

public record UserSummaryResponse(
        String userId,
        String bluenoteNo,
        String nickname,
        String avatarUrl,
        String bio,
        String userStatus,
        long profileVersion
) {
}
