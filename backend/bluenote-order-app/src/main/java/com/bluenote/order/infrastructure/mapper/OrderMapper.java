package com.bluenote.order.infrastructure.mapper;

import com.bluenote.order.infrastructure.entity.OrderEntities.CouponActivity;
import com.bluenote.order.infrastructure.entity.OrderEntities.CouponTemplate;
import com.bluenote.order.infrastructure.entity.OrderEntities.OrderConsumeRecord;
import com.bluenote.order.infrastructure.entity.OrderEntities.OrderOutboxEvent;
import com.bluenote.order.infrastructure.entity.OrderEntities.OrderTimeoutTask;
import com.bluenote.order.infrastructure.entity.OrderEntities.PaymentRecord;
import com.bluenote.order.infrastructure.entity.OrderEntities.SeckillRequest;
import com.bluenote.order.infrastructure.entity.OrderEntities.UserCoupon;
import com.bluenote.order.infrastructure.entity.OrderEntities.VoucherOrder;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OrderMapper {

    int insertTemplate(CouponTemplate entity);

    int insertActivity(CouponActivity entity);

    CouponTemplate selectTemplateById(@Param("templateId") Long templateId);

    CouponActivity selectCurrentActivity(@Param("now") LocalDateTime now);

    List<CouponActivity> selectActivities(
            @Param("status") String status,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    CouponActivity selectActivityById(@Param("activityId") Long activityId);

    CouponActivity selectActivityByIdForUpdate(@Param("activityId") Long activityId);

    List<StatusCountRow> countRequestsByActivity(@Param("activityId") Long activityId);

    List<StatusCountRow> countOrdersByActivity(@Param("activityId") Long activityId);

    List<StatusCountRow> countCouponsByActivity(@Param("activityId") Long activityId);

    List<Long> selectActiveParticipantUserIds(@Param("activityId") Long activityId);

    Integer countSuccessfulOrdersByActivity(@Param("activityId") Long activityId);

    List<SeckillRequest> selectStuckProcessingRequests(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    int updateActivityStatus(
            @Param("activityId") Long activityId,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("allowedStatuses") List<String> allowedStatuses
    );

    int repairActivityStock(
            @Param("activityId") Long activityId,
            @Param("availableStock") Integer availableStock,
            @Param("soldStock") Integer soldStock,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int adjustActivityStock(
            @Param("activityId") Long activityId,
            @Param("deltaStock") Integer deltaStock,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markActivityPreheated(@Param("activityId") Long activityId, @Param("preheatedAt") LocalDateTime preheatedAt);

    int deductActivityStock(@Param("activityId") Long activityId, @Param("updatedAt") LocalDateTime updatedAt);

    int replenishActivityStock(@Param("activityId") Long activityId, @Param("updatedAt") LocalDateTime updatedAt);

    int markActivitySoldOut(@Param("activityId") Long activityId, @Param("updatedAt") LocalDateTime updatedAt);

    int insertSeckillRequest(SeckillRequest entity);

    SeckillRequest selectRequestByUserClient(@Param("userId") Long userId, @Param("clientRequestId") String clientRequestId);

    SeckillRequest selectRequestByUserActivity(@Param("userId") Long userId, @Param("activityId") Long activityId);

    SeckillRequest selectRequestById(@Param("requestId") Long requestId);

    SeckillRequest selectRequestByIdForUpdate(@Param("requestId") Long requestId);

    int updateRequestResult(
            @Param("requestId") Long requestId,
            @Param("status") String status,
            @Param("orderId") Long orderId,
            @Param("userCouponId") Long userCouponId,
            @Param("payRequired") Integer payRequired,
            @Param("payAmount") Integer payAmount,
            @Param("expireAt") LocalDateTime expireAt,
            @Param("message") String message,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int insertOrder(VoucherOrder entity);

    VoucherOrder selectOrderById(@Param("orderId") Long orderId);

    VoucherOrder selectOrderByIdForUpdate(@Param("orderId") Long orderId);

    VoucherOrder selectOrderByUserActivity(@Param("userId") Long userId, @Param("activityId") Long activityId);

    int updateOrderStatusCas(
            @Param("orderId") Long orderId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("successAt") LocalDateTime successAt,
            @Param("closedAt") LocalDateTime closedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int insertPayment(PaymentRecord entity);

    int insertUserCoupon(UserCoupon entity);

    UserCoupon selectCouponBySourceOrder(@Param("sourceOrderId") Long sourceOrderId);

    List<UserCoupon> selectUserCoupons(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("cursorIssuedAt") LocalDateTime cursorIssuedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    int insertStockLog(
            @Param("id") Long id,
            @Param("activityId") Long activityId,
            @Param("orderId") Long orderId,
            @Param("requestId") Long requestId,
            @Param("changeType") String changeType,
            @Param("changeAmount") Integer changeAmount,
            @Param("beforeStock") Integer beforeStock,
            @Param("afterStock") Integer afterStock,
            @Param("operatorType") String operatorType,
            @Param("operatorId") String operatorId,
            @Param("reason") String reason,
            @Param("createdAt") LocalDateTime createdAt
    );

    int insertStatusLog(
            @Param("id") Long id,
            @Param("orderId") Long orderId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("reason") String reason,
            @Param("operatorType") String operatorType,
            @Param("operatorId") String operatorId,
            @Param("createdAt") LocalDateTime createdAt
    );

    int insertTimeoutTask(OrderTimeoutTask entity);

    List<OrderTimeoutTask> selectDueTimeoutTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int updateTimeoutTaskStatus(
            @Param("orderId") Long orderId,
            @Param("status") String status,
            @Param("executedAt") LocalDateTime executedAt,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    OrderConsumeRecord selectConsumeRecord(@Param("consumerGroup") String consumerGroup, @Param("eventId") String eventId);

    int insertConsumeRecord(OrderConsumeRecord entity);

    int updateConsumeRecordSuccess(@Param("id") Long id, @Param("consumedAt") LocalDateTime consumedAt, @Param("updatedAt") LocalDateTime updatedAt);

    int insertOutbox(OrderOutboxEvent entity);

    class StatusCountRow {
        private String status;
        private Integer count;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
