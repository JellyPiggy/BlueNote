package com.bluenote.social.relation.api.dto;

public record FollowActionResponse(
        String followerId,
        String followeeId,
        String followStatus,
        String followedAt,
        String canceledAt
) {
}
