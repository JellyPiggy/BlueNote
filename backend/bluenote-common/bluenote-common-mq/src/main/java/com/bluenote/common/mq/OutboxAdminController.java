package com.bluenote.common.mq;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mq/outbox")
public class OutboxAdminController {

    private final OutboxDispatcher outboxDispatcher;

    public OutboxAdminController(OutboxDispatcher outboxDispatcher) {
        this.outboxDispatcher = outboxDispatcher;
    }

    @GetMapping("/stats")
    public ApiResponse<List<OutboxTableStats>> stats() {
        return ApiResponse.success(outboxDispatcher.stats(), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/dispatch-once")
    public ApiResponse<OutboxDispatchResponse> dispatchOnce() {
        return ApiResponse.success(new OutboxDispatchResponse(outboxDispatcher.dispatchOnce()), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/events/retry")
    public ApiResponse<OutboxRetryResponse> retry(@RequestBody OutboxRetryRequest request) {
        boolean retried = outboxDispatcher.retry(request.tableName(), request.eventId());
        return ApiResponse.success(new OutboxRetryResponse(request.tableName(), request.eventId(), retried), TraceIdHolder.currentOrNew());
    }
}
