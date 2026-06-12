package com.bluenote.social.im.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.im.api.dto.ImBatchSummaryRequest;
import com.bluenote.social.im.api.dto.ImBatchSummaryResponse;
import com.bluenote.social.im.api.dto.ImPushPolicyResponse;
import com.bluenote.social.im.api.dto.ImRebuildUnreadResponse;
import com.bluenote.social.im.application.ImApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/im")
public class InternalImController {

    private final ImApplicationService imApplicationService;

    public InternalImController(ImApplicationService imApplicationService) {
        this.imApplicationService = imApplicationService;
    }

    @PostMapping("/conversations/batch-summary")
    public ApiResponse<ImBatchSummaryResponse> batchSummary(@RequestBody ImBatchSummaryRequest request) {
        return ApiResponse.success(imApplicationService.batchSummary(request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/conversations/{conversationId}/members/{userId}/push-policy")
    public ApiResponse<ImPushPolicyResponse> pushPolicy(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("userId") String userId
    ) {
        return ApiResponse.success(imApplicationService.pushPolicy(conversationId, userId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/users/{userId}/rebuild-unread")
    public ApiResponse<ImRebuildUnreadResponse> rebuildUnread(@PathVariable("userId") String userId) {
        return ApiResponse.success(imApplicationService.rebuildUnread(userId), TraceIdHolder.currentOrNew());
    }
}
