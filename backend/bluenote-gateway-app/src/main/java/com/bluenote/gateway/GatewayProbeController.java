package com.bluenote.gateway;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdGenerator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class GatewayProbeController {

    @GetMapping("/internal/gateway/probe")
    public Mono<ApiResponse<Map<String, String>>> probe(
            @RequestHeader(name = TraceConstants.TRACE_ID_HEADER, required = false) String traceId
    ) {
        String currentTraceId = traceId == null || traceId.isBlank() ? TraceIdGenerator.generate() : traceId;
        return Mono.just(ApiResponse.success(Map.of(
                "app", "bluenote-gateway-app",
                "status", "UP"
        ), currentTraceId));
    }
}
