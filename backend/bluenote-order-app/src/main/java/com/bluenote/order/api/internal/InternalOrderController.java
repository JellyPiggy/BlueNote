package com.bluenote.order.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.order.api.dto.OrderDtos.ActivityStatusResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalCreateActivityRequest;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityOpsSummaryResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderRedisRebuildResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderStockReconcileRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderStockReconcileResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderSweepStuckRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderSweepStuckResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderTimeoutScanResponse;
import com.bluenote.order.application.OrderApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/order")
public class InternalOrderController {

    private final OrderApplicationService orderApplicationService;

    public InternalOrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @PostMapping("/coupon-activities")
    public ApiResponse<InternalActivityResponse> createActivity(@RequestBody InternalCreateActivityRequest request) {
        return ApiResponse.success(
                orderApplicationService.createActivity(request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/coupon-activities/{activityId}/preheat")
    public ApiResponse<ActivityStatusResponse> preheat(@PathVariable("activityId") String activityId) {
        return ApiResponse.success(
                orderApplicationService.preheat(activityId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/coupon-activities/{activityId}/pause")
    public ApiResponse<ActivityStatusResponse> pause(@PathVariable("activityId") String activityId) {
        return ApiResponse.success(
                orderApplicationService.updateActivityStatus(activityId, "pause"),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/coupon-activities/{activityId}/resume")
    public ApiResponse<ActivityStatusResponse> resume(@PathVariable("activityId") String activityId) {
        return ApiResponse.success(
                orderApplicationService.updateActivityStatus(activityId, "resume"),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/coupon-activities/{activityId}/end")
    public ApiResponse<ActivityStatusResponse> end(@PathVariable("activityId") String activityId) {
        return ApiResponse.success(
                orderApplicationService.updateActivityStatus(activityId, "end"),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/timeout-tasks/scan-once")
    public ApiResponse<OrderTimeoutScanResponse> scanTimeoutTasksOnce() {
        return ApiResponse.success(
                orderApplicationService.scanTimeoutTasksOnce(),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/coupon-activities/{activityId}/ops-summary")
    public ApiResponse<InternalActivityOpsSummaryResponse> opsSummary(@PathVariable("activityId") String activityId) {
        return ApiResponse.success(
                orderApplicationService.opsSummary(activityId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/coupon-activities/{activityId}/redis-rebuild")
    public ApiResponse<OrderRedisRebuildResponse> rebuildRedis(@PathVariable("activityId") String activityId) {
        return ApiResponse.success(
                orderApplicationService.rebuildRedis(activityId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/coupon-activities/{activityId}/stock-reconcile")
    public ApiResponse<OrderStockReconcileResponse> reconcileStock(
            @PathVariable("activityId") String activityId,
            @RequestBody(required = false) OrderStockReconcileRequest request
    ) {
        return ApiResponse.success(
                orderApplicationService.reconcileStock(activityId, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/seckill-requests/sweep-stuck")
    public ApiResponse<OrderSweepStuckResponse> sweepStuckRequests(@RequestBody(required = false) OrderSweepStuckRequest request) {
        return ApiResponse.success(
                orderApplicationService.sweepStuckRequests(request),
                TraceIdHolder.currentOrNew()
        );
    }
}
