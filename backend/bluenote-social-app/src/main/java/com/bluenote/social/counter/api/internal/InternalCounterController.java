package com.bluenote.social.counter.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterBatchResponse;
import com.bluenote.social.counter.api.dto.CounterConsumeEventRequest;
import com.bluenote.social.counter.api.dto.CounterConsumeEventResponse;
import com.bluenote.social.counter.api.dto.CounterRebuildTaskResponse;
import com.bluenote.social.counter.api.dto.CounterReconcileRequest;
import com.bluenote.social.counter.api.dto.CounterReconcileResponse;
import com.bluenote.social.counter.api.dto.CounterWarmupRequest;
import com.bluenote.social.counter.api.dto.CounterWarmupResponse;
import com.bluenote.social.counter.application.CounterApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/events/consume")
    public ApiResponse<CounterConsumeEventResponse> consumeEvent(@Valid @RequestBody CounterConsumeEventRequest request) {
        return ApiResponse.success(counterApplicationService.consumeEvent(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/warmup")
    public ApiResponse<CounterWarmupResponse> warmup(@Valid @RequestBody CounterWarmupRequest request) {
        return ApiResponse.success(counterApplicationService.warmup(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/reconcile")
    public ApiResponse<CounterReconcileResponse> reconcile(@Valid @RequestBody CounterReconcileRequest request) {
        return ApiResponse.success(counterApplicationService.reconcile(request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/rebuild-tasks/{taskId}")
    public ApiResponse<CounterRebuildTaskResponse> rebuildTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(counterApplicationService.rebuildTask(taskId), TraceIdHolder.currentOrNew());
    }
}
