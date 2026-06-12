package com.bluenote.social.counter.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterBatchResponse;
import com.bluenote.social.counter.application.CounterApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/counters")
public class InternalCounterController {

    private final CounterApplicationService counterApplicationService;

    public InternalCounterController(CounterApplicationService counterApplicationService) {
        this.counterApplicationService = counterApplicationService;
    }

    @PostMapping("/batch")
    public ApiResponse<CounterBatchResponse> batch(@Valid @RequestBody CounterBatchRequest request) {
        return ApiResponse.success(counterApplicationService.batch(request), TraceIdHolder.currentOrNew());
    }
}
