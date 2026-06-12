package com.bluenote.order.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.order.api.dto.OrderDtos.ActivityStatusResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalCreateActivityRequest;
import com.bluenote.order.application.OrderApplicationService;
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
}
