package com.bluenote.member;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberProbeController {

    @GetMapping("/internal/member/probe")
    public ApiResponse<Map<String, String>> probe() {
        return ApiResponse.success(Map.of(
                "app", "bluenote-member-app",
                "domains", "auth,user",
                "status", "UP"
        ), TraceIdHolder.currentOrNew());
    }
}
