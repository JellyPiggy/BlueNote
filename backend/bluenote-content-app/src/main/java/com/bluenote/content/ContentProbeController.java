package com.bluenote.content;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentProbeController {

    @GetMapping("/internal/content/probe")
    public ApiResponse<Map<String, String>> probe() {
        return ApiResponse.success(Map.of(
                "app", "bluenote-content-app",
                "domains", "file,note,comment",
                "status", "UP"
        ), TraceIdHolder.currentOrNew());
    }
}
