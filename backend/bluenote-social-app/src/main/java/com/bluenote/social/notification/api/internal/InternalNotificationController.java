package com.bluenote.social.notification.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.notification.api.dto.NotificationBatchSummaryRequest;
import com.bluenote.social.notification.api.dto.NotificationBatchSummaryResponse;
import com.bluenote.social.notification.api.dto.NotificationConsumeEventResponse;
import com.bluenote.social.notification.api.dto.NotificationRebuildUnreadResponse;
import com.bluenote.social.notification.api.dto.NotificationReplayRequest;
import com.bluenote.social.notification.api.dto.SystemNotificationRequest;
import com.bluenote.social.notification.api.dto.SystemNotificationResponse;
import com.bluenote.social.notification.application.NotificationApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationApplicationService notificationApplicationService;

    public InternalNotificationController(NotificationApplicationService notificationApplicationService) {
        this.notificationApplicationService = notificationApplicationService;
    }

    @PostMapping("/system")
    public ApiResponse<SystemNotificationResponse> system(@Valid @RequestBody SystemNotificationRequest request) {
        return ApiResponse.success(notificationApplicationService.createSystemNotification(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/batch-summary")
    public ApiResponse<NotificationBatchSummaryResponse> batchSummary(
            @Valid @RequestBody NotificationBatchSummaryRequest request
    ) {
        return ApiResponse.success(notificationApplicationService.batchSummary(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/users/{userId}/rebuild-unread")
    public ApiResponse<NotificationRebuildUnreadResponse> rebuildUnread(@PathVariable("userId") String userId) {
        return ApiResponse.success(notificationApplicationService.rebuildUnread(userId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/events/replay")
    public ApiResponse<NotificationConsumeEventResponse> replay(@Valid @RequestBody NotificationReplayRequest request) {
        return ApiResponse.success(notificationApplicationService.replay(request), TraceIdHolder.currentOrNew());
    }
}
