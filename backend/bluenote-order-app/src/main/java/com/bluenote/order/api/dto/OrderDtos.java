package com.bluenote.order.api.dto;

import java.util.List;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ActivityCurrentResponse(OrderActivityItem activity) {
    }

    public record OrderActivityItem(
            String activityId,
            String activityName,
            String templateName,
            Integer faceValue,
            Integer thresholdAmount,
            Integer payAmount,
            String status,
            String startAt,
            String endAt,
            Boolean userJoined,
            String description,
            String serverTime
    ) {
    }

    public record SeckillTokenRequest(String activityId) {
    }

    public record SeckillTokenResponse(String seckillToken, Integer expiresInSeconds) {
    }

    public record SeckillSubmitRequest(String activityId, String clientRequestId, String seckillToken) {
    }

    public record SeckillSubmitResponse(String requestId, String status, String message) {
    }

    public record SeckillResultResponse(
            String requestId,
            String status,
            String orderId,
            String userCouponId,
            Boolean payRequired,
            Integer payAmount,
            String expireAt,
            String message
    ) {
    }

    public record OrderDetailResponse(
            String orderId,
            String orderNo,
            String activityId,
            String activityName,
            String templateName,
            Integer faceValue,
            Integer thresholdAmount,
            Integer payAmount,
            String orderStatus,
            String expireAt,
            String paidAt,
            String successAt,
            String closedAt,
            String userCouponId,
            String createdAt
    ) {
    }

    public record OrderPayRequest(String channel) {
    }

    public record OrderPayResponse(String orderId, String orderStatus, Boolean paid, String userCouponId) {
    }

    public record OrderCancelResponse(String orderId, String orderStatus, Boolean cancelled) {
    }

    public record UserCouponItem(
            String userCouponId,
            String templateId,
            String templateName,
            String activityName,
            Integer faceValue,
            Integer thresholdAmount,
            String couponStatus,
            String validStartAt,
            String validEndAt,
            String issuedAt
    ) {
    }

    public record InternalCreateActivityRequest(
            String activityName,
            String templateName,
            Integer faceValue,
            Integer thresholdAmount,
            Integer validDays,
            Integer totalStock,
            Integer perUserLimit,
            Integer payAmount,
            String startAt,
            String endAt,
            Integer payTimeoutMinutes,
            String description
    ) {
    }

    public record InternalActivityResponse(String activityId, String templateId, String status) {
    }

    public record ActivityStatusResponse(String activityId, String status) {
    }

    public record OrderTimeoutScanResponse(Integer scannedCount, Integer closedCount, Integer failedCount) {
    }

    public record InternalActivityOpsSummaryResponse(
            String activityId,
            String activityName,
            String templateId,
            String templateName,
            String status,
            String displayStatus,
            Integer totalStock,
            Integer availableStock,
            Integer soldStock,
            Integer expectedAvailableStock,
            Integer expectedSoldStock,
            Boolean stockConsistent,
            OrderRedisSnapshot redis,
            Map<String, Integer> requestCounts,
            Map<String, Integer> orderCounts,
            Map<String, Integer> couponCounts,
            String serverTime
    ) {
    }

    public record OrderRedisSnapshot(
            Boolean stockKeyExists,
            Integer stock,
            Integer participantCount,
            Boolean soldOut,
            Boolean rebuilding
    ) {
    }

    public record OrderRedisRebuildResponse(
            String activityId,
            Integer mysqlAvailableStock,
            Integer participantCount,
            OrderRedisSnapshot before,
            OrderRedisSnapshot after,
            Boolean rebuilt
    ) {
    }

    public record OrderStockReconcileRequest(Boolean repair) {
    }

    public record OrderStockReconcileResponse(
            String activityId,
            Integer totalStock,
            Integer beforeAvailableStock,
            Integer beforeSoldStock,
            Integer expectedAvailableStock,
            Integer expectedSoldStock,
            Integer afterAvailableStock,
            Integer afterSoldStock,
            Boolean consistent,
            Boolean repaired,
            String message
    ) {
    }

    public record OrderSweepStuckRequest(Integer stuckSeconds, Integer limit) {
    }

    public record OrderSweepStuckResponse(
            Integer scannedCount,
            Integer retriedCount,
            Integer syncedCount,
            Integer skippedCount,
            List<String> requestIds
    ) {
    }

    public record OrderConsumeEventRequest(
            String topic,
            String consumerGroup,
            String eventId,
            String eventType,
            Integer eventVersion,
            String occurredAt,
            String traceId,
            String producer,
            String bizKey,
            Map<String, Object> payload,
            String envelopeJson
    ) {
    }

    public record OrderCouponListResponse(List<UserCouponItem> items, String nextCursor, boolean hasMore) {
    }
}
