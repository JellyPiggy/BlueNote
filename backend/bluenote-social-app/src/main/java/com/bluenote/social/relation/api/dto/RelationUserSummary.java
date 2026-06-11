package com.bluenote.social.relation.api.dto;

public record RelationUserSummary(
        String userId,
        String bluenoteNo,
        String nickname,
        String avatarUrl,
        String bio,
        String userStatus,
        Long profileVersion
) {
}
