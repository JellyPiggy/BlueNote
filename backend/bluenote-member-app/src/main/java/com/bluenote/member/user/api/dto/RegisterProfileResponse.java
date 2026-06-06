package com.bluenote.member.user.api.dto;

public record RegisterProfileResponse(
        String userId,
        String bluenoteNo,
        String nickname,
        String userStatus,
        long profileVersion
) {
}
