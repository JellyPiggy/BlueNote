package com.bluenote.social.push.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.push.api.dto.PushConsumeEventResponse;
import com.bluenote.social.push.api.dto.PushKickRequest;
import com.bluenote.social.push.api.dto.PushKickResponse;
import com.bluenote.social.push.api.dto.PushOnlineStateResponse;
import com.bluenote.social.push.api.dto.PushReplayRequest;
import com.bluenote.social.push.api.dto.PushRequestDetailResponse;
import com.bluenote.social.push.api.dto.PushSendRequest;
import com.bluenote.social.push.api.dto.PushSendResponse;
import com.bluenote.social.push.application.PushApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/push")
public class InternalPushController {

    private final PushApplicationService pushApplicationService;

    public InternalPushController(PushApplicationService pushApplicationService) {
        this.pushApplicationService = pushApplicationService;
    }

    @PostMapping("/requests/send")
    public ApiResponse<PushSendResponse> send(@RequestBody PushSendRequest request) {
        return ApiResponse.success(pushApplicationService.send(request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/requests/{requestId}")
    public ApiResponse<PushRequestDetailResponse> requestDetail(@PathVariable("requestId") String requestId) {
        return ApiResponse.success(pushApplicationService.requestDetail(requestId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/requests/{requestId}/retry")
    public ApiResponse<PushSendResponse> retry(@PathVariable("requestId") String requestId) {
        return ApiResponse.success(pushApplicationService.retry(requestId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/events/replay")
    public ApiResponse<PushConsumeEventResponse> replay(@RequestBody PushReplayRequest request) {
        return ApiResponse.success(pushApplicationService.replay(request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/users/{userId}/online-state")
    public ApiResponse<PushOnlineStateResponse> onlineState(@PathVariable("userId") String userId) {
        return ApiResponse.success(pushApplicationService.onlineState(userId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/users/{userId}/kick")
    public ApiResponse<PushKickResponse> kick(@PathVariable("userId") String userId, @RequestBody PushKickRequest request) {
        return ApiResponse.success(pushApplicationService.kickUserDevice(userId, request), TraceIdHolder.currentOrNew());
    }
}
