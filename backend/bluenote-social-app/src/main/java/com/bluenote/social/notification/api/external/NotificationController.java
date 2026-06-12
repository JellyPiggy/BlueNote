package com.bluenote.social.notification.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.social.notification.api.dto.NotificationDeleteBatchRequest;
import com.bluenote.social.notification.api.dto.NotificationDeleteBatchResponse;
import com.bluenote.social.notification.api.dto.NotificationDeleteResponse;
import com.bluenote.social.notification.api.dto.NotificationDetailResponse;
import com.bluenote.social.notification.api.dto.NotificationListResponse;
import com.bluenote.social.notification.api.dto.NotificationReadAllRequest;
import com.bluenote.social.notification.api.dto.NotificationReadAllResponse;
import com.bluenote.social.notification.api.dto.NotificationReadResponse;
import com.bluenote.social.notification.api.dto.NotificationUnreadCountResponse;
import com.bluenote.social.notification.application.NotificationApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationApplicationService notificationApplicationService;

    public NotificationController(NotificationApplicationService notificationApplicationService) {
        this.notificationApplicationService = notificationApplicationService;
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> unreadCount() {
        return ApiResponse.success(
                notificationApplicationService.unreadCount(requireUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                notificationApplicationService.list(requireUserId(), category, cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/{notificationId}")
    public ApiResponse<NotificationDetailResponse> detail(@PathVariable("notificationId") String notificationId) {
        return ApiResponse.success(
                notificationApplicationService.detail(requireUserId(), notificationId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationReadResponse> markRead(@PathVariable("notificationId") String notificationId) {
        return ApiResponse.success(
                notificationApplicationService.markRead(requireUserId(), notificationId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> markReadAll(
            @RequestBody(required = false) NotificationReadAllRequest request
    ) {
        return ApiResponse.success(
                notificationApplicationService.markReadAll(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<NotificationDeleteResponse> delete(@PathVariable("notificationId") String notificationId) {
        return ApiResponse.success(
                notificationApplicationService.delete(requireUserId(), notificationId),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping
    public ApiResponse<NotificationDeleteBatchResponse> deleteBatch(
            @Valid @RequestBody NotificationDeleteBatchRequest request
    ) {
        return ApiResponse.success(
                notificationApplicationService.deleteBatch(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }
}
