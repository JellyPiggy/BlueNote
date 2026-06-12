package com.bluenote.order.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.order.api.dto.OrderDtos.ActivityCurrentResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderCancelResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderCouponListResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderDetailResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderPayRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderPayResponse;
import com.bluenote.order.api.dto.OrderDtos.SeckillResultResponse;
import com.bluenote.order.api.dto.OrderDtos.SeckillSubmitRequest;
import com.bluenote.order.api.dto.OrderDtos.SeckillSubmitResponse;
import com.bluenote.order.api.dto.OrderDtos.SeckillTokenRequest;
import com.bluenote.order.api.dto.OrderDtos.SeckillTokenResponse;
import com.bluenote.order.application.OrderApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @GetMapping("/coupon-activities/current")
    public ApiResponse<ActivityCurrentResponse> currentActivity() {
        return ApiResponse.success(
                orderApplicationService.currentActivity(requireUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/seckill/token")
    public ApiResponse<SeckillTokenResponse> token(@RequestBody SeckillTokenRequest request) {
        return ApiResponse.success(
                orderApplicationService.issueToken(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/seckill/orders")
    public ApiResponse<SeckillSubmitResponse> submit(@RequestBody SeckillSubmitRequest request) {
        return ApiResponse.success(
                orderApplicationService.submitSeckill(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/seckill/results/{requestId}")
    public ApiResponse<SeckillResultResponse> result(@PathVariable("requestId") String requestId) {
        return ApiResponse.success(
                orderApplicationService.seckillResult(requireUserId(), requestId),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> orderDetail(@PathVariable("orderId") String orderId) {
        return ApiResponse.success(
                orderApplicationService.orderDetail(requireUserId(), orderId),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/orders/{orderId}/pay")
    public ApiResponse<OrderPayResponse> pay(
            @PathVariable("orderId") String orderId,
            @RequestBody(required = false) OrderPayRequest request
    ) {
        return ApiResponse.success(
                orderApplicationService.pay(requireUserId(), orderId, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<OrderCancelResponse> cancel(@PathVariable("orderId") String orderId) {
        return ApiResponse.success(
                orderApplicationService.cancel(requireUserId(), orderId),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/my-coupons")
    public ApiResponse<OrderCouponListResponse> myCoupons(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                orderApplicationService.myCoupons(requireUserId(), status, cursor, pageSize),
                TraceIdHolder.currentOrNew()
        );
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }
}
