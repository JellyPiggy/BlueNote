package com.bluenote.member.auth.api.external;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.member.auth.api.dto.ChangePasswordRequest;
import com.bluenote.member.auth.api.dto.LoginRequest;
import com.bluenote.member.auth.api.dto.LogoutRequest;
import com.bluenote.member.auth.api.dto.LogoutResponse;
import com.bluenote.member.auth.api.dto.RefreshTokenRequest;
import com.bluenote.member.auth.api.dto.RegisterRequest;
import com.bluenote.member.auth.api.dto.TokenPairResponse;
import com.bluenote.member.auth.application.AuthApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/register")
    public ApiResponse<TokenPairResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authApplicationService.register(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/login")
    public ApiResponse<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authApplicationService.login(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/token/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authApplicationService.refresh(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request) {
        return ApiResponse.success(authApplicationService.logout(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/password/change")
    public ApiResponse<TokenPairResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success(authApplicationService.changePassword(requireUserId(), request), TraceIdHolder.currentOrNew());
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }
}
