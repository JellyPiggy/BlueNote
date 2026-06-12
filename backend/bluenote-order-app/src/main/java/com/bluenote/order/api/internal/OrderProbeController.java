package com.bluenote.order.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/order")
public class OrderProbeController {

    @GetMapping("/probe")
    public ApiResponse<Map<String, String>> probe() {
        return ApiResponse.success(Map.of("status", "ok", "service", "bluenote-order-app"), TraceIdHolder.currentOrNew());
    }
}
