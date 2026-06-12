package com.bluenote.social;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SocialProbeController {

    @GetMapping("/api/social/probe")
    public ApiResponse<Map<String, String>> probe() {
        return ApiResponse.success(Map.of("status", "ok", "app", "bluenote-social-app"), TraceIdHolder.currentOrNew());
    }
}
