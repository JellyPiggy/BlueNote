package com.bluenote.member.user.api.dto;

public record UserCountsResponse(
        long followingCount,
        long followerCount,
        long noteCount,
        long likedCount
) {
}
