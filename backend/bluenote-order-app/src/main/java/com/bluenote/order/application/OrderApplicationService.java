package com.bluenote.order.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.order.api.dto.OrderDtos.ActivityCurrentResponse;
import com.bluenote.order.api.dto.OrderDtos.ActivityStatusResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityListItem;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityListResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalActivityOpsSummaryResponse;
import com.bluenote.order.api.dto.OrderDtos.InternalCreateActivityRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderActivityPrecheckResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderActivityItem;
import com.bluenote.order.api.dto.OrderDtos.OrderCancelResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderConsumeEventRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderCouponListResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderDetailResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderPayRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderPayResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderRedisRebuildResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderRedisSnapshot;
import com.bluenote.order.api.dto.OrderDtos.OrderStockAdjustRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderStockAdjustResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderStockReconcileRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderStockReconcileResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderSweepStuckRequest;
import com.bluenote.order.api.dto.OrderDtos.OrderSweepStuckResponse;
import com.bluenote.order.api.dto.OrderDtos.OrderTimeoutScanResponse;
import com.bluenote.order.api.dto.OrderDtos.SeckillResultResponse;
import com.bluenote.order.api.dto.OrderDtos.SeckillSubmitRequest;
import com.bluenote.order.api.dto.OrderDtos.SeckillSubmitResponse;
import com.bluenote.order.api.dto.OrderDtos.SeckillTokenRequest;
import com.bluenote.order.api.dto.OrderDtos.SeckillTokenResponse;
import com.bluenote.order.api.dto.OrderDtos.UserCouponItem;
import com.bluenote.order.common.OrderIdGenerator;
import com.bluenote.order.common.OrderJsonPayloads;
import com.bluenote.order.infrastructure.client.OrderMemberClient;
import com.bluenote.order.infrastructure.entity.OrderEntities.CouponActivity;
import com.bluenote.order.infrastructure.entity.OrderEntities.CouponTemplate;
import com.bluenote.order.infrastructure.entity.OrderEntities.OrderConsumeRecord;
import com.bluenote.order.infrastructure.entity.OrderEntities.OrderOutboxEvent;
import com.bluenote.order.infrastructure.entity.OrderEntities.OrderTimeoutTask;
import com.bluenote.order.infrastructure.entity.OrderEntities.PaymentRecord;
import com.bluenote.order.infrastructure.entity.OrderEntities.SeckillRequest;
import com.bluenote.order.infrastructure.entity.OrderEntities.UserCoupon;
import com.bluenote.order.infrastructure.entity.OrderEntities.VoucherOrder;
import com.bluenote.order.infrastructure.mapper.OrderMapper;
import com.bluenote.order.infrastructure.mapper.OrderMapper.StatusCountRow;
import com.bluenote.order.infrastructure.redis.OrderRedisStore;
import com.bluenote.order.infrastructure.redis.OrderRedisStore.RedisActivitySnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String ACTIVITY_READY = "READY";
    private static final String ACTIVITY_PREHEATED = "PREHEATED";
    private static final String ACTIVITY_ONLINE = "ONLINE";
    private static final String ACTIVITY_SOLD_OUT = "SOLD_OUT";
    private static final String ACTIVITY_PAUSED = "PAUSED";
    private static final String ACTIVITY_ENDED = "ENDED";
    private static final String ACTIVITY_CANCELLED = "CANCELLED";
    private static final String REQUEST_PROCESSING = "PROCESSING";
    private static final String REQUEST_SUCCESS = "SUCCESS";
    private static final String REQUEST_WAIT_PAY = "WAIT_PAY";
    private static final String REQUEST_SOLD_OUT = "SOLD_OUT";
    private static final String REQUEST_DUPLICATE = "DUPLICATE";
    private static final String REQUEST_CANCELLED = "CANCELLED";
    private static final String REQUEST_CLOSED = "CLOSED";
    private static final String ORDER_WAIT_PAY = "WAIT_PAY";
    private static final String ORDER_SUCCESS = "SUCCESS";
    private static final String ORDER_CLOSED = "CLOSED";
    private static final String ORDER_CANCELLED = "CANCELLED";
    private static final String COUPON_UNUSED = "UNUSED";
    private static final String TIMEOUT_PENDING = "PENDING";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final OrderMapper orderMapper;
    private final OrderRedisStore redisStore;
    private final OrderMemberClient memberClient;
    private final OrderIdGenerator idGenerator;
    private final OrderJsonPayloads jsonPayloads;
    private final TransactionTemplate transactionTemplate;
    private final int tokenTtlSeconds;
    private final long redisRetainMinutes;
    private final int timeoutScanBatchSize;

    public OrderApplicationService(
            OrderMapper orderMapper,
            OrderRedisStore redisStore,
            OrderMemberClient memberClient,
            OrderIdGenerator idGenerator,
            OrderJsonPayloads jsonPayloads,
            TransactionTemplate transactionTemplate,
            @Value("${bluenote.order.seckill-token-ttl-seconds:30}") int tokenTtlSeconds,
            @Value("${bluenote.order.redis-retain-minutes:60}") long redisRetainMinutes,
            @Value("${bluenote.order.timeout-scan-batch-size:50}") int timeoutScanBatchSize
    ) {
        this.orderMapper = orderMapper;
        this.redisStore = redisStore;
        this.memberClient = memberClient;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.transactionTemplate = transactionTemplate;
        this.tokenTtlSeconds = Math.max(10, tokenTtlSeconds);
        this.redisRetainMinutes = Math.max(10, redisRetainMinutes);
        this.timeoutScanBatchSize = Math.max(1, timeoutScanBatchSize);
    }

    @Transactional(readOnly = true)
    public ActivityCurrentResponse currentActivity(String userId) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        LocalDateTime now = now();
        CouponActivity activity = orderMapper.selectCurrentActivity(now);
        if (activity == null) {
            return new ActivityCurrentResponse(null);
        }
        CouponTemplate template = orderMapper.selectTemplateById(activity.getTemplateId());
        boolean joined = orderMapper.selectRequestByUserActivity(currentUserId, activity.getActivityId()) != null;
        return new ActivityCurrentResponse(toActivityItem(activity, template, joined, now));
    }

    @Transactional
    public InternalActivityResponse createActivity(InternalCreateActivityRequest request) {
        LocalDateTime now = now();
        if (request == null) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        String activityName = requireText(request.activityName(), 128, ApiErrorCode.PARAM_INVALID);
        String templateName = requireText(request.templateName(), 128, ApiErrorCode.PARAM_INVALID);
        int faceValue = positive(request.faceValue(), ApiErrorCode.PARAM_INVALID);
        int thresholdAmount = nonNegative(request.thresholdAmount(), ApiErrorCode.PARAM_INVALID);
        int validDays = positive(defaultInt(request.validDays(), 7), ApiErrorCode.PARAM_INVALID);
        int totalStock = positive(request.totalStock(), ApiErrorCode.PARAM_INVALID);
        int payAmount = nonNegative(defaultInt(request.payAmount(), 0), ApiErrorCode.PARAM_INVALID);
        int perUserLimit = positive(defaultInt(request.perUserLimit(), 1), ApiErrorCode.PARAM_INVALID);
        int timeoutMinutes = positive(defaultInt(request.payTimeoutMinutes(), 15), ApiErrorCode.PARAM_INVALID);
        LocalDateTime startAt = parseTime(request.startAt(), ApiErrorCode.PARAM_INVALID);
        LocalDateTime endAt = parseTime(request.endAt(), ApiErrorCode.PARAM_INVALID);
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }

        Long templateId = idGenerator.nextId();
        CouponTemplate template = new CouponTemplate();
        template.setTemplateId(templateId);
        template.setTemplateName(templateName);
        template.setFaceValue(faceValue);
        template.setThresholdAmount(thresholdAmount);
        template.setValidDays(validDays);
        template.setCoverUrl(null);
        template.setDescription(blankToNull(request.description()));
        template.setTemplateStatus("ACTIVE");
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        orderMapper.insertTemplate(template);

        Long activityId = idGenerator.nextId();
        CouponActivity activity = new CouponActivity();
        activity.setActivityId(activityId);
        activity.setTemplateId(templateId);
        activity.setActivityName(activityName);
        activity.setTotalStock(totalStock);
        activity.setAvailableStock(totalStock);
        activity.setLockedStock(0);
        activity.setSoldStock(0);
        activity.setPerUserLimit(perUserLimit);
        activity.setPayAmount(payAmount);
        activity.setActivityStatus(ACTIVITY_READY);
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setPayTimeoutMinutes(timeoutMinutes);
        activity.setPreheatedAt(null);
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        activity.setVersion(0L);
        orderMapper.insertActivity(activity);
        return new InternalActivityResponse(String.valueOf(activityId), String.valueOf(templateId), ACTIVITY_READY);
    }

    @Transactional(readOnly = true)
    public InternalActivityListResponse listActivities(String status, String cursor, Integer pageSize) {
        String normalizedStatus = normalizeActivityStatus(status);
        int size = normalizePageSize(pageSize);
        ActivityCursor parsedCursor = parseActivityCursor(cursor);
        List<CouponActivity> rows = orderMapper.selectActivities(
                normalizedStatus,
                parsedCursor == null ? null : parsedCursor.createdAt(),
                parsedCursor == null ? null : parsedCursor.activityId(),
                size + 1
        );
        boolean hasMore = rows.size() > size;
        List<CouponActivity> page = hasMore ? rows.subList(0, size) : rows;
        LocalDateTime now = now();
        List<InternalActivityListItem> items = page.stream()
                .map(activity -> toActivityListItem(activity, orderMapper.selectTemplateById(activity.getTemplateId()), now))
                .toList();
        return new InternalActivityListResponse(items, nextActivityCursor(page, hasMore), hasMore);
    }

    @Transactional
    public ActivityStatusResponse preheat(String activityId) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        CouponActivity activity = requireActivity(parsedActivityId);
        LocalDateTime now = now();
        assertPreheatAllowed(activity, now);
        redisStore.preheat(parsedActivityId, safeInt(activity.getAvailableStock()), redisTtl(activity, now));
        int updated = orderMapper.markActivityPreheated(parsedActivityId, now);
        if (updated <= 0) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        return new ActivityStatusResponse(String.valueOf(parsedActivityId), ACTIVITY_PREHEATED);
    }

    @Transactional
    public ActivityStatusResponse updateActivityStatus(String activityId, String action) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        CouponActivity activity = requireActivity(parsedActivityId);
        LocalDateTime now = now();
        List<String> allowedStatuses;
        String status = switch (action) {
            case "pause" -> {
                assertPauseAllowed(activity);
                allowedStatuses = List.of(ACTIVITY_PREHEATED, ACTIVITY_ONLINE, ACTIVITY_SOLD_OUT);
                yield ACTIVITY_PAUSED;
            }
            case "resume" -> {
                assertResumeAllowed(activity);
                allowedStatuses = List.of(ACTIVITY_PAUSED);
                yield safeInt(activity.getAvailableStock()) <= 0 ? ACTIVITY_SOLD_OUT : ACTIVITY_PREHEATED;
            }
            case "end" -> {
                assertEndAllowed(activity);
                allowedStatuses = List.of(ACTIVITY_READY, ACTIVITY_PREHEATED, ACTIVITY_ONLINE, ACTIVITY_SOLD_OUT, ACTIVITY_PAUSED);
                yield ACTIVITY_ENDED;
            }
            default -> throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        };
        int updated = orderMapper.updateActivityStatus(parsedActivityId, status, now, allowedStatuses);
        if (updated <= 0) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        return new ActivityStatusResponse(String.valueOf(parsedActivityId), status);
    }

    @Transactional(readOnly = true)
    public SeckillTokenResponse issueToken(String userId, SeckillTokenRequest request) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long activityId = parseId(request == null ? null : request.activityId(), ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        assertUserAllowed(currentUserId);
        CouponActivity activity = requireActivity(activityId);
        LocalDateTime now = now();
        if (redisStore.rebuilding(activityId)) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_REBUILDING);
        }
        if (!ACTIVITY_ONLINE.equals(displayStatus(activity, now))) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        String token = redisStore.issueToken(activityId, currentUserId, Duration.ofSeconds(tokenTtlSeconds));
        return new SeckillTokenResponse(token, tokenTtlSeconds);
    }

    public SeckillSubmitResponse submitSeckill(String userId, SeckillSubmitRequest request) {
        if (request == null) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long activityId = parseId(request == null ? null : request.activityId(), ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        String clientRequestId = requireText(request.clientRequestId(), 128, ApiErrorCode.PARAM_INVALID);
        String token = requireText(request.seckillToken(), 128, ApiErrorCode.ORDER_SECKILL_TOKEN_INVALID);
        assertUserAllowed(currentUserId);

        SeckillRequest existing = orderMapper.selectRequestByUserClient(currentUserId, clientRequestId);
        if (existing != null) {
            return toSubmitResponse(existing);
        }
        existing = orderMapper.selectRequestByUserActivity(currentUserId, activityId);
        if (existing != null) {
            return toSubmitResponse(existing);
        }

        CouponActivity activity = requireActivity(activityId);
        LocalDateTime now = now();
        if (redisStore.rebuilding(activityId)) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_REBUILDING);
        }
        if (!ACTIVITY_ONLINE.equals(displayStatus(activity, now))) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }

        Long requestId = idGenerator.nextId();
        long preDeduct = redisStore.preDeduct(activityId, currentUserId, requestId, token, redisTtl(activity, now));
        if (preDeduct == -2L) {
            throw new BusinessException(ApiErrorCode.ORDER_SECKILL_TOKEN_INVALID);
        }
        if (preDeduct == -1L) {
            SeckillRequest duplicate = orderMapper.selectRequestByUserActivity(currentUserId, activityId);
            if (duplicate != null) {
                return toSubmitResponse(duplicate);
            }
            throw new BusinessException(ApiErrorCode.ORDER_DUPLICATE_REQUEST);
        }
        if (preDeduct == 0L) {
            return insertTerminalRequest(currentUserId, activityId, clientRequestId, requestId, REQUEST_SOLD_OUT, "神券已抢光", now);
        }
        if (preDeduct != 1L) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_NOT_PREHEATED);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                SeckillRequest entity = new SeckillRequest();
                entity.setRequestId(requestId);
                entity.setClientRequestId(clientRequestId);
                entity.setUserId(currentUserId);
                entity.setActivityId(activityId);
                entity.setOrderId(null);
                entity.setUserCouponId(null);
                entity.setRequestStatus(REQUEST_PROCESSING);
                entity.setResultMessage("抢券处理中");
                entity.setPayRequired(0);
                entity.setPayAmount(safeInt(activity.getPayAmount()));
                entity.setExpireAt(null);
                entity.setRequestedAt(now);
                entity.setCompletedAt(null);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                orderMapper.insertSeckillRequest(entity);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("requestId", String.valueOf(requestId));
                payload.put("clientRequestId", clientRequestId);
                payload.put("userId", String.valueOf(currentUserId));
                payload.put("activityId", String.valueOf(activityId));
                payload.put("expectedPayAmount", safeInt(activity.getPayAmount()));
                insertOutbox("CouponSeckillAccepted", String.valueOf(requestId), payload, now);
            });
        } catch (DuplicateKeyException exception) {
            SeckillRequest duplicate = orderMapper.selectRequestByUserClient(currentUserId, clientRequestId);
            if (duplicate == null) {
                duplicate = orderMapper.selectRequestByUserActivity(currentUserId, activityId);
            }
            if (duplicate != null) {
                return toSubmitResponse(duplicate);
            }
            redisStore.recoverReservation(activityId, currentUserId);
            throw exception;
        } catch (RuntimeException exception) {
            redisStore.recoverReservation(activityId, currentUserId);
            throw exception;
        }
        return new SeckillSubmitResponse(String.valueOf(requestId), REQUEST_PROCESSING, "抢券处理中");
    }

    @Transactional(readOnly = true)
    public SeckillResultResponse seckillResult(String userId, String requestId) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        SeckillRequest request = requireRequest(requestId);
        if (!currentUserId.equals(request.getUserId())) {
            throw new BusinessException(ApiErrorCode.ORDER_OWNER_FORBIDDEN);
        }
        return toResultResponse(request);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse orderDetail(String userId, String orderId) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        VoucherOrder order = requireOrder(orderId);
        if (!currentUserId.equals(order.getUserId())) {
            throw new BusinessException(ApiErrorCode.ORDER_OWNER_FORBIDDEN);
        }
        return toOrderDetail(order);
    }

    public OrderPayResponse pay(String userId, String orderId, OrderPayRequest request) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedOrderId = parseId(orderId, ApiErrorCode.ORDER_NOT_FOUND);
        String channel = normalizeChannel(request == null ? null : request.channel());
        if (!"MOCK".equals(channel)) {
            throw new BusinessException(ApiErrorCode.ORDER_PAYMENT_INVALID);
        }
        return transactionTemplate.execute(status -> payInTransaction(currentUserId, parsedOrderId, channel));
    }

    public OrderCancelResponse cancel(String userId, String orderId) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedOrderId = parseId(orderId, ApiErrorCode.ORDER_NOT_FOUND);
        return transactionTemplate.execute(status -> cancelInTransaction(currentUserId, parsedOrderId));
    }

    @Transactional(readOnly = true)
    public OrderCouponListResponse myCoupons(String userId, String status, String cursor, Integer pageSize) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String normalizedStatus = normalizeCouponStatus(status);
        int size = normalizePageSize(pageSize);
        CouponCursor parsedCursor = parseCouponCursor(cursor);
        List<UserCoupon> rows = orderMapper.selectUserCoupons(
                currentUserId,
                normalizedStatus,
                parsedCursor == null ? null : parsedCursor.issuedAt(),
                parsedCursor == null ? null : parsedCursor.userCouponId(),
                size + 1
        );
        boolean hasMore = rows.size() > size;
        List<UserCoupon> page = hasMore ? rows.subList(0, size) : rows;
        List<UserCouponItem> items = page.stream().map(this::toCouponItem).toList();
        return new OrderCouponListResponse(items, nextCouponCursor(page, hasMore), hasMore);
    }

    public void consumeEvent(OrderConsumeEventRequest request) {
        transactionTemplate.executeWithoutResult(status -> consumeEventInTransaction(request));
    }

    @Scheduled(
            initialDelayString = "${bluenote.order.timeout-scan-initial-delay-millis:10000}",
            fixedDelayString = "${bluenote.order.timeout-scan-fixed-delay-millis:30000}"
    )
    public void closeExpiredOrdersScheduled() {
        scanTimeoutTasksOnce();
    }

    public OrderTimeoutScanResponse scanTimeoutTasksOnce() {
        List<OrderTimeoutTask> tasks = orderMapper.selectDueTimeoutTasks(now(), timeoutScanBatchSize);
        int closedCount = 0;
        int failedCount = 0;
        for (OrderTimeoutTask task : tasks) {
            try {
                if (closeExpiredOrder(task.getOrderId(), true)) {
                    closedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("Failed to close expired order, orderId={}", task.getOrderId(), exception);
            }
        }
        return new OrderTimeoutScanResponse(tasks.size(), closedCount, failedCount);
    }

    @Transactional(readOnly = true)
    public InternalActivityOpsSummaryResponse opsSummary(String activityId) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        CouponActivity activity = requireActivity(parsedActivityId);
        CouponTemplate template = orderMapper.selectTemplateById(activity.getTemplateId());
        StockExpectation stock = stockExpectation(activity);
        LocalDateTime now = now();
        return new InternalActivityOpsSummaryResponse(
                String.valueOf(activity.getActivityId()),
                activity.getActivityName(),
                String.valueOf(activity.getTemplateId()),
                template == null ? "" : template.getTemplateName(),
                activity.getActivityStatus(),
                displayStatus(activity, now),
                safeInt(activity.getTotalStock()),
                safeInt(activity.getAvailableStock()),
                safeInt(activity.getSoldStock()),
                stock.availableStock(),
                stock.soldStock(),
                stock.consistent(activity),
                toRedisSnapshot(redisStore.snapshot(parsedActivityId)),
                statusCounts(orderMapper.countRequestsByActivity(parsedActivityId)),
                statusCounts(orderMapper.countOrdersByActivity(parsedActivityId)),
                statusCounts(orderMapper.countCouponsByActivity(parsedActivityId)),
                toOffsetString(now)
        );
    }

    @Transactional(readOnly = true)
    public OrderRedisRebuildResponse rebuildRedis(String activityId) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        CouponActivity activity = requireActivity(parsedActivityId);
        RedisActivitySnapshot before = redisStore.snapshot(parsedActivityId);
        Set<Long> participantUserIds = new LinkedHashSet<>(orderMapper.selectActiveParticipantUserIds(parsedActivityId));
        redisStore.rebuild(parsedActivityId, safeInt(activity.getAvailableStock()), participantUserIds, redisTtl(activity, now()));
        RedisActivitySnapshot after = redisStore.snapshot(parsedActivityId);
        return new OrderRedisRebuildResponse(
                String.valueOf(parsedActivityId),
                safeInt(activity.getAvailableStock()),
                participantUserIds.size(),
                toRedisSnapshot(before),
                toRedisSnapshot(after),
                true
        );
    }

    public OrderStockReconcileResponse reconcileStock(String activityId, OrderStockReconcileRequest request) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        boolean repair = request != null && Boolean.TRUE.equals(request.repair());
        return transactionTemplate.execute(status -> reconcileStockInTransaction(parsedActivityId, repair));
    }

    @Transactional(readOnly = true)
    public OrderActivityPrecheckResponse precheck(String activityId) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        CouponActivity activity = requireActivity(parsedActivityId);
        return buildPrecheck(activity, now());
    }

    public OrderStockAdjustResponse adjustStock(String activityId, OrderStockAdjustRequest request) {
        Long parsedActivityId = parseId(activityId, ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        if (request == null || request.deltaStock() == null || request.deltaStock() == 0) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        int deltaStock = request.deltaStock();
        if (deltaStock == Integer.MIN_VALUE || Math.abs(deltaStock) > 100000) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        String reason = requireText(request.reason(), 256, ApiErrorCode.PARAM_INVALID);
        String operatorId = isBlank(request.operatorId()) ? "order-ops" : requireText(request.operatorId(), 64, ApiErrorCode.PARAM_INVALID);
        RedisActivitySnapshot beforeRedis = redisStore.snapshot(parsedActivityId);
        OrderStockAdjustResponse response = transactionTemplate.execute(status ->
                adjustStockInTransaction(parsedActivityId, deltaStock, reason, operatorId)
        );
        if (response != null && Boolean.TRUE.equals(beforeRedis.stockKeyExists())) {
            CouponActivity activity = requireActivity(parsedActivityId);
            Set<Long> participantUserIds = new LinkedHashSet<>(orderMapper.selectActiveParticipantUserIds(parsedActivityId));
            redisStore.rebuild(parsedActivityId, safeInt(activity.getAvailableStock()), participantUserIds, redisTtl(activity, now()));
        }
        return response;
    }

    public OrderSweepStuckResponse sweepStuckRequests(OrderSweepStuckRequest request) {
        int stuckSeconds = Math.max(10, request == null || request.stuckSeconds() == null ? 60 : request.stuckSeconds());
        int limit = Math.min(100, Math.max(1, request == null || request.limit() == null ? timeoutScanBatchSize : request.limit()));
        LocalDateTime deadline = now().minusSeconds(stuckSeconds);
        List<SeckillRequest> requests = orderMapper.selectStuckProcessingRequests(deadline, limit);
        int retriedCount = 0;
        int syncedCount = 0;
        int skippedCount = 0;
        List<String> requestIds = requests.stream().map(item -> String.valueOf(item.getRequestId())).toList();
        for (SeckillRequest requestItem : requests) {
            SweepResult result = transactionTemplate.execute(status -> sweepOneStuckRequest(requestItem.getRequestId()));
            if (result == SweepResult.RETRIED) {
                retriedCount++;
            } else if (result == SweepResult.SYNCED) {
                syncedCount++;
            } else {
                skippedCount++;
            }
        }
        return new OrderSweepStuckResponse(requests.size(), retriedCount, syncedCount, skippedCount, requestIds);
    }

    private SeckillSubmitResponse insertTerminalRequest(
            Long userId,
            Long activityId,
            String clientRequestId,
            Long requestId,
            String requestStatus,
            String message,
            LocalDateTime now
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                SeckillRequest entity = new SeckillRequest();
                entity.setRequestId(requestId);
                entity.setClientRequestId(clientRequestId);
                entity.setUserId(userId);
                entity.setActivityId(activityId);
                entity.setOrderId(null);
                entity.setUserCouponId(null);
                entity.setRequestStatus(requestStatus);
                entity.setResultMessage(message);
                entity.setPayRequired(0);
                entity.setPayAmount(0);
                entity.setExpireAt(null);
                entity.setRequestedAt(now);
                entity.setCompletedAt(now);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                orderMapper.insertSeckillRequest(entity);
            });
        } catch (DuplicateKeyException exception) {
            SeckillRequest duplicate = orderMapper.selectRequestByUserClient(userId, clientRequestId);
            if (duplicate == null) {
                duplicate = orderMapper.selectRequestByUserActivity(userId, activityId);
            }
            if (duplicate != null) {
                return toSubmitResponse(duplicate);
            }
            throw exception;
        }
        return new SeckillSubmitResponse(String.valueOf(requestId), requestStatus, message);
    }

    private OrderStockReconcileResponse reconcileStockInTransaction(Long activityId, boolean repair) {
        CouponActivity activity = orderMapper.selectActivityByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        }
        StockExpectation stock = stockExpectation(activity);
        int beforeAvailable = safeInt(activity.getAvailableStock());
        int beforeSold = safeInt(activity.getSoldStock());
        boolean consistent = stock.consistent(activity);
        boolean repaired = false;
        if (!consistent && repair) {
            orderMapper.repairActivityStock(activityId, stock.availableStock(), stock.soldStock(), now());
            repaired = true;
            activity = orderMapper.selectActivityById(activityId);
        }
        int afterAvailable = activity == null ? beforeAvailable : safeInt(activity.getAvailableStock());
        int afterSold = activity == null ? beforeSold : safeInt(activity.getSoldStock());
        return new OrderStockReconcileResponse(
                String.valueOf(activityId),
                activity == null ? 0 : safeInt(activity.getTotalStock()),
                beforeAvailable,
                beforeSold,
                stock.availableStock(),
                stock.soldStock(),
                afterAvailable,
                afterSold,
                consistent,
                repaired,
                consistent ? "stock consistent" : (repaired ? "stock repaired" : "stock inconsistent")
        );
    }

    private OrderStockAdjustResponse adjustStockInTransaction(Long activityId, int deltaStock, String reason, String operatorId) {
        CouponActivity before = orderMapper.selectActivityByIdForUpdate(activityId);
        if (before == null) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        }
        if (ACTIVITY_ENDED.equals(before.getActivityStatus()) || ACTIVITY_CANCELLED.equals(before.getActivityStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        StockExpectation stock = stockExpectation(before);
        if (!stock.consistent(before)) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        LocalDateTime now = now();
        int updated = orderMapper.adjustActivityStock(activityId, deltaStock, now);
        if (updated <= 0) {
            throw new BusinessException(ApiErrorCode.ORDER_STOCK_NOT_ENOUGH);
        }
        CouponActivity after = orderMapper.selectActivityById(activityId);
        orderMapper.insertStockLog(
                idGenerator.nextId(),
                activityId,
                null,
                null,
                "OPS_ADJUST",
                deltaStock,
                safeInt(before.getAvailableStock()),
                after == null ? null : safeInt(after.getAvailableStock()),
                "OPS",
                operatorId,
                reason,
                now
        );
        return new OrderStockAdjustResponse(
                String.valueOf(activityId),
                safeInt(before.getTotalStock()),
                safeInt(before.getAvailableStock()),
                safeInt(before.getSoldStock()),
                after == null ? 0 : safeInt(after.getTotalStock()),
                after == null ? 0 : safeInt(after.getAvailableStock()),
                after == null ? 0 : safeInt(after.getSoldStock()),
                after == null ? before.getActivityStatus() : after.getActivityStatus(),
                displayStatus(after == null ? before : after, now)
        );
    }

    private SweepResult sweepOneStuckRequest(Long requestId) {
        SeckillRequest request = orderMapper.selectRequestByIdForUpdate(requestId);
        if (request == null || !REQUEST_PROCESSING.equals(request.getRequestStatus())) {
            return SweepResult.SKIPPED;
        }
        LocalDateTime now = now();
        VoucherOrder existingOrder = orderMapper.selectOrderByUserActivity(request.getUserId(), request.getActivityId());
        if (existingOrder != null) {
            UserCoupon coupon = orderMapper.selectCouponBySourceOrder(existingOrder.getOrderId());
            updateRequestFromOrder(request, existingOrder, coupon, now);
            return SweepResult.SYNCED;
        }
        CouponActivity activity = orderMapper.selectActivityById(request.getActivityId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", String.valueOf(request.getRequestId()));
        payload.put("clientRequestId", request.getClientRequestId());
        payload.put("userId", String.valueOf(request.getUserId()));
        payload.put("activityId", String.valueOf(request.getActivityId()));
        payload.put("expectedPayAmount", activity == null ? safeInt(request.getPayAmount()) : safeInt(activity.getPayAmount()));
        insertOutbox("CouponSeckillAccepted", String.valueOf(request.getRequestId()), payload, now);
        return SweepResult.RETRIED;
    }

    private void consumeEventInTransaction(OrderConsumeEventRequest request) {
        if (request == null || isBlank(request.eventId()) || isBlank(request.eventType())) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        OrderConsumeRecord existing = orderMapper.selectConsumeRecord(request.consumerGroup(), request.eventId());
        if (existing != null && "SUCCESS".equals(existing.getConsumeStatus())) {
            return;
        }
        if (existing == null) {
            OrderConsumeRecord record = new OrderConsumeRecord();
            record.setId(idGenerator.nextId());
            record.setConsumerGroup(request.consumerGroup());
            record.setEventId(request.eventId());
            record.setTopic(request.topic());
            record.setEventType(request.eventType());
            record.setBizKey(request.bizKey());
            record.setEnvelopeJson(request.envelopeJson());
            record.setConsumeStatus("PROCESSING");
            record.setRetryCount(0);
            record.setErrorMessage(null);
            record.setConsumedAt(null);
            record.setCreatedAt(now());
            record.setUpdatedAt(now());
            orderMapper.insertConsumeRecord(record);
            existing = record;
        }
        if ("CouponSeckillAccepted".equals(request.eventType())) {
            createOrderFromAccepted(request.payload());
        } else if ("OrderTimeoutCheck".equals(request.eventType())) {
            Long orderId = parseId(value(request.payload(), "orderId"), ApiErrorCode.ORDER_NOT_FOUND);
            closeExpiredOrderInTransaction(orderId, false);
        }
        LocalDateTime consumedAt = now();
        orderMapper.updateConsumeRecordSuccess(existing.getId(), consumedAt, consumedAt);
    }

    private void createOrderFromAccepted(Map<String, Object> payload) {
        Long requestId = parseId(value(payload, "requestId"), ApiErrorCode.ORDER_REQUEST_NOT_FOUND);
        SeckillRequest request = orderMapper.selectRequestByIdForUpdate(requestId);
        if (request == null) {
            throw new BusinessException(ApiErrorCode.ORDER_REQUEST_NOT_FOUND);
        }
        if (!REQUEST_PROCESSING.equals(request.getRequestStatus())) {
            return;
        }
        LocalDateTime now = now();
        CouponActivity activity = orderMapper.selectActivityByIdForUpdate(request.getActivityId());
        if (activity == null) {
            orderMapper.updateRequestResult(requestId, "FAILED", null, null, 0, 0, null, "活动不存在", now, now);
            redisStore.recoverReservation(request.getActivityId(), request.getUserId());
            return;
        }
        VoucherOrder existingOrder = orderMapper.selectOrderByUserActivity(request.getUserId(), request.getActivityId());
        if (existingOrder != null) {
            UserCoupon coupon = orderMapper.selectCouponBySourceOrder(existingOrder.getOrderId());
            updateRequestFromOrder(request, existingOrder, coupon, now);
            return;
        }
        int deducted = orderMapper.deductActivityStock(activity.getActivityId(), now);
        if (deducted <= 0) {
            orderMapper.markActivitySoldOut(activity.getActivityId(), now);
            orderMapper.updateRequestResult(requestId, REQUEST_SOLD_OUT, null, null, 0, 0, null, "神券已抢光", now, now);
            redisStore.recoverReservation(activity.getActivityId(), request.getUserId());
            return;
        }

        CouponTemplate template = orderMapper.selectTemplateById(activity.getTemplateId());
        Long orderId = idGenerator.nextId();
        boolean payRequired = safeInt(activity.getPayAmount()) > 0;
        LocalDateTime expireAt = payRequired ? now.plusMinutes(Math.max(1, safeInt(activity.getPayTimeoutMinutes()))) : null;
        VoucherOrder order = new VoucherOrder();
        order.setOrderId(orderId);
        order.setOrderNo(orderNo(orderId, now));
        order.setRequestId(requestId);
        order.setUserId(request.getUserId());
        order.setActivityId(activity.getActivityId());
        order.setTemplateId(activity.getTemplateId());
        order.setPayAmount(safeInt(activity.getPayAmount()));
        order.setOrderStatus(payRequired ? ORDER_WAIT_PAY : ORDER_SUCCESS);
        order.setExpireAt(expireAt);
        order.setPaidAt(null);
        order.setSuccessAt(payRequired ? null : now);
        order.setClosedAt(null);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setVersion(0L);
        orderMapper.insertOrder(order);

        UserCoupon coupon = null;
        if (!payRequired) {
            coupon = createCoupon(order, activity, template, now);
        } else {
            OrderTimeoutTask task = new OrderTimeoutTask();
            task.setTaskId(idGenerator.nextId());
            task.setOrderId(orderId);
            task.setActivityId(activity.getActivityId());
            task.setUserId(request.getUserId());
            task.setExpireAt(expireAt);
            task.setTaskStatus(TIMEOUT_PENDING);
            task.setExecutedAt(null);
            task.setErrorMessage(null);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            orderMapper.insertTimeoutTask(task);
        }

        orderMapper.insertStockLog(
                idGenerator.nextId(),
                activity.getActivityId(),
                orderId,
                requestId,
                "DEDUCT",
                -1,
                null,
                null,
                "SYSTEM",
                "order-service",
                "ORDER_CREATE",
                now
        );
        orderMapper.insertStatusLog(idGenerator.nextId(), orderId, null, order.getOrderStatus(), "CREATE", "SYSTEM", "order-service", now);
        orderMapper.updateRequestResult(
                requestId,
                payRequired ? REQUEST_WAIT_PAY : REQUEST_SUCCESS,
                orderId,
                coupon == null ? null : coupon.getUserCouponId(),
                payRequired ? 1 : 0,
                safeInt(activity.getPayAmount()),
                expireAt,
                payRequired ? "待支付" : "神券已到账",
                now,
                now
        );

        insertOrderCreatedOutbox(order, now);
        if (payRequired) {
            insertTimeoutOutbox(order, now);
            insertPushOutbox("ORDER_WAIT_PAY", order.getUserId(), order.getOrderId(), null, "神券待支付", "你抢到的神券需要完成支付", now);
        } else {
            insertCouponIssuedOutbox(coupon, now);
            insertPushOutbox("COUPON_ISSUED", order.getUserId(), order.getOrderId(), coupon.getUserCouponId(), "神券已到账", "你抢到的 BlueNote 神券已放入卡包", now);
        }
    }

    private OrderPayResponse payInTransaction(Long currentUserId, Long orderId, String channel) {
        VoucherOrder order = orderMapper.selectOrderByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ApiErrorCode.ORDER_NOT_FOUND);
        }
        if (!currentUserId.equals(order.getUserId())) {
            throw new BusinessException(ApiErrorCode.ORDER_OWNER_FORBIDDEN);
        }
        if (ORDER_SUCCESS.equals(order.getOrderStatus())) {
            UserCoupon coupon = orderMapper.selectCouponBySourceOrder(orderId);
            return new OrderPayResponse(String.valueOf(orderId), ORDER_SUCCESS, true, coupon == null ? null : String.valueOf(coupon.getUserCouponId()));
        }
        LocalDateTime now = now();
        if (!ORDER_WAIT_PAY.equals(order.getOrderStatus()) || order.getExpireAt() == null || !order.getExpireAt().isAfter(now)) {
            throw new BusinessException(ApiErrorCode.ORDER_STATUS_INVALID);
        }
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentId(idGenerator.nextId());
        payment.setOrderId(orderId);
        payment.setOrderNo(order.getOrderNo());
        payment.setChannel(channel);
        payment.setChannelTradeNo("mock_" + order.getOrderNo() + "_" + UUID.randomUUID());
        payment.setPayAmount(order.getPayAmount());
        payment.setPaymentStatus("SUCCESS");
        payment.setPaidAt(now);
        payment.setRawPayloadJson("{\"channel\":\"MOCK\"}");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        orderMapper.insertPayment(payment);

        int updated = orderMapper.updateOrderStatusCas(orderId, ORDER_WAIT_PAY, ORDER_SUCCESS, now, now, null, now);
        if (updated <= 0) {
            throw new BusinessException(ApiErrorCode.ORDER_STATUS_INVALID);
        }
        CouponActivity activity = orderMapper.selectActivityById(order.getActivityId());
        CouponTemplate template = orderMapper.selectTemplateById(order.getTemplateId());
        UserCoupon coupon = createCoupon(order, activity, template, now);
        orderMapper.updateRequestResult(order.getRequestId(), REQUEST_SUCCESS, orderId, coupon.getUserCouponId(), 0, order.getPayAmount(), null, "神券已到账", now, now);
        orderMapper.updateTimeoutTaskStatus(orderId, "PAID", now, null, now);
        orderMapper.insertStatusLog(idGenerator.nextId(), orderId, ORDER_WAIT_PAY, ORDER_SUCCESS, "MOCK_PAY", "USER", String.valueOf(currentUserId), now);
        insertOrderPaidOutbox(order, coupon, now);
        insertCouponIssuedOutbox(coupon, now);
        insertPushOutbox("COUPON_ISSUED", order.getUserId(), orderId, coupon.getUserCouponId(), "支付成功，神券已到账", "你的 BlueNote 神券已放入卡包", now);
        return new OrderPayResponse(String.valueOf(orderId), ORDER_SUCCESS, true, String.valueOf(coupon.getUserCouponId()));
    }

    private OrderCancelResponse cancelInTransaction(Long currentUserId, Long orderId) {
        VoucherOrder order = orderMapper.selectOrderByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ApiErrorCode.ORDER_NOT_FOUND);
        }
        if (!currentUserId.equals(order.getUserId())) {
            throw new BusinessException(ApiErrorCode.ORDER_OWNER_FORBIDDEN);
        }
        if (!ORDER_WAIT_PAY.equals(order.getOrderStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_STATUS_INVALID);
        }
        LocalDateTime now = now();
        int updated = orderMapper.updateOrderStatusCas(orderId, ORDER_WAIT_PAY, ORDER_CANCELLED, null, null, now, now);
        if (updated <= 0) {
            throw new BusinessException(ApiErrorCode.ORDER_STATUS_INVALID);
        }
        orderMapper.replenishActivityStock(order.getActivityId(), now);
        orderMapper.insertStockLog(
                idGenerator.nextId(),
                order.getActivityId(),
                orderId,
                order.getRequestId(),
                "CANCEL_REPLENISH",
                1,
                null,
                null,
                "USER",
                String.valueOf(currentUserId),
                "USER_CANCEL",
                now
        );
        orderMapper.insertStatusLog(idGenerator.nextId(), orderId, ORDER_WAIT_PAY, ORDER_CANCELLED, "USER_CANCEL", "USER", String.valueOf(currentUserId), now);
        orderMapper.updateRequestResult(order.getRequestId(), REQUEST_CANCELLED, orderId, null, 0, order.getPayAmount(), null, "订单已取消", now, now);
        orderMapper.updateTimeoutTaskStatus(orderId, "CANCELLED", now, null, now);
        redisStore.recoverReservation(order.getActivityId(), currentUserId);
        insertOrderCancelledOutbox(order, now);
        insertPushOutbox("ORDER_CANCELLED", order.getUserId(), orderId, null, "订单已取消", "你的神券订单已取消", now);
        return new OrderCancelResponse(String.valueOf(orderId), ORDER_CANCELLED, true);
    }

    private boolean closeExpiredOrder(Long orderId, boolean forceDue) {
        Boolean closed = transactionTemplate.execute(status -> closeExpiredOrderInTransaction(orderId, forceDue));
        return Boolean.TRUE.equals(closed);
    }

    private boolean closeExpiredOrderInTransaction(Long orderId, boolean forceDue) {
        VoucherOrder order = orderMapper.selectOrderByIdForUpdate(orderId);
        if (order == null) {
            return false;
        }
        LocalDateTime now = now();
        if (!ORDER_WAIT_PAY.equals(order.getOrderStatus())) {
            orderMapper.updateTimeoutTaskStatus(orderId, "SKIPPED", now, "order status " + order.getOrderStatus(), now);
            return false;
        }
        if (!forceDue && order.getExpireAt() != null && order.getExpireAt().isAfter(now)) {
            return false;
        }
        if (order.getExpireAt() != null && order.getExpireAt().isAfter(now)) {
            return false;
        }
        int updated = orderMapper.updateOrderStatusCas(orderId, ORDER_WAIT_PAY, ORDER_CLOSED, null, null, now, now);
        if (updated <= 0) {
            orderMapper.updateTimeoutTaskStatus(orderId, "SKIPPED", now, "status changed", now);
            return false;
        }
        orderMapper.replenishActivityStock(order.getActivityId(), now);
        orderMapper.insertStockLog(
                idGenerator.nextId(),
                order.getActivityId(),
                orderId,
                order.getRequestId(),
                "TIMEOUT_REPLENISH",
                1,
                null,
                null,
                "SYSTEM",
                "order-service",
                "TIMEOUT_CLOSE",
                now
        );
        orderMapper.insertStatusLog(idGenerator.nextId(), orderId, ORDER_WAIT_PAY, ORDER_CLOSED, "TIMEOUT_CLOSE", "SYSTEM", "order-service", now);
        orderMapper.updateRequestResult(order.getRequestId(), REQUEST_CLOSED, orderId, null, 0, order.getPayAmount(), null, "订单超时关闭", now, now);
        orderMapper.updateTimeoutTaskStatus(orderId, ORDER_CLOSED, now, null, now);
        redisStore.recoverReservation(order.getActivityId(), order.getUserId());
        insertOrderClosedOutbox(order, now);
        insertPushOutbox("ORDER_CLOSED", order.getUserId(), orderId, null, "订单已关闭", "你的神券订单已超时关闭", now);
        return true;
    }

    private UserCoupon createCoupon(VoucherOrder order, CouponActivity activity, CouponTemplate template, LocalDateTime now) {
        UserCoupon existing = orderMapper.selectCouponBySourceOrder(order.getOrderId());
        if (existing != null) {
            return existing;
        }
        UserCoupon coupon = new UserCoupon();
        coupon.setUserCouponId(idGenerator.nextId());
        coupon.setUserId(order.getUserId());
        coupon.setTemplateId(order.getTemplateId());
        coupon.setActivityId(order.getActivityId());
        coupon.setSourceOrderId(order.getOrderId());
        coupon.setCouponStatus(COUPON_UNUSED);
        coupon.setFaceValue(template == null ? 0 : safeInt(template.getFaceValue()));
        coupon.setThresholdAmount(template == null ? 0 : safeInt(template.getThresholdAmount()));
        coupon.setValidStartAt(now);
        coupon.setValidEndAt(now.plusDays(template == null ? 7 : Math.max(1, safeInt(template.getValidDays()))));
        coupon.setIssuedAt(now);
        coupon.setUsedAt(null);
        coupon.setExpiredAt(null);
        coupon.setCreatedAt(now);
        coupon.setUpdatedAt(now);
        coupon.setTemplateName(template == null ? "" : template.getTemplateName());
        coupon.setActivityName(activity == null ? "" : activity.getActivityName());
        orderMapper.insertUserCoupon(coupon);
        return coupon;
    }

    private void updateRequestFromOrder(SeckillRequest request, VoucherOrder order, UserCoupon coupon, LocalDateTime now) {
        boolean waitPay = ORDER_WAIT_PAY.equals(order.getOrderStatus());
        String status = switch (order.getOrderStatus()) {
            case ORDER_SUCCESS -> REQUEST_SUCCESS;
            case ORDER_CANCELLED -> REQUEST_CANCELLED;
            case ORDER_CLOSED -> REQUEST_CLOSED;
            default -> waitPay ? REQUEST_WAIT_PAY : order.getOrderStatus();
        };
        orderMapper.updateRequestResult(
                request.getRequestId(),
                status,
                order.getOrderId(),
                coupon == null ? null : coupon.getUserCouponId(),
                waitPay ? 1 : 0,
                safeInt(order.getPayAmount()),
                waitPay ? order.getExpireAt() : null,
                messageForRequestStatus(status),
                now,
                now
        );
    }

    private void insertOrderCreatedOutbox(VoucherOrder order, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", String.valueOf(order.getOrderId()));
        payload.put("orderNo", order.getOrderNo());
        payload.put("requestId", String.valueOf(order.getRequestId()));
        payload.put("userId", String.valueOf(order.getUserId()));
        payload.put("activityId", String.valueOf(order.getActivityId()));
        payload.put("payAmount", order.getPayAmount());
        payload.put("status", order.getOrderStatus());
        insertOutbox("OrderCreated", String.valueOf(order.getOrderId()), payload, now);
    }

    private void insertOrderPaidOutbox(VoucherOrder order, UserCoupon coupon, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", String.valueOf(order.getOrderId()));
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", String.valueOf(order.getUserId()));
        payload.put("activityId", String.valueOf(order.getActivityId()));
        payload.put("payAmount", order.getPayAmount());
        payload.put("status", ORDER_SUCCESS);
        payload.put("userCouponId", String.valueOf(coupon.getUserCouponId()));
        payload.put("paidAt", toOffsetString(now));
        insertOutbox("OrderPaid", String.valueOf(order.getOrderId()), payload, now);
    }

    private void insertOrderClosedOutbox(VoucherOrder order, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", String.valueOf(order.getOrderId()));
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", String.valueOf(order.getUserId()));
        payload.put("activityId", String.valueOf(order.getActivityId()));
        payload.put("closedAt", toOffsetString(now));
        insertOutbox("OrderClosed", String.valueOf(order.getOrderId()), payload, now);
    }

    private void insertOrderCancelledOutbox(VoucherOrder order, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", String.valueOf(order.getOrderId()));
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", String.valueOf(order.getUserId()));
        payload.put("activityId", String.valueOf(order.getActivityId()));
        payload.put("cancelledAt", toOffsetString(now));
        insertOutbox("OrderCancelled", String.valueOf(order.getOrderId()), payload, now);
    }

    private void insertCouponIssuedOutbox(UserCoupon coupon, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userCouponId", String.valueOf(coupon.getUserCouponId()));
        payload.put("orderId", String.valueOf(coupon.getSourceOrderId()));
        payload.put("userId", String.valueOf(coupon.getUserId()));
        payload.put("activityId", String.valueOf(coupon.getActivityId()));
        payload.put("templateId", String.valueOf(coupon.getTemplateId()));
        payload.put("validEndAt", toOffsetString(coupon.getValidEndAt()));
        insertOutbox("CouponIssued", String.valueOf(coupon.getUserCouponId()), payload, now);
    }

    private void insertTimeoutOutbox(VoucherOrder order, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", String.valueOf(order.getOrderId()));
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", String.valueOf(order.getUserId()));
        payload.put("activityId", String.valueOf(order.getActivityId()));
        payload.put("expireAt", toOffsetString(order.getExpireAt()));
        insertOutbox("OrderTimeoutCheck", String.valueOf(order.getOrderId()), payload, now);
    }

    private void insertPushOutbox(
            String sceneBiz,
            Long targetUserId,
            Long orderId,
            Long userCouponId,
            String title,
            String body,
            LocalDateTime now
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", String.valueOf(orderId));
        if (userCouponId != null) {
            data.put("userCouponId", String.valueOf(userCouponId));
        }
        data.put("jumpType", "ORDER");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", "push_req_order_" + sceneBiz + "_" + orderId);
        payload.put("sourceService", "bluenote-order");
        payload.put("sourceBizType", sceneBiz);
        payload.put("sourceBizId", String.valueOf(orderId));
        payload.put("scene", "ORDER_STATUS");
        payload.put("targetUserId", String.valueOf(targetUserId));
        payload.put("targetDevicePolicy", "ALL_ACTIVE_DEVICES");
        payload.put("deliveryStrategy", "ONLINE_THEN_OFFLINE");
        payload.put("priority", 7);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("data", data);
        payload.put("expireAt", toOffsetString(now.plusMinutes(30)));
        insertOutbox("PushSendRequested", "push_req_order_" + sceneBiz + "_" + orderId, payload, now);
    }

    private void assertPreheatAllowed(CouponActivity activity, LocalDateTime now) {
        if (ACTIVITY_PAUSED.equals(activity.getActivityStatus())
                || ACTIVITY_ENDED.equals(activity.getActivityStatus())
                || ACTIVITY_CANCELLED.equals(activity.getActivityStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        if (activity.getEndAt() == null || !activity.getEndAt().isAfter(now)) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        if (!List.of(ACTIVITY_READY, ACTIVITY_PREHEATED, ACTIVITY_ONLINE).contains(activity.getActivityStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
        StockExpectation stock = stockExpectation(activity);
        if (!stock.consistent(activity)) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
    }

    private void assertPauseAllowed(CouponActivity activity) {
        if (!List.of(ACTIVITY_PREHEATED, ACTIVITY_ONLINE, ACTIVITY_SOLD_OUT).contains(activity.getActivityStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
    }

    private void assertResumeAllowed(CouponActivity activity) {
        if (!ACTIVITY_PAUSED.equals(activity.getActivityStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
    }

    private void assertEndAllowed(CouponActivity activity) {
        if (!List.of(ACTIVITY_READY, ACTIVITY_PREHEATED, ACTIVITY_ONLINE, ACTIVITY_SOLD_OUT, ACTIVITY_PAUSED)
                .contains(activity.getActivityStatus())) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_STATUS_INVALID);
        }
    }

    private void insertOutbox(String eventType, String bizKey, Map<String, Object> payload, LocalDateTime now) {
        String eventId = "evt_" + eventType + "_" + bizKey + "_" + UUID.randomUUID();
        OrderOutboxEvent entity = new OrderOutboxEvent();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(bizKey);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, bizKey, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setNextRetryAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        orderMapper.insertOutbox(entity);
    }

    private Map<String, Object> eventEnvelope(String eventId, String eventType, String bizKey, Map<String, Object> payload, LocalDateTime now) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", toOffsetString(now));
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-order");
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload);
        return envelope;
    }

    private OrderActivityItem toActivityItem(CouponActivity activity, CouponTemplate template, boolean joined, LocalDateTime now) {
        return new OrderActivityItem(
                String.valueOf(activity.getActivityId()),
                activity.getActivityName(),
                template == null ? "" : template.getTemplateName(),
                template == null ? 0 : safeInt(template.getFaceValue()),
                template == null ? 0 : safeInt(template.getThresholdAmount()),
                safeInt(activity.getPayAmount()),
                displayStatus(activity, now),
                toOffsetString(activity.getStartAt()),
                toOffsetString(activity.getEndAt()),
                joined,
                template == null ? null : template.getDescription(),
                toOffsetString(now)
        );
    }

    private SeckillSubmitResponse toSubmitResponse(SeckillRequest request) {
        return new SeckillSubmitResponse(
                String.valueOf(request.getRequestId()),
                request.getRequestStatus(),
                messageForRequestStatus(request.getRequestStatus())
        );
    }

    private SeckillResultResponse toResultResponse(SeckillRequest request) {
        return new SeckillResultResponse(
                String.valueOf(request.getRequestId()),
                request.getRequestStatus(),
                request.getOrderId() == null ? null : String.valueOf(request.getOrderId()),
                request.getUserCouponId() == null ? null : String.valueOf(request.getUserCouponId()),
                safeInt(request.getPayRequired()) == 1,
                safeInt(request.getPayAmount()),
                toOffsetString(request.getExpireAt()),
                request.getResultMessage()
        );
    }

    private OrderDetailResponse toOrderDetail(VoucherOrder order) {
        CouponActivity activity = orderMapper.selectActivityById(order.getActivityId());
        CouponTemplate template = orderMapper.selectTemplateById(order.getTemplateId());
        UserCoupon coupon = orderMapper.selectCouponBySourceOrder(order.getOrderId());
        return new OrderDetailResponse(
                String.valueOf(order.getOrderId()),
                order.getOrderNo(),
                String.valueOf(order.getActivityId()),
                activity == null ? "" : activity.getActivityName(),
                template == null ? "" : template.getTemplateName(),
                template == null ? 0 : safeInt(template.getFaceValue()),
                template == null ? 0 : safeInt(template.getThresholdAmount()),
                safeInt(order.getPayAmount()),
                order.getOrderStatus(),
                toOffsetString(order.getExpireAt()),
                toOffsetString(order.getPaidAt()),
                toOffsetString(order.getSuccessAt()),
                toOffsetString(order.getClosedAt()),
                coupon == null ? null : String.valueOf(coupon.getUserCouponId()),
                toOffsetString(order.getCreatedAt())
        );
    }

    private UserCouponItem toCouponItem(UserCoupon coupon) {
        return new UserCouponItem(
                String.valueOf(coupon.getUserCouponId()),
                String.valueOf(coupon.getTemplateId()),
                coupon.getTemplateName(),
                coupon.getActivityName(),
                safeInt(coupon.getFaceValue()),
                safeInt(coupon.getThresholdAmount()),
                coupon.getCouponStatus(),
                toOffsetString(coupon.getValidStartAt()),
                toOffsetString(coupon.getValidEndAt()),
                toOffsetString(coupon.getIssuedAt())
        );
    }

    private InternalActivityListItem toActivityListItem(CouponActivity activity, CouponTemplate template, LocalDateTime now) {
        return new InternalActivityListItem(
                String.valueOf(activity.getActivityId()),
                activity.getActivityName(),
                String.valueOf(activity.getTemplateId()),
                template == null ? "" : template.getTemplateName(),
                activity.getActivityStatus(),
                displayStatus(activity, now),
                safeInt(activity.getTotalStock()),
                safeInt(activity.getAvailableStock()),
                safeInt(activity.getSoldStock()),
                safeInt(activity.getPayAmount()),
                toOffsetString(activity.getStartAt()),
                toOffsetString(activity.getEndAt()),
                toOffsetString(activity.getPreheatedAt()),
                toOffsetString(activity.getCreatedAt()),
                toOffsetString(activity.getUpdatedAt())
        );
    }

    private OrderActivityPrecheckResponse buildPrecheck(CouponActivity activity, LocalDateTime now) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        CouponTemplate template = orderMapper.selectTemplateById(activity.getTemplateId());
        if (template == null) {
            blockers.add("TEMPLATE_NOT_FOUND");
        } else if (!"ACTIVE".equals(template.getTemplateStatus())) {
            blockers.add("TEMPLATE_INACTIVE");
        }
        if (activity.getStartAt() == null || activity.getEndAt() == null || !activity.getEndAt().isAfter(activity.getStartAt())) {
            blockers.add("ACTIVITY_TIME_INVALID");
        } else if (!activity.getEndAt().isAfter(now)) {
            blockers.add("ACTIVITY_ENDED");
        }
        if (ACTIVITY_ENDED.equals(activity.getActivityStatus()) || ACTIVITY_CANCELLED.equals(activity.getActivityStatus())) {
            blockers.add("ACTIVITY_STATUS_CLOSED");
        }
        if (safeInt(activity.getTotalStock()) <= 0) {
            blockers.add("STOCK_EMPTY");
        }
        StockExpectation stock = stockExpectation(activity);
        boolean stockConsistent = stock.consistent(activity);
        if (!stockConsistent) {
            blockers.add("MYSQL_STOCK_INCONSISTENT");
        }
        if (safeInt(activity.getAvailableStock()) <= 0) {
            warnings.add("STOCK_SOLD_OUT");
        }
        RedisActivitySnapshot redis = redisStore.snapshot(activity.getActivityId());
        if (Boolean.TRUE.equals(redis.rebuilding())) {
            blockers.add("REDIS_REBUILDING");
        }
        boolean shouldHaveRedis = List.of(ACTIVITY_PREHEATED, ACTIVITY_ONLINE, ACTIVITY_SOLD_OUT, ACTIVITY_PAUSED)
                .contains(activity.getActivityStatus());
        if (shouldHaveRedis && !Boolean.TRUE.equals(redis.stockKeyExists())) {
            warnings.add("REDIS_STOCK_KEY_MISSING");
        }
        if (Boolean.TRUE.equals(redis.stockKeyExists())
                && redis.stock() != null
                && redis.stock().intValue() != safeInt(activity.getAvailableStock())) {
            blockers.add("REDIS_STOCK_MISMATCH");
        }
        if (shouldHaveRedis) {
            int expectedParticipants = orderMapper.selectActiveParticipantUserIds(activity.getActivityId()).size();
            if (safeInt(redis.participantCount()) != expectedParticipants) {
                warnings.add("REDIS_PARTICIPANT_MISMATCH");
            }
        }
        return new OrderActivityPrecheckResponse(
                String.valueOf(activity.getActivityId()),
                blockers.isEmpty(),
                blockers,
                warnings,
                activity.getActivityStatus(),
                displayStatus(activity, now),
                safeInt(activity.getTotalStock()),
                safeInt(activity.getAvailableStock()),
                safeInt(activity.getSoldStock()),
                stock.availableStock(),
                stock.soldStock(),
                stockConsistent,
                toRedisSnapshot(redis),
                toOffsetString(now)
        );
    }

    private StockExpectation stockExpectation(CouponActivity activity) {
        int occupied = safeInt(orderMapper.countSuccessfulOrdersByActivity(activity.getActivityId()));
        int soldStock = Math.min(safeInt(activity.getTotalStock()), Math.max(0, occupied));
        int availableStock = Math.max(0, safeInt(activity.getTotalStock()) - soldStock);
        return new StockExpectation(availableStock, soldStock);
    }

    private Map<String, Integer> statusCounts(List<StatusCountRow> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (StatusCountRow row : rows) {
            if (row != null && !isBlank(row.getStatus())) {
                result.put(row.getStatus(), safeInt(row.getCount()));
            }
        }
        return result;
    }

    private OrderRedisSnapshot toRedisSnapshot(RedisActivitySnapshot snapshot) {
        return new OrderRedisSnapshot(
                snapshot.stockKeyExists(),
                snapshot.stock(),
                snapshot.participantCount(),
                snapshot.soldOut(),
                snapshot.rebuilding()
        );
    }

    private String displayStatus(CouponActivity activity, LocalDateTime now) {
        if (ACTIVITY_PAUSED.equals(activity.getActivityStatus())) {
            return ACTIVITY_PAUSED;
        }
        if (ACTIVITY_ENDED.equals(activity.getActivityStatus()) || !activity.getEndAt().isAfter(now)) {
            return ACTIVITY_ENDED;
        }
        if (ACTIVITY_SOLD_OUT.equals(activity.getActivityStatus()) || safeInt(activity.getAvailableStock()) <= 0) {
            return ACTIVITY_SOLD_OUT;
        }
        if (activity.getStartAt().isAfter(now)) {
            return ACTIVITY_PREHEATED.equals(activity.getActivityStatus()) ? ACTIVITY_PREHEATED : "NOT_STARTED";
        }
        if (ACTIVITY_PREHEATED.equals(activity.getActivityStatus()) || ACTIVITY_ONLINE.equals(activity.getActivityStatus())) {
            return ACTIVITY_ONLINE;
        }
        return activity.getActivityStatus();
    }

    private void assertUserAllowed(Long userId) {
        if (!memberClient.orderAllowed(String.valueOf(userId))) {
            throw new BusinessException(ApiErrorCode.ACCOUNT_DISABLED);
        }
    }

    private CouponActivity requireActivity(Long activityId) {
        CouponActivity activity = orderMapper.selectActivityById(activityId);
        if (activity == null) {
            throw new BusinessException(ApiErrorCode.ORDER_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private SeckillRequest requireRequest(String requestId) {
        Long parsed = parseId(requestId, ApiErrorCode.ORDER_REQUEST_NOT_FOUND);
        SeckillRequest request = orderMapper.selectRequestById(parsed);
        if (request == null) {
            throw new BusinessException(ApiErrorCode.ORDER_REQUEST_NOT_FOUND);
        }
        return request;
    }

    private VoucherOrder requireOrder(String orderId) {
        Long parsed = parseId(orderId, ApiErrorCode.ORDER_NOT_FOUND);
        VoucherOrder order = orderMapper.selectOrderById(parsed);
        if (order == null) {
            throw new BusinessException(ApiErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private String messageForRequestStatus(String status) {
        return switch (status) {
            case REQUEST_PROCESSING -> "抢券处理中";
            case REQUEST_SUCCESS -> "神券已到账";
            case REQUEST_WAIT_PAY -> "待支付";
            case REQUEST_SOLD_OUT -> "神券已抢光";
            case REQUEST_DUPLICATE -> "你已参与本次活动";
            case REQUEST_CANCELLED -> "订单已取消";
            case REQUEST_CLOSED -> "订单超时关闭";
            default -> "抢券失败";
        };
    }

    private String orderNo(Long orderId, LocalDateTime now) {
        return "BN" + ORDER_NO_FORMATTER.format(now) + String.valueOf(orderId).substring(Math.max(0, String.valueOf(orderId).length() - 6));
    }

    private Duration redisTtl(CouponActivity activity, LocalDateTime now) {
        LocalDateTime expiresAt = activity.getEndAt().plusMinutes(redisRetainMinutes);
        Duration duration = Duration.between(now, expiresAt);
        return duration.isNegative() || duration.isZero() ? Duration.ofMinutes(redisRetainMinutes) : duration;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    private String toOffsetString(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        ZoneOffset offset = ZONE.getRules().getOffset(time.atZone(ZONE).toInstant());
        return time.atOffset(offset).toString();
    }

    private LocalDateTime parseTime(String value, ApiErrorCode errorCode) {
        if (isBlank(value)) {
            throw new BusinessException(errorCode);
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException exception) {
                throw new BusinessException(errorCode);
            }
        }
    }

    private Long parseId(String value, ApiErrorCode errorCode) {
        if (isBlank(value)) {
            throw new BusinessException(errorCode);
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("id must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private String normalizeChannel(String channel) {
        if (isBlank(channel)) {
            throw new BusinessException(ApiErrorCode.ORDER_PAYMENT_INVALID);
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCouponStatus(String status) {
        if (isBlank(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(COUPON_UNUSED, "USED", "EXPIRED").contains(normalized)) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        return normalized;
    }

    private String normalizeActivityStatus(String status) {
        if (isBlank(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(
                ACTIVITY_READY,
                ACTIVITY_PREHEATED,
                ACTIVITY_ONLINE,
                ACTIVITY_SOLD_OUT,
                ACTIVITY_PAUSED,
                ACTIVITY_ENDED,
                ACTIVITY_CANCELLED
        ).contains(normalized)) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        return normalized;
    }

    private int normalizePageSize(Integer pageSize) {
        int value = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (value <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }

    private CouponCursor parseCouponCursor(String cursor) {
        if (isBlank(cursor)) {
            return null;
        }
        String[] parts = cursor.split("_", 2);
        if (parts.length != 2) {
            throw new BusinessException(ApiErrorCode.ORDER_CURSOR_INVALID);
        }
        try {
            long millis = Long.parseLong(parts[0]);
            Long couponId = parseId(parts[1], ApiErrorCode.ORDER_CURSOR_INVALID);
            LocalDateTime issuedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
            return new CouponCursor(issuedAt, couponId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.ORDER_CURSOR_INVALID);
        }
    }

    private ActivityCursor parseActivityCursor(String cursor) {
        if (isBlank(cursor)) {
            return null;
        }
        String[] parts = cursor.split("_", 2);
        if (parts.length != 2) {
            throw new BusinessException(ApiErrorCode.ORDER_CURSOR_INVALID);
        }
        try {
            long millis = Long.parseLong(parts[0]);
            Long activityId = parseId(parts[1], ApiErrorCode.ORDER_CURSOR_INVALID);
            LocalDateTime createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
            return new ActivityCursor(createdAt, activityId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.ORDER_CURSOR_INVALID);
        }
    }

    private String nextCouponCursor(List<UserCoupon> page, boolean hasMore) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }
        UserCoupon last = page.get(page.size() - 1);
        long millis = last.getIssuedAt().atZone(ZONE).toInstant().toEpochMilli();
        return millis + "_" + last.getUserCouponId();
    }

    private String nextActivityCursor(List<CouponActivity> page, boolean hasMore) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }
        CouponActivity last = page.get(page.size() - 1);
        long millis = last.getCreatedAt().atZone(ZONE).toInstant().toEpochMilli();
        return millis + "_" + last.getActivityId();
    }

    private int positive(Integer value, ApiErrorCode errorCode) {
        if (value == null || value <= 0) {
            throw new BusinessException(errorCode);
        }
        return value;
    }

    private int nonNegative(Integer value, ApiErrorCode errorCode) {
        if (value == null || value < 0) {
            throw new BusinessException(errorCode);
        }
        return value;
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String requireText(String value, int maxLength, ApiErrorCode errorCode) {
        if (isBlank(value) || value.trim().length() > maxLength) {
            throw new BusinessException(errorCode);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String value(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private record CouponCursor(LocalDateTime issuedAt, Long userCouponId) {
    }

    private record ActivityCursor(LocalDateTime createdAt, Long activityId) {
    }

    private record StockExpectation(Integer availableStock, Integer soldStock) {
        private boolean consistent(CouponActivity activity) {
            return availableStock.equals(safe(activity.getAvailableStock()))
                    && soldStock.equals(safe(activity.getSoldStock()));
        }

        private static Integer safe(Integer value) {
            return value == null ? 0 : value;
        }
    }

    private enum SweepResult {
        RETRIED,
        SYNCED,
        SKIPPED
    }
}
