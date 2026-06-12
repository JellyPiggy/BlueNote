package com.bluenote.social.feed.api.dto;

public record FeedFanoutSubTaskResponse(
        String subTaskId,
        String subTaskStatus,
        String messageStatus,
        int targetCount,
        String progressUserId,
        int retryCount
) {
}
