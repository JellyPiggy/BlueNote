package com.bluenote.social.notification.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.notification.api.dto.NotificationActorSummary;
import com.bluenote.social.notification.api.dto.NotificationBatchSummaryItem;
import com.bluenote.social.notification.api.dto.NotificationBatchSummaryRequest;
import com.bluenote.social.notification.api.dto.NotificationBatchSummaryResponse;
import com.bluenote.social.notification.api.dto.NotificationConsumeEventRequest;
import com.bluenote.social.notification.api.dto.NotificationConsumeEventResponse;
import com.bluenote.social.notification.api.dto.NotificationDeleteBatchRequest;
import com.bluenote.social.notification.api.dto.NotificationDeleteBatchResponse;
import com.bluenote.social.notification.api.dto.NotificationDeleteResponse;
import com.bluenote.social.notification.api.dto.NotificationDetailResponse;
import com.bluenote.social.notification.api.dto.NotificationItem;
import com.bluenote.social.notification.api.dto.NotificationListResponse;
import com.bluenote.social.notification.api.dto.NotificationReadAllRequest;
import com.bluenote.social.notification.api.dto.NotificationReadAllResponse;
import com.bluenote.social.notification.api.dto.NotificationReadResponse;
import com.bluenote.social.notification.api.dto.NotificationRebuildUnreadResponse;
import com.bluenote.social.notification.api.dto.NotificationReplayRequest;
import com.bluenote.social.notification.api.dto.NotificationUnreadCountResponse;
import com.bluenote.social.notification.api.dto.SystemNotificationRequest;
import com.bluenote.social.notification.api.dto.SystemNotificationResponse;
import com.bluenote.social.notification.infrastructure.client.NotificationContentClient;
import com.bluenote.social.notification.infrastructure.client.NotificationContentClient.NoteSummary;
import com.bluenote.social.notification.infrastructure.client.NotificationMemberClient;
import com.bluenote.social.notification.infrastructure.client.NotificationMemberClient.UserSummary;
import com.bluenote.social.notification.infrastructure.entity.NotificationAggregateActorEntity;
import com.bluenote.social.notification.infrastructure.entity.NotificationConsumeRecordEntity;
import com.bluenote.social.notification.infrastructure.entity.NotificationOutboxEventEntity;
import com.bluenote.social.notification.infrastructure.entity.NotificationRecordEntity;
import com.bluenote.social.notification.infrastructure.entity.NotificationUnreadCounterEntity;
import com.bluenote.social.notification.infrastructure.mapper.NotificationAggregateActorMapper;
import com.bluenote.social.notification.infrastructure.mapper.NotificationConsumeRecordMapper;
import com.bluenote.social.notification.infrastructure.mapper.NotificationOutboxEventMapper;
import com.bluenote.social.notification.infrastructure.mapper.NotificationRecordMapper;
import com.bluenote.social.notification.infrastructure.mapper.NotificationUnreadCounterMapper;
import com.bluenote.social.notification.infrastructure.redis.NotificationRedisStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final List<String> CATEGORIES = List.of("INTERACTION", "FOLLOW", "SYSTEM", "ORDER");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int VISIBLE = 1;
    private static final int DELETED = 2;
    private static final int UNREAD = 0;
    private static final int READ = 1;
    private static final int AGGREGATE = 1;
    private static final int DETAIL = 0;
    private static final String CONSUME_PROCESSING = "PROCESSING";
    private static final String CONSUME_SUCCESS = "SUCCESS";
    private static final String CONSUME_SKIPPED = "SKIPPED";

    private final NotificationRecordMapper recordMapper;
    private final NotificationAggregateActorMapper actorMapper;
    private final NotificationUnreadCounterMapper unreadCounterMapper;
    private final NotificationConsumeRecordMapper consumeRecordMapper;
    private final NotificationOutboxEventMapper outboxEventMapper;
    private final NotificationRedisStore redisStore;
    private final NotificationMemberClient memberClient;
    private final NotificationContentClient contentClient;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public NotificationApplicationService(
            NotificationRecordMapper recordMapper,
            NotificationAggregateActorMapper actorMapper,
            NotificationUnreadCounterMapper unreadCounterMapper,
            NotificationConsumeRecordMapper consumeRecordMapper,
            NotificationOutboxEventMapper outboxEventMapper,
            NotificationRedisStore redisStore,
            NotificationMemberClient memberClient,
            NotificationContentClient contentClient,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.recordMapper = recordMapper;
        this.actorMapper = actorMapper;
        this.unreadCounterMapper = unreadCounterMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.redisStore = redisStore;
        this.memberClient = memberClient;
        this.contentClient = contentClient;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse unreadCount(String userId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        return toUnreadResponse(parsedUserId);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(String userId, String category, String cursor, Integer size) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String normalizedCategory = normalizeCategory(category, true);
        int pageSize = normalizeSize(size);
        Cursor parsedCursor = parseCursor(cursor);
        List<NotificationRecordEntity> rows = recordMapper.selectPage(
                parsedUserId,
                normalizedCategory,
                parsedCursor == null ? null : parsedCursor.lastEventAt(),
                parsedCursor == null ? null : parsedCursor.notificationId(),
                pageSize + 1
        );
        boolean hasMore = rows.size() > pageSize;
        List<NotificationRecordEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        Map<Long, UserSummary> userSummaries = loadUserSummaries(page);
        List<NotificationItem> items = page.stream()
                .map(item -> toItem(item, userSummaries))
                .toList();
        return new NotificationListResponse(items, nextCursor(page, hasMore), hasMore);
    }

    @Transactional(readOnly = true)
    public NotificationDetailResponse detail(String userId, String notificationId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        NotificationRecordEntity entity = requireOwnedNotification(parsedUserId, notificationId);
        return new NotificationDetailResponse(
                String.valueOf(entity.getNotificationId()),
                entity.getCategory(),
                entity.getNotificationType(),
                entity.getTitle(),
                entity.getContent(),
                entity.getReadStatus() != null && entity.getReadStatus() == READ,
                parseJsonMap(entity.getSnapshotJson()),
                parseJsonMap(entity.getJumpJson())
        );
    }

    @Transactional
    public NotificationReadResponse markRead(String userId, String notificationId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        NotificationRecordEntity entity = requireOwnedNotification(parsedUserId, notificationId);
        LocalDateTime now = now();
        int updated = recordMapper.markRead(entity.getNotificationId(), parsedUserId, now);
        NotificationUnreadCountResponse unread;
        if (updated > 0) {
            adjustUnread(parsedUserId, entity.getCategory(), -1, now);
            insertNotificationOutbox("NotificationRead", entity, Map.of(
                    "notificationId", String.valueOf(entity.getNotificationId()),
                    "receiverId", String.valueOf(parsedUserId),
                    "category", entity.getCategory(),
                    "readAt", toOffsetString(now)
            ), now);
            unread = toUnreadResponse(parsedUserId);
        } else {
            unread = rebuildUnreadCounts(parsedUserId, now);
        }
        return new NotificationReadResponse(String.valueOf(entity.getNotificationId()), true, unread.totalUnread());
    }

    @Transactional
    public NotificationReadAllResponse markReadAll(String userId, NotificationReadAllRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String category = normalizeCategory(request == null ? null : request.category(), true);
        LocalDateTime now = now();
        int updated = recordMapper.markReadAll(parsedUserId, category, now);
        rebuildUnreadCounts(parsedUserId, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiverId", String.valueOf(parsedUserId));
        payload.put("category", category);
        payload.put("updatedCount", updated);
        payload.put("readAt", toOffsetString(now));
        insertOutboxEnvelope("NotificationReadBatch", String.valueOf(parsedUserId), payload, now);
        NotificationUnreadCountResponse unread = toUnreadResponse(parsedUserId);
        return new NotificationReadAllResponse(updated, unread.totalUnread(), unread.categories());
    }

    @Transactional
    public NotificationDeleteResponse delete(String userId, String notificationId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        NotificationRecordEntity entity = requireOwnedNotification(parsedUserId, notificationId);
        LocalDateTime now = now();
        int updated = recordMapper.markDeleted(entity.getNotificationId(), parsedUserId, now);
        if (updated > 0) {
            if (entity.getReadStatus() != null && entity.getReadStatus() == UNREAD) {
                adjustUnread(parsedUserId, entity.getCategory(), -1, now);
            }
            insertNotificationOutbox("NotificationDeleted", entity, Map.of(
                    "notificationId", String.valueOf(entity.getNotificationId()),
                    "receiverId", String.valueOf(parsedUserId),
                    "category", entity.getCategory(),
                    "deletedAt", toOffsetString(now)
            ), now);
        }
        return new NotificationDeleteResponse(notificationId, true);
    }

    @Transactional
    public NotificationDeleteBatchResponse deleteBatch(String userId, NotificationDeleteBatchRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        if (request == null || request.notificationIds() == null || request.notificationIds().isEmpty()) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        if (request.notificationIds().size() > MAX_SIZE) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_SIZE_EXCEEDED);
        }
        List<Long> ids = new ArrayList<>();
        for (String notificationId : request.notificationIds()) {
            Long parsedNotificationId = parseId(notificationId, ApiErrorCode.NOTIFICATION_NOT_FOUND);
            if (!ids.contains(parsedNotificationId)) {
                ids.add(parsedNotificationId);
            }
        }
        List<NotificationRecordEntity> existing = recordMapper.selectByIds(parsedUserId, ids);
        if (existing.isEmpty()) {
            return new NotificationDeleteBatchResponse(0);
        }
        LocalDateTime now = now();
        int updated = recordMapper.markDeletedBatch(parsedUserId, ids, now);
        rebuildUnreadCounts(parsedUserId, now);
        insertOutboxEnvelope("NotificationDeleted", String.valueOf(parsedUserId), Map.of(
                "receiverId", String.valueOf(parsedUserId),
                "notificationIds", ids.stream().map(String::valueOf).toList(),
                "deletedAt", toOffsetString(now)
        ), now);
        return new NotificationDeleteBatchResponse(updated);
    }

    public NotificationConsumeEventResponse consumeEvent(NotificationConsumeEventRequest request) {
        validateConsumeRequest(request);
        NotificationConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(
                request.consumerGroup(),
                request.eventId()
        );
        if (existing != null && (CONSUME_SUCCESS.equals(existing.getConsumeStatus())
                || CONSUME_SKIPPED.equals(existing.getConsumeStatus()))) {
            return new NotificationConsumeEventResponse(request.eventId(), request.eventType(), existing.getConsumeStatus(), null);
        }
        NotificationConsumeRecordEntity record = transactionTemplate.execute(status -> reserveConsumeRecord(request, now()));
        if (record == null) {
            throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
        }
        try {
            NotificationResult result = transactionTemplate.execute(status -> {
                LocalDateTime processTime = now();
                NotificationResult processResult = dispatchEvent(request, processTime);
                if (processResult.skipped()) {
                    consumeRecordMapper.markSkipped(record.getId(), processResult.reason(), processTime, processTime);
                } else {
                    consumeRecordMapper.markSuccess(record.getId(), processTime, processTime);
                }
                return processResult;
            });
            if (result == null) {
                throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
            }
            return new NotificationConsumeEventResponse(
                    request.eventId(),
                    request.eventType(),
                    result.skipped() ? CONSUME_SKIPPED : CONSUME_SUCCESS,
                    result.notificationId() == null ? null : String.valueOf(result.notificationId())
            );
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> consumeRecordMapper.markFail(
                    record.getId(),
                    truncate(exception.getMessage(), 512),
                    now()
            ));
            throw exception;
        }
    }

    private NotificationResult dispatchEvent(NotificationConsumeEventRequest request, LocalDateTime now) {
        return switch (request.eventType()) {
            case "NoteLiked" -> consumeNoteInteraction(request, "NOTE_LIKED", now);
            case "NoteCollected" -> consumeNoteInteraction(request, "NOTE_COLLECTED", now);
            case "CommentCreated" -> consumeCommentCreated(request, now);
            case "UserFollowed" -> consumeUserFollowed(request, now);
            case "NoteStatusChanged" -> consumeNoteStatusChanged(request, now);
            case "OrderCreated" -> consumeOrderCreated(request, now);
            case "OrderPaid" -> NotificationResult.skipped("coupon issued event handles paid notification");
            case "CouponIssued" -> consumeCouponIssued(request, now);
            case "OrderClosed" -> consumeOrderClosed(request, now);
            case "OrderCancelled" -> consumeOrderCancelled(request, now);
            default -> NotificationResult.skipped("unsupported event type");
        };
    }

    @Transactional
    public SystemNotificationResponse createSystemNotification(SystemNotificationRequest request) {
        if (request == null || request.receiverIds() == null || request.receiverIds().isEmpty()) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_SYSTEM_REQUEST_INVALID);
        }
        if (!List.of("SYSTEM_ANNOUNCEMENT", "NOTE_AUDIT_REJECTED", "NOTE_OFFLINE").contains(request.notificationType())) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_SYSTEM_REQUEST_INVALID);
        }
        LocalDateTime now = now();
        LocalDateTime expireAt = parseOptionalDateTime(request.expireAt());
        List<String> notificationIds = new ArrayList<>();
        int createdCount = 0;
        for (String receiverId : request.receiverIds()) {
            Long parsedReceiverId = parseId(receiverId, ApiErrorCode.NOTIFICATION_SYSTEM_REQUEST_INVALID);
            NotificationRecordEntity existing = recordMapper.selectByReceiverSource(
                    parsedReceiverId,
                    "SYSTEM",
                    request.requestId(),
                    request.notificationType()
            );
            if (existing != null) {
                notificationIds.add(String.valueOf(existing.getNotificationId()));
                continue;
            }
            NotificationBuild build = new NotificationBuild(
                    parsedReceiverId,
                    null,
                    "SYSTEM",
                    request.notificationType(),
                    "SYSTEM",
                    request.requestId(),
                    "SYSTEM",
                    request.requestId(),
                    false,
                    null,
                    safeText(request.title(), 128),
                    safeText(request.content(), 512),
                    Map.of("target", Map.of("targetType", "SYSTEM", "targetId", request.requestId())),
                    request.jump() == null ? Map.of("page", "SYSTEM_NOTICE", "noticeId", request.requestId()) : request.jump(),
                    now,
                    expireAt,
                    Boolean.TRUE.equals(request.pushRequired())
            );
            NotificationRecordEntity entity = createDetailNotification(build, now);
            notificationIds.add(String.valueOf(entity.getNotificationId()));
            createdCount++;
        }
        return new SystemNotificationResponse(request.requestId(), createdCount, notificationIds);
    }

    @Transactional(readOnly = true)
    public NotificationBatchSummaryResponse batchSummary(NotificationBatchSummaryRequest request) {
        if (request == null || request.notificationIds() == null || request.notificationIds().isEmpty()) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        if (request.notificationIds().size() > 100) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        List<NotificationBatchSummaryItem> items = new ArrayList<>();
        for (String notificationId : request.notificationIds()) {
            Long parsedId = parseId(notificationId, ApiErrorCode.PARAM_INVALID);
            NotificationRecordEntity entity = recordMapper.selectById(parsedId);
            if (entity == null) {
                items.add(new NotificationBatchSummaryItem(notificationId, null, null, null, null, null, "NOT_FOUND"));
            } else {
                items.add(new NotificationBatchSummaryItem(
                        String.valueOf(entity.getNotificationId()),
                        String.valueOf(entity.getReceiverId()),
                        entity.getCategory(),
                        entity.getNotificationType(),
                        entity.getTitle(),
                        entity.getContent(),
                        entity.getVisibleStatus() != null && entity.getVisibleStatus() == VISIBLE ? "FOUND" : "HIDDEN"
                ));
            }
        }
        return new NotificationBatchSummaryResponse(items);
    }

    @Transactional
    public NotificationRebuildUnreadResponse rebuildUnread(String userId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.PARAM_INVALID);
        LocalDateTime now = now();
        NotificationUnreadCountResponse response = rebuildUnreadCounts(parsedUserId, now);
        return new NotificationRebuildUnreadResponse(String.valueOf(parsedUserId), true, response.totalUnread());
    }

    @Transactional
    public NotificationConsumeEventResponse replay(NotificationReplayRequest request) {
        if (request == null || request.eventId() == null || request.eventId().isBlank()) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        NotificationConsumeRecordEntity record = consumeRecordMapper.selectByGroupAndEvent(
                replayConsumerGroup(request.topic()),
                request.eventId()
        );
        if (record == null || record.getEnvelopeJson() == null || record.getEnvelopeJson().isBlank()) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_NOT_FOUND);
        }
        NotificationConsumeEventRequest consumeRequest = parseConsumeRecord(record);
        return consumeEvent(consumeRequest);
    }

    private NotificationResult consumeNoteInteraction(
            NotificationConsumeEventRequest request,
            String notificationType,
            LocalDateTime now
    ) {
        Map<String, Object> payload = request.payload();
        Long noteId = requiredId(payload, "noteId");
        Long authorId = requiredId(payload, "authorId");
        Long actorId = requiredId(payload, "userId");
        if (Objects.equals(authorId, actorId)) {
            return NotificationResult.skipped("self interaction");
        }
        NotificationSnapshot snapshot = buildSnapshot(actorId, noteId, null, null, authorId);
        String actorName = actorName(snapshot.actor(), actorId);
        String titleVerb = "NOTE_LIKED".equals(notificationType) ? "点赞" : "收藏";
        String aggregateKey = authorId + ":" + notificationType + ":" + noteId;
        NotificationBuild build = new NotificationBuild(
                authorId,
                actorId,
                "INTERACTION",
                notificationType,
                "NOTE",
                String.valueOf(noteId),
                "INTERACTION",
                actorId + ":" + noteId + ":" + notificationType,
                true,
                aggregateKey,
                actorName + titleVerb + "了你的笔记",
                noteTitle(snapshot.note(), noteId),
                snapshot.toMap(null),
                noteJump(noteId, null),
                parseEventTime(request.occurredAt(), now),
                null,
                true
        );
        NotificationRecordEntity entity = createOrUpdateAggregate(build, now);
        return NotificationResult.success(entity.getNotificationId());
    }

    private NotificationResult consumeCommentCreated(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        if (!"VISIBLE".equals(stringValue(payload.get("commentStatus")))) {
            return NotificationResult.skipped("comment not visible");
        }
        Long commentId = requiredId(payload, "commentId");
        Long noteId = requiredId(payload, "noteId");
        Long noteAuthorId = requiredId(payload, "noteAuthorId");
        Long actorId = requiredId(payload, "userId");
        Integer level = intValue(payload.get("level"));
        Long replyToUserId = nullableId(payload.get("replyToUserId"));
        boolean reply = level != null && level >= 2 && replyToUserId != null;
        Long receiverId = reply ? replyToUserId : noteAuthorId;
        if (Objects.equals(receiverId, actorId)) {
            return NotificationResult.skipped("self comment");
        }
        String contentPreview = safeText(stringValue(payload.get("contentPreview")), 128);
        NotificationSnapshot snapshot = buildSnapshot(actorId, noteId, commentId, contentPreview, receiverId);
        String actorName = actorName(snapshot.actor(), actorId);
        String notificationType = reply ? "COMMENT_REPLIED" : "NOTE_COMMENTED";
        NotificationBuild build = new NotificationBuild(
                receiverId,
                actorId,
                "INTERACTION",
                notificationType,
                "NOTE",
                String.valueOf(noteId),
                "COMMENT",
                String.valueOf(commentId),
                false,
                null,
                actorName + (reply ? "回复了你的评论" : "评论了你的笔记"),
                contentPreview,
                snapshot.toMap(Map.of("commentId", String.valueOf(commentId), "summary", contentPreview)),
                noteJump(noteId, commentId),
                parseEventTime(request.occurredAt(), now),
                null,
                true
        );
        NotificationRecordEntity entity = createDetailNotification(build, now);
        return NotificationResult.success(entity.getNotificationId());
    }

    private NotificationResult consumeUserFollowed(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long followerId = requiredId(payload, "followerId");
        Long followeeId = requiredId(payload, "followeeId");
        if (Objects.equals(followerId, followeeId)) {
            return NotificationResult.skipped("self follow");
        }
        UserSummary actor = memberClient.batchSummary(List.of(String.valueOf(followerId))).get(String.valueOf(followerId));
        Map<String, Object> actorSnapshot = actorMap(actor, followerId);
        Map<String, Object> targetSnapshot = new LinkedHashMap<>();
        targetSnapshot.put("targetType", "USER");
        targetSnapshot.put("targetId", String.valueOf(followerId));
        targetSnapshot.put("nickname", actorSnapshot.get("nickname"));
        targetSnapshot.put("avatarUrl", actorSnapshot.get("avatarUrl"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("actor", actorSnapshot);
        snapshot.put("target", targetSnapshot);
        String actorName = actorName(actor, followerId);
        NotificationBuild build = new NotificationBuild(
                followeeId,
                followerId,
                "FOLLOW",
                "USER_FOLLOWED",
                "USER",
                String.valueOf(followerId),
                "FOLLOW",
                followerId + ":" + followeeId,
                false,
                null,
                actorName + "关注了你",
                "查看对方主页",
                snapshot,
                Map.of("page", "USER_PROFILE", "userId", String.valueOf(followerId)),
                parseEventTime(request.occurredAt(), now),
                null,
                true
        );
        NotificationRecordEntity entity = createDetailNotification(build, now);
        return NotificationResult.success(entity.getNotificationId());
    }

    private NotificationResult consumeNoteStatusChanged(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long noteId = requiredId(payload, "noteId");
        Long authorId = requiredId(payload, "authorId");
        String toStatus = stringValue(payload.get("toStatus"));
        String reason = stringValue(payload.get("reason"));
        String notificationType;
        String title;
        if ("AUDIT_REJECTED".equals(toStatus)) {
            notificationType = "NOTE_AUDIT_REJECTED";
            title = "你的笔记审核未通过";
        } else if ("OFFLINE".equals(toStatus)) {
            notificationType = "NOTE_OFFLINE";
            title = "你的笔记已被下架";
        } else {
            return NotificationResult.skipped("note status no notification");
        }
        NoteSummary note = contentClient.batchNoteSummary(List.of(String.valueOf(noteId)), String.valueOf(authorId), true)
                .get(String.valueOf(noteId));
        String content = reason == null || reason.isBlank() ? noteTitle(note, noteId) : safeText(reason, 512);
        NotificationBuild build = new NotificationBuild(
                authorId,
                null,
                "SYSTEM",
                notificationType,
                "NOTE",
                String.valueOf(noteId),
                "NOTE_STATUS",
                request.eventId(),
                false,
                null,
                title,
                content,
                Map.of("target", noteTarget(note, noteId)),
                Map.of("page", "NOTE_DETAIL", "noteId", String.valueOf(noteId)),
                parseEventTime(request.occurredAt(), now),
                null,
                true
        );
        NotificationRecordEntity entity = createDetailNotification(build, now);
        return NotificationResult.success(entity.getNotificationId());
    }

    private NotificationResult consumeOrderCreated(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        String status = stringValue(payload.get("status"));
        if ("WAIT_PAY".equals(status)) {
            Long userId = requiredId(payload, "userId");
            Long orderId = requiredId(payload, "orderId");
            return createOrderStatusNotification(
                    request,
                    now,
                    userId,
                    orderId,
                    "WAIT_PAY",
                    "神券待支付",
                    orderContent(payload, "你抢到的神券需要完成支付"),
                    null
            );
        }
        if ("SUCCESS".equals(status)) {
            return NotificationResult.skipped("coupon issued event handles success notification");
        }
        return NotificationResult.skipped("order created status no notification");
    }

    private NotificationResult consumeCouponIssued(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long userId = requiredId(payload, "userId");
        Long orderId = requiredId(payload, "orderId");
        Long userCouponId = requiredId(payload, "userCouponId");
        return createOrderStatusNotification(
                request,
                now,
                userId,
                orderId,
                "SUCCESS",
                "神券已到账",
                orderContent(payload, "你抢到的 BlueNote 神券已放入卡包"),
                userCouponId
        );
    }

    private NotificationResult consumeOrderClosed(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long userId = requiredId(payload, "userId");
        Long orderId = requiredId(payload, "orderId");
        return createOrderStatusNotification(
                request,
                now,
                userId,
                orderId,
                "CLOSED",
                "订单已关闭",
                orderContent(payload, "待支付订单已超时关闭"),
                null
        );
    }

    private NotificationResult consumeOrderCancelled(NotificationConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long userId = requiredId(payload, "userId");
        Long orderId = requiredId(payload, "orderId");
        return createOrderStatusNotification(
                request,
                now,
                userId,
                orderId,
                "CANCELLED",
                "订单已取消",
                orderContent(payload, "你的神券订单已取消"),
                null
        );
    }

    private NotificationResult createOrderStatusNotification(
            NotificationConsumeEventRequest request,
            LocalDateTime now,
            Long receiverId,
            Long orderId,
            String status,
            String title,
            String content,
            Long userCouponId
    ) {
        String sourceId = orderId + ":" + status;
        NotificationRecordEntity existing = recordMapper.selectByReceiverSource(
                receiverId,
                "ORDER",
                sourceId,
                "ORDER_STATUS_CHANGED"
        );
        if (existing != null) {
            return NotificationResult.success(existing.getNotificationId());
        }
        NotificationBuild build = new NotificationBuild(
                receiverId,
                null,
                "ORDER",
                "ORDER_STATUS_CHANGED",
                "ORDER",
                String.valueOf(orderId),
                "ORDER",
                sourceId,
                false,
                null,
                title,
                content,
                orderSnapshot(request.payload(), orderId, status, title, content, userCouponId),
                orderJump(request.payload(), orderId, userCouponId),
                parseEventTime(request.occurredAt(), now),
                null,
                false
        );
        NotificationRecordEntity entity = createDetailNotification(build, now);
        return NotificationResult.success(entity.getNotificationId());
    }

    private NotificationRecordEntity createOrUpdateAggregate(NotificationBuild build, LocalDateTime now) {
        NotificationRecordEntity existing = recordMapper.selectUnreadAggregate(build.receiverId(), build.aggregateKey());
        if (existing == null) {
            NotificationRecordEntity created = createRecord(build, AGGREGATE, now);
            insertAggregateActor(created.getNotificationId(), build.actorId(), build.sourceId(), build.eventTime(), now);
            insertNotificationOutbox("NotificationCreated", created, notificationLifecyclePayload(created, now), now);
            insertPushOutbox(created, now);
            return created;
        }

        insertAggregateActor(existing.getNotificationId(), build.actorId(), build.sourceId(), build.eventTime(), now);
        int actorCount = Math.max(1, actorMapper.countActors(existing.getNotificationId()));
        Map<Long, UserSummary> actors = memberClient.batchSummary(latestActorIds(existing.getNotificationId()).stream()
                .map(String::valueOf)
                .toList())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> Long.valueOf(entry.getKey()), Map.Entry::getValue));
        UserSummary latestActor = actors.get(build.actorId());
        String actorName = actorName(latestActor, build.actorId());
        NotificationRecordEntity updated = new NotificationRecordEntity();
        updated.setNotificationId(existing.getNotificationId());
        updated.setReceiverId(existing.getReceiverId());
        updated.setActorId(build.actorId());
        updated.setActorCount(actorCount);
        updated.setTitle(aggregateTitle(actorName, actorCount, build.notificationType()));
        updated.setContent(build.content());
        updated.setSnapshotJson(json(build.snapshot()));
        updated.setJumpJson(json(build.jump()));
        updated.setLastEventAt(build.eventTime());
        updated.setUpdatedAt(now);
        recordMapper.updateAggregate(updated);
        NotificationRecordEntity current = recordMapper.selectById(existing.getNotificationId());
        insertNotificationOutbox("NotificationAggregated", current, notificationLifecyclePayload(current, now), now);
        insertPushOutbox(current, now);
        return current;
    }

    private NotificationRecordEntity createDetailNotification(NotificationBuild build, LocalDateTime now) {
        NotificationRecordEntity entity = createRecord(build, DETAIL, now);
        insertNotificationOutbox("NotificationCreated", entity, notificationLifecyclePayload(entity, now), now);
        if (build.pushRequired()) {
            insertPushOutbox(entity, now);
        }
        return entity;
    }

    private NotificationRecordEntity createRecord(NotificationBuild build, int aggregate, LocalDateTime now) {
        NotificationRecordEntity entity = new NotificationRecordEntity();
        entity.setNotificationId(idGenerator.nextId());
        entity.setReceiverId(build.receiverId());
        entity.setActorId(build.actorId());
        entity.setCategory(build.category());
        entity.setNotificationType(build.notificationType());
        entity.setTargetType(build.targetType());
        entity.setTargetId(build.targetId());
        entity.setSourceType(build.sourceType());
        entity.setSourceId(build.sourceId());
        entity.setAggregate(aggregate);
        entity.setAggregateKey(build.aggregateKey());
        entity.setAggregateUnreadKey(aggregate == AGGREGATE ? build.aggregateKey() : null);
        entity.setActorCount(1);
        entity.setTitle(build.title());
        entity.setContent(build.content());
        entity.setSnapshotJson(json(build.snapshot()));
        entity.setJumpJson(json(build.jump()));
        entity.setReadStatus(UNREAD);
        entity.setVisibleStatus(VISIBLE);
        entity.setLastEventAt(build.eventTime());
        entity.setExpireAt(build.expireAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        recordMapper.insert(entity);
        adjustUnread(build.receiverId(), build.category(), 1, now);
        return entity;
    }

    private void insertAggregateActor(Long notificationId, Long actorId, String sourceBizId, LocalDateTime actedAt, LocalDateTime now) {
        if (actorId == null) {
            return;
        }
        NotificationAggregateActorEntity actor = new NotificationAggregateActorEntity();
        actor.setId(idGenerator.nextId());
        actor.setNotificationId(notificationId);
        actor.setActorId(actorId);
        actor.setSourceBizId(sourceBizId);
        actor.setActedAt(actedAt);
        actor.setCreatedAt(now);
        actorMapper.insertIgnore(actor);
    }

    private void adjustUnread(Long userId, String category, long delta, LocalDateTime now) {
        unreadCounterMapper.upsertDelta(userId, category, delta, now);
        refreshUnreadCache(userId);
    }

    private NotificationUnreadCountResponse rebuildUnreadCounts(Long userId, LocalDateTime now) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            long count = recordMapper.countUnreadByCategory(userId, category);
            unreadCounterMapper.upsertValue(userId, category, count, now);
            values.put(category, count);
        }
        values.put("total", values.get("INTERACTION") + values.get("FOLLOW") + values.get("SYSTEM") + values.get("ORDER"));
        redisStore.putUnread(String.valueOf(userId), values);
        return new NotificationUnreadCountResponse(values.get("total"), categoryOnly(values));
    }

    private NotificationUnreadCountResponse toUnreadResponse(Long userId) {
        Map<String, Long> cached = redisStore.getUnread(String.valueOf(userId));
        if (hasAllUnreadFields(cached)) {
            return new NotificationUnreadCountResponse(cached.get("total"), categoryOnly(cached));
        }
        List<NotificationUnreadCounterEntity> rows = unreadCounterMapper.selectByUser(userId);
        Map<String, Long> values = zeroUnreadMap();
        for (NotificationUnreadCounterEntity row : rows) {
            values.put(row.getCategory(), Math.max(0L, row.getUnreadCount()));
        }
        values.put("total", values.get("INTERACTION") + values.get("FOLLOW") + values.get("SYSTEM") + values.get("ORDER"));
        redisStore.putUnread(String.valueOf(userId), values);
        return new NotificationUnreadCountResponse(values.get("total"), categoryOnly(values));
    }

    private void refreshUnreadCache(Long userId) {
        List<NotificationUnreadCounterEntity> rows = unreadCounterMapper.selectByUser(userId);
        Map<String, Long> values = zeroUnreadMap();
        for (NotificationUnreadCounterEntity row : rows) {
            values.put(row.getCategory(), Math.max(0L, row.getUnreadCount()));
        }
        values.put("total", values.get("INTERACTION") + values.get("FOLLOW") + values.get("SYSTEM") + values.get("ORDER"));
        redisStore.putUnread(String.valueOf(userId), values);
    }

    private Map<String, Long> zeroUnreadMap() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("INTERACTION", 0L);
        values.put("FOLLOW", 0L);
        values.put("SYSTEM", 0L);
        values.put("ORDER", 0L);
        values.put("total", 0L);
        return values;
    }

    private Map<String, Long> categoryOnly(Map<String, Long> values) {
        Map<String, Long> categories = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            categories.put(category, Math.max(0L, values.getOrDefault(category, 0L)));
        }
        return categories;
    }

    private boolean hasAllUnreadFields(Map<String, Long> values) {
        return values.containsKey("total") && CATEGORIES.stream().allMatch(values::containsKey);
    }

    private NotificationRecordEntity requireOwnedNotification(Long userId, String notificationId) {
        Long parsedNotificationId = parseId(notificationId, ApiErrorCode.NOTIFICATION_NOT_FOUND);
        NotificationRecordEntity entity = recordMapper.selectById(parsedNotificationId);
        if (entity == null || entity.getVisibleStatus() != VISIBLE) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (!Objects.equals(entity.getReceiverId(), userId)) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_OWNER_FORBIDDEN);
        }
        return entity;
    }

    private NotificationConsumeRecordEntity reserveConsumeRecord(NotificationConsumeEventRequest request, LocalDateTime now) {
        NotificationConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(
                request.consumerGroup(),
                request.eventId()
        );
        if (existing != null) {
            return existing;
        }
        NotificationConsumeRecordEntity record = new NotificationConsumeRecordEntity();
        record.setId(idGenerator.nextId());
        record.setConsumerGroup(request.consumerGroup());
        record.setEventId(request.eventId());
        record.setTopic(request.topic());
        record.setEventType(request.eventType());
        record.setBizKey(request.bizKey());
        record.setEnvelopeJson(json(toEnvelopeMap(request)));
        record.setConsumeStatus(CONSUME_PROCESSING);
        record.setRetryCount(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            consumeRecordMapper.insert(record);
            return record;
        } catch (DuplicateKeyException ignored) {
            return consumeRecordMapper.selectByGroupAndEvent(request.consumerGroup(), request.eventId());
        }
    }

    private void validateConsumeRequest(NotificationConsumeEventRequest request) {
        if (request == null || isBlank(request.eventId()) || isBlank(request.eventType()) || isBlank(request.consumerGroup())) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        if (request.payload() == null) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private NotificationSnapshot buildSnapshot(Long actorId, Long noteId, Long commentId, String commentSummary, Long viewerId) {
        UserSummary actor = actorId == null ? null : memberClient.batchSummary(List.of(String.valueOf(actorId))).get(String.valueOf(actorId));
        NoteSummary note = noteId == null ? null : contentClient.batchNoteSummary(
                List.of(String.valueOf(noteId)),
                viewerId == null ? null : String.valueOf(viewerId),
                true
        ).get(String.valueOf(noteId));
        return new NotificationSnapshot(actor, note, actorId, noteId, commentId, commentSummary);
    }

    private Map<Long, UserSummary> loadUserSummaries(List<NotificationRecordEntity> rows) {
        Set<Long> actorIds = new LinkedHashSet<>();
        for (NotificationRecordEntity row : rows) {
            if (row.getActorId() != null) {
                actorIds.add(row.getActorId());
            }
            if (row.getAggregate() != null && row.getAggregate() == AGGREGATE) {
                actorIds.addAll(latestActorIds(row.getNotificationId()));
            }
        }
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return memberClient.batchSummary(actorIds.stream().map(String::valueOf).toList())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> Long.valueOf(entry.getKey()), Map.Entry::getValue));
    }

    private List<Long> latestActorIds(Long notificationId) {
        return actorMapper.selectLatestActors(notificationId, 3)
                .stream()
                .map(NotificationAggregateActorEntity::getActorId)
                .toList();
    }

    private NotificationItem toItem(NotificationRecordEntity entity, Map<Long, UserSummary> userSummaries) {
        Map<String, Object> snapshot = parseJsonMap(entity.getSnapshotJson());
        Map<String, Object> target = snapshotTarget(snapshot, entity);
        List<NotificationActorSummary> actors = new ArrayList<>();
        if (entity.getAggregate() != null && entity.getAggregate() == AGGREGATE) {
            for (Long actorId : latestActorIds(entity.getNotificationId())) {
                UserSummary summary = userSummaries.get(actorId);
                actors.add(toActorSummary(summary, actorId));
            }
        } else if (entity.getActorId() != null) {
            actors.add(toActorSummary(userSummaries.get(entity.getActorId()), entity.getActorId()));
        }
        return new NotificationItem(
                String.valueOf(entity.getNotificationId()),
                entity.getCategory(),
                entity.getNotificationType(),
                entity.getAggregate() != null && entity.getAggregate() == AGGREGATE,
                entity.getActorCount() == null ? 1 : entity.getActorCount(),
                entity.getTitle(),
                entity.getContent(),
                entity.getReadStatus() != null && entity.getReadStatus() == READ,
                actors,
                target,
                parseJsonMap(entity.getJumpJson()),
                toOffsetString(entity.getCreatedAt()),
                toOffsetString(entity.getLastEventAt())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotTarget(Map<String, Object> snapshot, NotificationRecordEntity entity) {
        Object target = snapshot.get("target");
        if (target instanceof Map<?, ?> targetMap) {
            return (Map<String, Object>) targetMap;
        }
        return Map.of("targetType", entity.getTargetType(), "targetId", entity.getTargetId());
    }

    private NotificationActorSummary toActorSummary(UserSummary summary, Long actorId) {
        return new NotificationActorSummary(
                String.valueOf(actorId),
                summary == null || isBlank(summary.nickname()) ? "用户" + actorId : summary.nickname(),
                summary == null ? null : summary.avatarUrl()
        );
    }

    private NotificationSnapshot snapshotFromRecord(NotificationRecordEntity entity) {
        Map<String, Object> snapshot = parseJsonMap(entity.getSnapshotJson());
        return new NotificationSnapshot(null, null, entity.getActorId(), parseLongOrNull(entity.getTargetId()), null, null);
    }

    private Map<String, Object> notificationLifecyclePayload(NotificationRecordEntity entity, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", String.valueOf(entity.getNotificationId()));
        payload.put("receiverId", String.valueOf(entity.getReceiverId()));
        payload.put("category", entity.getCategory());
        payload.put("notificationType", entity.getNotificationType());
        payload.put("targetType", entity.getTargetType());
        payload.put("targetId", entity.getTargetId());
        payload.put("sourceBizId", entity.getSourceId());
        payload.put("aggregate", entity.getAggregate() != null && entity.getAggregate() == AGGREGATE);
        payload.put("createdAt", toOffsetString(now));
        if (entity.getActorCount() != null) {
            payload.put("actorCount", entity.getActorCount());
        }
        payload.put("lastEventAt", toOffsetString(entity.getLastEventAt()));
        return payload;
    }

    private void insertNotificationOutbox(String eventType, NotificationRecordEntity entity, Map<String, Object> payload, LocalDateTime now) {
        insertOutboxEnvelope(eventType, String.valueOf(entity.getNotificationId()), payload, now);
    }

    private void insertPushOutbox(NotificationRecordEntity entity, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", "push_req_" + entity.getNotificationId());
        payload.put("sourceService", "bluenote-notification");
        payload.put("sourceBizType", "NOTIFICATION");
        payload.put("sourceBizId", String.valueOf(entity.getNotificationId()));
        payload.put("scene", pushScene(entity.getNotificationType()));
        payload.put("targetUserId", String.valueOf(entity.getReceiverId()));
        payload.put("targetDevicePolicy", "ALL_ACTIVE_DEVICES");
        payload.put("deliveryStrategy", "ONLINE_THEN_OFFLINE");
        payload.put("priority", pushPriority(entity.getNotificationType()));
        payload.put("title", entity.getTitle());
        payload.put("body", entity.getContent());
        payload.put("data", Map.of(
                "notificationId", String.valueOf(entity.getNotificationId()),
                "targetType", entity.getTargetType(),
                "targetId", entity.getTargetId()
        ));
        payload.put("expireAt", toOffsetString(now.plusMinutes(10)));
        insertOutboxEnvelope("PushSendRequested", "push_req_" + entity.getNotificationId(), payload, now);
    }

    private void insertOutboxEnvelope(String eventType, String bizKey, Map<String, Object> payload, LocalDateTime now) {
        String eventId = "evt_" + eventType + "_" + bizKey + "_" + UUID.randomUUID();
        NotificationOutboxEventEntity entity = new NotificationOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(bizKey);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, bizKey, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            String bizKey,
            Map<String, Object> payload,
            LocalDateTime occurredAt
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", toOffsetString(occurredAt));
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-notification");
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload);
        return envelope;
    }

    private Map<String, Object> toEnvelopeMap(NotificationConsumeEventRequest request) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", request.eventId());
        envelope.put("eventType", request.eventType());
        envelope.put("eventVersion", request.eventVersion());
        envelope.put("occurredAt", request.occurredAt());
        envelope.put("traceId", request.traceId());
        envelope.put("producer", request.producer());
        envelope.put("bizKey", request.bizKey());
        envelope.put("payload", request.payload());
        envelope.put("_topic", request.topic());
        envelope.put("_consumerGroup", request.consumerGroup());
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private NotificationConsumeEventRequest parseConsumeRecord(NotificationConsumeRecordEntity record) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(record.getEnvelopeJson(), MAP_TYPE);
            Object payload = envelope.get("payload");
            if (!(payload instanceof Map<?, ?> payloadMap)) {
                throw new BusinessException(ApiErrorCode.PARAM_INVALID);
            }
            return new NotificationConsumeEventRequest(
                    record.getTopic(),
                    record.getConsumerGroup(),
                    record.getEventId(),
                    record.getEventType(),
                    intValue(envelope.get("eventVersion")),
                    stringValue(envelope.get("occurredAt")),
                    stringValue(envelope.get("traceId")),
                    stringValue(envelope.get("producer")),
                    record.getBizKey(),
                    (Map<String, Object>) payloadMap
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private String replayConsumerGroup(String topic) {
        return switch (topic) {
            case "interaction-event" -> "bluenote-notification-interaction-consumer";
            case "comment-event" -> "bluenote-notification-comment-consumer";
            case "relation-event" -> "bluenote-notification-relation-consumer";
            case "note-event" -> "bluenote-notification-note-consumer";
            case "order-event" -> "bluenote-notification-order-consumer";
            default -> topic;
        };
    }

    private Map<String, Object> actorMap(UserSummary actor, Long actorId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", String.valueOf(actorId));
        map.put("nickname", actor == null || isBlank(actor.nickname()) ? "用户" + actorId : actor.nickname());
        map.put("avatarUrl", actor == null ? null : actor.avatarUrl());
        return map;
    }

    private Map<String, Object> noteTarget(NoteSummary note, Long noteId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("targetType", "NOTE");
        map.put("targetId", String.valueOf(noteId));
        map.put("title", noteTitle(note, noteId));
        map.put("coverUrl", note == null ? null : note.coverUrl());
        return map;
    }

    private Map<String, Object> noteJump(Long noteId, Long commentId) {
        Map<String, Object> jump = new LinkedHashMap<>();
        jump.put("page", "NOTE_DETAIL");
        jump.put("noteId", String.valueOf(noteId));
        if (commentId != null) {
            jump.put("commentId", String.valueOf(commentId));
        }
        return jump;
    }

    private Map<String, Object> orderSnapshot(
            Map<String, Object> payload,
            Long orderId,
            String status,
            String title,
            String content,
            Long userCouponId
    ) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "ORDER");
        target.put("targetId", String.valueOf(orderId));
        target.put("title", title);
        target.put("summary", content);
        target.put("status", status);
        putText(target, "orderNo", payload.get("orderNo"));
        putText(target, "activityId", payload.get("activityId"));
        putText(target, "payAmount", payload.get("payAmount"));
        putText(target, "validEndAt", payload.get("validEndAt"));
        if (userCouponId != null) {
            target.put("userCouponId", String.valueOf(userCouponId));
        }
        return Map.of("target", target);
    }

    private Map<String, Object> orderJump(Map<String, Object> payload, Long orderId, Long userCouponId) {
        Map<String, Object> jump = new LinkedHashMap<>();
        jump.put("page", "ORDER_ACTIVITY");
        jump.put("orderId", String.valueOf(orderId));
        putText(jump, "activityId", payload.get("activityId"));
        if (userCouponId != null) {
            jump.put("userCouponId", String.valueOf(userCouponId));
        }
        return jump;
    }

    private String orderContent(Map<String, Object> payload, String fallback) {
        String orderNo = stringValue(payload.get("orderNo"));
        if (isBlank(orderNo)) {
            return fallback;
        }
        return safeText("订单 " + orderNo + "，" + fallback, 512);
    }

    private void putText(Map<String, Object> map, String key, Object value) {
        String text = stringValue(value);
        if (!isBlank(text)) {
            map.put(key, text);
        }
    }

    private String actorName(UserSummary actor, Long actorId) {
        return actor == null || isBlank(actor.nickname()) ? "用户" + actorId : actor.nickname();
    }

    private String aggregateTitle(String actorName, int actorCount, String notificationType) {
        String action = "NOTE_COLLECTED".equals(notificationType) ? "收藏" : "点赞";
        if (actorCount <= 1) {
            return actorName + action + "了你的笔记";
        }
        return actorName + "等 " + actorCount + " 人" + action + "了你的笔记";
    }

    private String noteTitle(NoteSummary note, Long noteId) {
        if (note != null && !isBlank(note.title())) {
            return safeText(note.title(), 128);
        }
        if (note != null && !isBlank(note.contentPreview())) {
            return safeText(note.contentPreview(), 128);
        }
        return "笔记 " + noteId;
    }

    private String pushScene(String notificationType) {
        return switch (notificationType) {
            case "NOTE_LIKED" -> "LIKE_NOTIFICATION";
            case "NOTE_COLLECTED" -> "COLLECT_NOTIFICATION";
            case "NOTE_COMMENTED" -> "COMMENT_NOTIFICATION";
            case "COMMENT_REPLIED" -> "REPLY_NOTIFICATION";
            case "USER_FOLLOWED" -> "FOLLOW_NOTIFICATION";
            case "ORDER_STATUS_CHANGED" -> "ORDER_STATUS";
            default -> "SYSTEM_NOTIFICATION";
        };
    }

    private int pushPriority(String notificationType) {
        if ("ORDER_STATUS_CHANGED".equals(notificationType)) {
            return 7;
        }
        return notificationType != null && notificationType.startsWith("NOTE_") && !"NOTE_LIKED".equals(notificationType)
                && !"NOTE_COLLECTED".equals(notificationType)
                ? 7
                : 5;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification JSON", exception);
        }
    }

    private Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = cursor.split("_", 2);
        if (parts.length != 2) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_CURSOR_INVALID);
        }
        try {
            LocalDateTime time = parseCursorTime(parts[0]);
            Long notificationId = Long.valueOf(parts[1]);
            return new Cursor(time, notificationId);
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_CURSOR_INVALID);
        }
    }

    private String nextCursor(List<NotificationRecordEntity> page, boolean hasMore) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }
        NotificationRecordEntity last = page.get(page.size() - 1);
        return toOffsetString(last.getLastEventAt()) + "_" + last.getNotificationId();
    }

    private LocalDateTime parseCursorTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            }
        }
    }

    private String normalizeCategory(String category, boolean allowNull) {
        if (category == null || category.isBlank()) {
            return allowNull ? null : "INTERACTION";
        }
        String normalized = category.trim().toUpperCase();
        if (!CATEGORIES.contains(normalized)) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_CATEGORY_INVALID);
        }
        return normalized;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            throw new BusinessException(ApiErrorCode.NOTIFICATION_SIZE_EXCEEDED);
        }
        return size;
    }

    private Long requiredId(Map<String, Object> payload, String field) {
        Long value = nullableId(payload.get(field));
        if (value == null) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        return value;
    }

    private Long nullableId(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return parseId(text, ApiErrorCode.PARAM_INVALID);
    }

    private Long parseId(String value, ApiErrorCode errorCode) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseEventTime(String value, LocalDateTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return fallback;
            }
        }
    }

    private LocalDateTime parseOptionalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseEventTime(value, null);
    }

    private String toOffsetString(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.ofHours(8)).toString();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    private String safeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Cursor(LocalDateTime lastEventAt, Long notificationId) {
    }

    private record NotificationResult(boolean skipped, String reason, Long notificationId) {

        static NotificationResult success(Long notificationId) {
            return new NotificationResult(false, null, notificationId);
        }

        static NotificationResult skipped(String reason) {
            return new NotificationResult(true, reason, null);
        }
    }

    private record NotificationBuild(
            Long receiverId,
            Long actorId,
            String category,
            String notificationType,
            String targetType,
            String targetId,
            String sourceType,
            String sourceId,
            boolean aggregate,
            String aggregateKey,
            String title,
            String content,
            Map<String, Object> snapshot,
            Map<String, Object> jump,
            LocalDateTime eventTime,
            LocalDateTime expireAt,
            boolean pushRequired
    ) {
    }

    private record NotificationSnapshot(
            UserSummary actor,
            NoteSummary note,
            Long actorId,
            Long noteId,
            Long commentId,
            String commentSummary
    ) {
        Map<String, Object> toMap(Map<String, Object> commentOverride) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("actor", actorMap(actor, actorId));
            snapshot.put("target", noteTarget(note, noteId));
            if (commentOverride != null) {
                snapshot.put("comment", commentOverride);
            } else if (commentId != null) {
                snapshot.put("comment", Map.of(
                        "commentId", String.valueOf(commentId),
                        "summary", commentSummary == null ? "" : commentSummary
                ));
            }
            return snapshot;
        }

        private Map<String, Object> actorMap(UserSummary actor, Long actorId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("userId", String.valueOf(actorId));
            map.put("nickname", actor == null || actor.nickname() == null || actor.nickname().isBlank() ? "用户" + actorId : actor.nickname());
            map.put("avatarUrl", actor == null ? null : actor.avatarUrl());
            return map;
        }

        private Map<String, Object> noteTarget(NoteSummary note, Long noteId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("targetType", "NOTE");
            map.put("targetId", String.valueOf(noteId));
            if (note != null && note.title() != null && !note.title().isBlank()) {
                map.put("title", note.title());
            } else if (note != null && note.contentPreview() != null && !note.contentPreview().isBlank()) {
                map.put("title", note.contentPreview());
            } else {
                map.put("title", "笔记 " + noteId);
            }
            map.put("coverUrl", note == null ? null : note.coverUrl());
            return map;
        }
    }
}
