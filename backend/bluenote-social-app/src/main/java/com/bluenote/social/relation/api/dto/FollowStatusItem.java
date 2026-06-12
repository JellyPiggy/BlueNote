package com.bluenote.social.relation.api.dto;

public record FollowStatusItem(
        String targetUserId,
        String followStatus,
        String followedAt
) {
}
