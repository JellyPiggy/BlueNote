package com.bluenote.social.push.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.social.push.api.dto.PushClickRequest;
import com.bluenote.social.push.api.dto.PushClickResponse;
import com.bluenote.social.push.api.dto.PushDeviceListResponse;
import com.bluenote.social.push.api.dto.PushDeviceRegisterRequest;
import com.bluenote.social.push.api.dto.PushDeviceRegisterResponse;
import com.bluenote.social.push.api.dto.PushDeviceUnbindResponse;
import com.bluenote.social.push.api.dto.PushPreferenceResponse;
import com.bluenote.social.push.api.dto.PushPreferenceUpdateRequest;
import com.bluenote.social.push.application.PushApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushApplicationService pushApplicationService;

    public PushController(PushApplicationService pushApplicationService) {
        this.pushApplicationService = pushApplicationService;
    }

    @PostMapping("/devices/register")
    public ApiResponse<PushDeviceRegisterResponse> registerDevice(@RequestBody PushDeviceRegisterRequest request) {
        return ApiResponse.success(pushApplicationService.registerDevice(requireUserId(), request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/devices")
    public ApiResponse<PushDeviceListResponse> devices() {
        return ApiResponse.success(pushApplicationService.devices(requireUserId()), TraceIdHolder.currentOrNew());
    }

    @DeleteMapping("/devices/{deviceId}")
    public ApiResponse<PushDeviceUnbindResponse> unbindDevice(@PathVariable("deviceId") String deviceId) {
        return ApiResponse.success(pushApplicationService.unbindDevice(requireUserId(), deviceId), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/preferences")
    public ApiResponse<PushPreferenceResponse> preference() {
        return ApiResponse.success(pushApplicationService.preference(requireUserId()), TraceIdHolder.currentOrNew());
    }

    @PutMapping("/preferences")
    public ApiResponse<PushPreferenceResponse> updatePreference(@RequestBody PushPreferenceUpdateRequest request) {
        return ApiResponse.success(pushApplicationService.updatePreference(requireUserId(), request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/clicks")
    public ApiResponse<PushClickResponse> recordClick(@RequestBody PushClickRequest request) {
        return ApiResponse.success(pushApplicationService.recordClick(requireUserId(), request), TraceIdHolder.currentOrNew());
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }
}
