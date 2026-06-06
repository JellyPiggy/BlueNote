package com.bluenote.member.user.api.dto;

public record UserSummaryItem(
        String userId,
        String nickname,
        String avatarUrl,
        String bluenoteNo,
        String userStatus,
        Long profileVersion,
        String status
) {
}
