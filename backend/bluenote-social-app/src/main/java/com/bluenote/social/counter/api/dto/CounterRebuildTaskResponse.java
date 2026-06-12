package com.bluenote.social.counter.api.dto;

public record CounterRebuildTaskResponse(
        String taskId,
        String taskType,
        String targetType,
        String targetId,
        String taskStatus,
        CounterRebuildProgress progress
) {
}
