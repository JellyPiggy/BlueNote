package com.bluenote.member.user.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.member.user.api.dto.UpdateProfileRequest;
import com.bluenote.member.user.api.dto.UpdateProfileResponse;
import com.bluenote.member.user.api.dto.UserHomeResponse;
import com.bluenote.member.user.api.dto.UserProfileResponse;
import com.bluenote.member.user.api.dto.UserSummaryResponse;
import com.bluenote.member.user.application.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userApplicationService;

    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.success(
                userApplicationService.currentUserProfile(requireUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @PutMapping("/me/profile")
    public ApiResponse<UpdateProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(
                userApplicationService.updateProfile(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/{userId}/public")
    public ApiResponse<UserSummaryResponse> publicProfile(@PathVariable String userId) {
        return ApiResponse.success(userApplicationService.publicProfile(userId), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/{userId}/home")
    public ApiResponse<UserHomeResponse> home(@PathVariable String userId) {
        return ApiResponse.success(userApplicationService.home(userId), TraceIdHolder.currentOrNew());
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }
}
