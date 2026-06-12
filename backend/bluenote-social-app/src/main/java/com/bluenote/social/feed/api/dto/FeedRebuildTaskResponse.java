package com.bluenote.social.feed.api.dto;

public record FeedRebuildTaskResponse(
        String taskId,
        String userId,
        String reason,
        String taskStatus,
        FeedTaskProgress progress
) {
}
