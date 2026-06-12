package com.bluenote.social.feed.api.dto;

public record FeedCountsResponse(
        long likeCount,
        long collectCount,
        long commentCount
) {
}
