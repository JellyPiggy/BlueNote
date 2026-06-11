package com.bluenote.social.relation.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.social.relation.api.dto.BatchFollowStatusRequest;
import com.bluenote.social.relation.api.dto.BatchFollowStatusResponse;
import com.bluenote.social.relation.api.dto.FollowActionResponse;
import com.bluenote.social.relation.api.dto.FollowStatusResponse;
import com.bluenote.social.relation.api.dto.RelationCursorPage;
import com.bluenote.social.relation.api.dto.RelationUserItem;
import com.bluenote.social.relation.application.RelationApplicationService;
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
@RequestMapping("/api/relations")
public class RelationController {

    private final RelationApplicationService relationApplicationService;

    public RelationController(RelationApplicationService relationApplicationService) {
        this.relationApplicationService = relationApplicationService;
    }

    @PostMapping("/following/{followeeId}")
    public ApiResponse<FollowActionResponse> follow(@PathVariable("followeeId") String followeeId) {
        return ApiResponse.success(
                relationApplicationService.follow(requireUserId(), followeeId),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping("/following/{followeeId}")
    public ApiResponse<FollowActionResponse> unfollow(@PathVariable("followeeId") String followeeId) {
        return ApiResponse.success(
                relationApplicationService.unfollow(requireUserId(), followeeId),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/users/{userId}/following")
    public ApiResponse<RelationCursorPage<RelationUserItem>> following(
            @PathVariable("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                relationApplicationService.followingList(optionalUserId(), userId, cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/users/{userId}/followers")
    public ApiResponse<RelationCursorPage<RelationUserItem>> followers(
            @PathVariable("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                relationApplicationService.followersList(optionalUserId(), userId, cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/following/{targetUserId}/status")
    public ApiResponse<FollowStatusResponse> followStatus(@PathVariable("targetUserId") String targetUserId) {
        return ApiResponse.success(
                relationApplicationService.followStatus(requireUserId(), targetUserId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/following/status/batch")
    public ApiResponse<BatchFollowStatusResponse> batchStatus(@Valid @RequestBody BatchFollowStatusRequest request) {
        return ApiResponse.success(
                relationApplicationService.batchStatus(requireUserId(), request.targetUserIds()),
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

    private String optionalUserId() {
        UserContext userContext = UserContextHolder.current();
        return userContext == null || !userContext.authenticated() ? null : userContext.userId();
    }
}
