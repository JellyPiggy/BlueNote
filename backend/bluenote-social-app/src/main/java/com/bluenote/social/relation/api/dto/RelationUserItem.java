package com.bluenote.social.relation.api.dto;

public record RelationUserItem(
        RelationUserSummary user,
        String followStatus,
        String followedAt
) {
}
