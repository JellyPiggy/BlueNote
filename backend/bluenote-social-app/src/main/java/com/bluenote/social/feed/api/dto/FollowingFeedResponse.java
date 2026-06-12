package com.bluenote.social.feed.api.dto;

import java.util.List;

public record FollowingFeedResponse(
        List<FeedCardResponse> items,
        String nextCursor,
        boolean hasMore,
        boolean degraded
) {
}
