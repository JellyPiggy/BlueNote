package com.bluenote.member.user.api.dto;

public record UserHomeResponse(
        UserSummaryResponse user,
        UserCountsResponse counts,
        UserRelationResponse relation,
        boolean degraded
) {
}
