package com.bluenote.social.feed.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.feed.api.dto.FeedFanoutTaskResponse;
import com.bluenote.social.feed.api.dto.FeedRebuildRequest;
import com.bluenote.social.feed.api.dto.FeedRebuildResponse;
import com.bluenote.social.feed.api.dto.FeedRebuildTaskResponse;
import com.bluenote.social.feed.application.FeedApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/feed")
public class InternalFeedController {

    private final FeedApplicationService feedApplicationService;

    public InternalFeedController(FeedApplicationService feedApplicationService) {
        this.feedApplicationService = feedApplicationService;
    }

    @PostMapping("/users/{userId}/rebuild")
    public ApiResponse<FeedRebuildResponse> rebuildUser(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) FeedRebuildRequest request
    ) {
        return ApiResponse.success(feedApplicationService.rebuildUser(userId, request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/rebuild-tasks/{taskId}")
    public ApiResponse<FeedRebuildTaskResponse> rebuildTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(feedApplicationService.rebuildTask(taskId), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/fanout-tasks/{taskId}")
    public ApiResponse<FeedFanoutTaskResponse> fanoutTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(feedApplicationService.fanoutTask(taskId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/fanout-tasks/{taskId}/retry")
    public ApiResponse<FeedFanoutTaskResponse> retryFanoutTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(feedApplicationService.retryFanoutTask(taskId), TraceIdHolder.currentOrNew());
    }
}
