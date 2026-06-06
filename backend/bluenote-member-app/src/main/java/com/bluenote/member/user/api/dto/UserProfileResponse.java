package com.bluenote.member.user.api.dto;

public record UserProfileResponse(
        String userId,
        String bluenoteNo,
        String nickname,
        String avatarFileId,
        String avatarUrl,
        String bio,
        String gender,
        String birthday,
        String regionCode,
        String homeCoverFileId,
        String homeCoverUrl,
        String userStatus,
        long profileVersion
) {
}
