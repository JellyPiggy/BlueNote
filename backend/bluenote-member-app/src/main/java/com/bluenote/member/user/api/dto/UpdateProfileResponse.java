package com.bluenote.member.user.api.dto;

public record UpdateProfileResponse(
        String userId,
        long profileVersion,
        String updatedAt
) {
}
