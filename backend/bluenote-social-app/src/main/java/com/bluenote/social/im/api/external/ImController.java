package com.bluenote.social.im.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.social.im.api.dto.ImConversationDeleteResponse;
import com.bluenote.social.im.api.dto.ImConversationItem;
import com.bluenote.social.im.api.dto.ImConversationListResponse;
import com.bluenote.social.im.api.dto.ImConversationSettingsRequest;
import com.bluenote.social.im.api.dto.ImMessageListResponse;
import com.bluenote.social.im.api.dto.ImReadRequest;
import com.bluenote.social.im.api.dto.ImReadResponse;
import com.bluenote.social.im.api.dto.ImReceivedRequest;
import com.bluenote.social.im.api.dto.ImReceivedResponse;
import com.bluenote.social.im.api.dto.ImSendMessageRequest;
import com.bluenote.social.im.api.dto.ImSendMessageResponse;
import com.bluenote.social.im.api.dto.ImSingleConversationRequest;
import com.bluenote.social.im.api.dto.ImUnreadCountResponse;
import com.bluenote.social.im.application.ImApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/im")
public class ImController {

    private final ImApplicationService imApplicationService;

    public ImController(ImApplicationService imApplicationService) {
        this.imApplicationService = imApplicationService;
    }

    @PostMapping("/conversations/single")
    public ApiResponse<ImConversationItem> singleConversation(@RequestBody ImSingleConversationRequest request) {
        return ApiResponse.success(
                imApplicationService.singleConversation(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/conversations")
    public ApiResponse<ImConversationListResponse> conversations(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                imApplicationService.conversations(requireUserId(), cursor, pageSize),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/messages")
    public ApiResponse<ImSendMessageResponse> sendMessage(@RequestBody ImSendMessageRequest request) {
        return ApiResponse.success(
                imApplicationService.sendMessage(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<ImMessageListResponse> messages(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(value = "afterSeq", required = false) Long afterSeq,
            @RequestParam(value = "beforeSeq", required = false) Long beforeSeq,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(
                imApplicationService.messages(requireUserId(), conversationId, afterSeq, beforeSeq, limit),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/conversations/{conversationId}/received")
    public ApiResponse<ImReceivedResponse> received(
            @PathVariable("conversationId") String conversationId,
            @RequestBody(required = false) ImReceivedRequest request
    ) {
        return ApiResponse.success(
                imApplicationService.markReceived(requireUserId(), conversationId, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ApiResponse<ImReadResponse> read(
            @PathVariable("conversationId") String conversationId,
            @RequestBody(required = false) ImReadRequest request
    ) {
        return ApiResponse.success(
                imApplicationService.markRead(requireUserId(), conversationId, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/unread-count")
    public ApiResponse<ImUnreadCountResponse> unreadCount() {
        return ApiResponse.success(
                imApplicationService.unreadCount(requireUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @PutMapping("/conversations/{conversationId}/settings")
    public ApiResponse<ImConversationItem> updateSettings(
            @PathVariable("conversationId") String conversationId,
            @RequestBody(required = false) ImConversationSettingsRequest request
    ) {
        return ApiResponse.success(
                imApplicationService.updateSettings(requireUserId(), conversationId, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<ImConversationDeleteResponse> deleteConversation(@PathVariable("conversationId") String conversationId) {
        return ApiResponse.success(
                imApplicationService.deleteConversation(requireUserId(), conversationId),
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
