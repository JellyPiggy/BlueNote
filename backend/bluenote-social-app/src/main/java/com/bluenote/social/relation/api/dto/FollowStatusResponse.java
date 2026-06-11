package com.bluenote.social.relation.api.dto;

public record FollowStatusResponse(
        String currentUserId,
        String targetUserId,
        String followStatus,
        String followedAt
) {
}
