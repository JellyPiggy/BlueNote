package com.bluenote.social.feed.api.dto;

import java.util.List;

public record FeedFanoutTaskResponse(
        String taskId,
        String noteId,
        String authorId,
        String taskStatus,
        int targetCount,
        int successCount,
        int failedCount,
        List<FeedFanoutSubTaskResponse> subTasks
) {
}
