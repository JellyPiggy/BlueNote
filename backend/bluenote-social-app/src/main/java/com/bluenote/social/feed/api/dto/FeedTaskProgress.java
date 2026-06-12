package com.bluenote.social.feed.api.dto;

public record FeedTaskProgress(
        int total,
        int success,
        int failed
) {
}
