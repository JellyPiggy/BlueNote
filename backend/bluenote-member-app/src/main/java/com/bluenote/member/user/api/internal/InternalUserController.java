package com.bluenote.member.user.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.member.user.api.dto.BatchUserSummaryRequest;
import com.bluenote.member.user.api.dto.BatchUserSummaryResponse;
import com.bluenote.member.user.api.dto.RegisterProfileRequest;
import com.bluenote.member.user.api.dto.RegisterProfileResponse;
import com.bluenote.member.user.api.dto.StatusCheckRequest;
import com.bluenote.member.user.api.dto.StatusCheckResponse;
import com.bluenote.member.user.application.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserApplicationService userApplicationService;

    public InternalUserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @PostMapping("/register-profile")
    public ApiResponse<RegisterProfileResponse> registerProfile(@Valid @RequestBody RegisterProfileRequest request) {
        return ApiResponse.success(userApplicationService.registerProfile(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/batch-summary")
    public ApiResponse<BatchUserSummaryResponse> batchSummary(@Valid @RequestBody BatchUserSummaryRequest request) {
        return ApiResponse.success(userApplicationService.batchSummary(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/status-check")
    public ApiResponse<StatusCheckResponse> statusCheck(@Valid @RequestBody StatusCheckRequest request) {
        return ApiResponse.success(userApplicationService.statusCheck(request), TraceIdHolder.currentOrNew());
    }
}
