package com.bluenote.social.feed.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.social.feed.api.dto.FollowingFeedResponse;
import com.bluenote.social.feed.application.FeedApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedApplicationService feedApplicationService;

    public FeedController(FeedApplicationService feedApplicationService) {
        this.feedApplicationService = feedApplicationService;
    }

    @GetMapping("/following")
    public ApiResponse<FollowingFeedResponse> following(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                feedApplicationService.followingFeed(requireUserId(), cursor, size),
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
