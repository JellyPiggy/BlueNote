package com.bluenote.social.feed.api.dto;

import com.bluenote.social.relation.api.dto.RelationUserSummary;

public record FeedCardResponse(
        String feedId,
        String noteId,
        RelationUserSummary author,
        String title,
        String contentPreview,
        String coverUrl,
        String noteType,
        FeedCountsResponse counts,
        Object viewerAction,
        String publishedAt,
        String sourceType,
        boolean degraded
) {
}
