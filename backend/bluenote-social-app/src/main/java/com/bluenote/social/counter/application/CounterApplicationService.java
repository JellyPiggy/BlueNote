package com.bluenote.social.counter.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterBatchResponse;
import com.bluenote.social.counter.api.dto.CounterConsumeEventRequest;
import com.bluenote.social.counter.api.dto.CounterConsumeEventResponse;
import com.bluenote.social.counter.api.dto.CounterItem;
import com.bluenote.social.counter.api.dto.CounterRebuildProgress;
import com.bluenote.social.counter.api.dto.CounterRebuildTaskResponse;
import com.bluenote.social.counter.api.dto.CounterReconcileRequest;
import com.bluenote.social.counter.api.dto.CounterReconcileResponse;
import com.bluenote.social.counter.api.dto.CounterTarget;
import com.bluenote.social.counter.api.dto.CounterWarmupRequest;
import com.bluenote.social.counter.api.dto.CounterWarmupResponse;
import com.bluenote.social.counter.infrastructure.client.ContentCounterSourceClient;
import com.bluenote.social.counter.infrastructure.entity.CounterConsumeRecordEntity;
import com.bluenote.social.counter.infrastructure.entity.CounterDeltaLogEntity;
import com.bluenote.social.counter.infrastructure.entity.CounterOutboxEventEntity;
import com.bluenote.social.counter.infrastructure.entity.CounterRebuildTaskEntity;
import com.bluenote.social.counter.infrastructure.entity.CounterSnapshotEntity;
import com.bluenote.social.counter.infrastructure.mapper.CounterConsumeRecordMapper;
import com.bluenote.social.counter.infrastructure.mapper.CounterDeltaLogMapper;
import com.bluenote.social.counter.infrastructure.mapper.CounterOutboxEventMapper;
import com.bluenote.social.counter.infrastructure.mapper.CounterRebuildTaskMapper;
import com.bluenote.social.counter.infrastructure.mapper.CounterSnapshotMapper;
import com.bluenote.social.counter.infrastructure.redis.CounterRedisStore;
import com.bluenote.social.relation.application.RelationApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CounterApplicationService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_BATCH_SIZE = 100;
    private static final String TARGET_NOTE = "NOTE";
    private static final String TARGET_USER = "USER";
    private static final String TARGET_COMMENT = "COMMENT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String TASK_RECONCILE = "RECONCILE";
    private static final String TASK_PENDING = "PENDING";
    private static final String CONSUME_SUCCESS = "SUCCESS";
    private static final String CONSUME_PROCESSING = "PROCESSING";
    private static final int MAX_EVENT_ID_LENGTH = 128;

    private final RelationApplicationService relationApplicationService;
    private final ContentCounterSourceClient contentCounterSourceClient;
    private final CounterSnapshotMapper snapshotMapper;
    private final CounterDeltaLogMapper deltaLogMapper;
    private final CounterOutboxEventMapper outboxEventMapper;
    private final CounterConsumeRecordMapper consumeRecordMapper;
    private final CounterRebuildTaskMapper rebuildTaskMapper;
    private final CounterRedisStore redisStore;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;

    public CounterApplicationService(
            RelationApplicationService relationApplicationService,
            ContentCounterSourceClient contentCounterSourceClient,
            CounterSnapshotMapper snapshotMapper,
            CounterDeltaLogMapper deltaLogMapper,
            CounterOutboxEventMapper outboxEventMapper,
            CounterConsumeRecordMapper consumeRecordMapper,
            CounterRebuildTaskMapper rebuildTaskMapper,
            CounterRedisStore redisStore,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper
    ) {
        this.relationApplicationService = relationApplicationService;
        this.contentCounterSourceClient = contentCounterSourceClient;
        this.snapshotMapper = snapshotMapper;
        this.deltaLogMapper = deltaLogMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.rebuildTaskMapper = rebuildTaskMapper;
        this.redisStore = redisStore;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
    }

    public CounterBatchResponse batch(CounterBatchRequest request) {
        if (request.targets().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ApiErrorCode.COUNTER_BATCH_SIZE_EXCEEDED);
        }
        return new CounterBatchResponse(request.targets().stream().map(this::counterItem).toList());
    }

    @Transactional
    public CounterWarmupResponse warmup(CounterWarmupRequest request) {
        if (request.targets().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ApiErrorCode.COUNTER_BATCH_SIZE_EXCEEDED);
        }
        request.targets().forEach(target -> counterItem(new CounterTarget(
                target.targetType(),
                target.targetId(),
                defaultFields(target.targetType())
        )));
        return new CounterWarmupResponse(request.targets().size());
    }

    @Transactional
    public CounterConsumeEventResponse consumeEvent(CounterConsumeEventRequest request) {
        LocalDateTime now = now();
        CounterConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(
                request.consumerGroup(),
                request.eventId()
        );
        if (existing != null && CONSUME_SUCCESS.equals(existing.getConsumeStatus())) {
            return new CounterConsumeEventResponse(request.eventId(), CONSUME_SUCCESS, 0);
        }

        reserveConsumeRecord(request, now);
        try {
            List<CounterDelta> deltas = deltasFromEvent(request);
            for (CounterDelta delta : deltas) {
                applyDelta(delta, request, now);
            }
            consumeRecordMapper.markSuccess(request.consumerGroup(), request.eventId());
            return new CounterConsumeEventResponse(request.eventId(), CONSUME_SUCCESS, deltas.size());
        } catch (RuntimeException exception) {
            consumeRecordMapper.markFail(request.consumerGroup(), request.eventId(), truncate(exception.getMessage(), 512));
            throw exception;
        }
    }

    @Transactional
    public CounterReconcileResponse reconcile(CounterReconcileRequest request) {
        CounterTarget target = new CounterTarget(request.targetType(), request.targetId(), request.fields());
        validateTarget(target);
        LocalDateTime now = now();
        String taskId = "counter_rebuild_" + request.targetType() + "_" + request.targetId() + "_"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(now);
        CounterRebuildTaskEntity task = new CounterRebuildTaskEntity();
        task.setTaskId(taskId);
        task.setTaskType(TASK_RECONCILE);
        task.setTargetType(request.targetType());
        task.setTargetId(parseTargetId(request.targetId()));
        task.setFieldsJson(jsonPayloads.stringify(Map.of("fields", request.fields())));
        task.setTaskStatus(TASK_PENDING);
        task.setProgressJson(progressJson(1, 0, 0));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        rebuildTaskMapper.insert(task);

        runRebuildTask(taskId, target);
        return new CounterReconcileResponse(taskId, rebuildTaskMapper.selectByTaskId(taskId).getTaskStatus());
    }

    public CounterRebuildTaskResponse rebuildTask(String taskId) {
        CounterRebuildTaskEntity task = rebuildTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.COUNTER_REBUILD_TASK_NOT_FOUND);
        }
        return new CounterRebuildTaskResponse(
                task.getTaskId(),
                task.getTaskType(),
                task.getTargetType(),
                String.valueOf(task.getTargetId()),
                task.getTaskStatus(),
                parseProgress(task.getProgressJson())
        );
    }

    private CounterItem counterItem(CounterTarget target) {
        validateTarget(target);
        List<String> fields = target.fields().stream().distinct().toList();
        Map<String, Long> counts = zeroCounts(fields);
        boolean degraded = false;

        List<String> missingFields = new ArrayList<>(fields);
        try {
            Map<String, Long> redisCounts = redisStore.getCounts(target.targetType(), target.targetId(), fields);
            counts.putAll(redisCounts);
            missingFields.removeAll(redisCounts.keySet());
        } catch (RuntimeException exception) {
            degraded = true;
        }

        if (!missingFields.isEmpty()) {
            Map<String, Long> snapshotCounts = snapshotCounts(target, missingFields);
            counts.putAll(snapshotCounts);
            missingFields.removeAll(snapshotCounts.keySet());
            if (!snapshotCounts.isEmpty()) {
                tryPutRedis(target.targetType(), target.targetId(), snapshotCounts);
            }
        }

        if (!missingFields.isEmpty()) {
            SourceCounts sourceCounts = sourceCountsBestEffort(target, missingFields);
            counts.putAll(sourceCounts.counts());
            missingFields.removeAll(sourceCounts.counts().keySet());
            upsertSnapshots(target.targetType(), parseTargetId(target.targetId()), sourceCounts.counts(), now());
            tryPutRedis(target.targetType(), target.targetId(), sourceCounts.counts());
            degraded = degraded || sourceCounts.degraded();
        }

        if (!missingFields.isEmpty()) {
            degraded = true;
        }
        return new CounterItem(target.targetType(), target.targetId(), counts, degraded);
    }

    private void runRebuildTask(String taskId, CounterTarget target) {
        rebuildTaskMapper.markRunning(taskId);
        try {
            Map<String, Long> counts = sourceCounts(target, target.fields().stream().distinct().toList());
            upsertSnapshots(target.targetType(), parseTargetId(target.targetId()), counts, now());
            tryPutRedis(target.targetType(), target.targetId(), counts);
            insertCounterRebuiltOutbox(target, counts, now());
            rebuildTaskMapper.markSuccess(taskId, progressJson(1, 1, 0));
        } catch (RuntimeException exception) {
            rebuildTaskMapper.markFailed(taskId, progressJson(1, 0, 1), truncate(exception.getMessage(), 512));
        }
    }

    private void applyDelta(CounterDelta delta, CounterConsumeEventRequest sourceEvent, LocalDateTime now) {
        CounterDeltaLogEntity entity = new CounterDeltaLogEntity();
        entity.setDeltaId(delta.deltaId());
        entity.setSourceEventId(sourceEvent.eventId());
        entity.setSourceEventType(sourceEvent.eventType());
        entity.setTargetType(delta.targetType());
        entity.setTargetId(delta.targetId());
        entity.setCounterField(delta.counterField());
        entity.setDeltaValue(delta.deltaValue());
        entity.setApplyStatus(STATUS_PENDING);
        entity.setOccurredAt(parseOffsetDateTime(sourceEvent.occurredAt()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        deltaLogMapper.insertIgnore(entity);

        CounterDeltaLogEntity stored = deltaLogMapper.selectByDeltaIdForUpdate(delta.deltaId());
        if (stored != null && STATUS_APPLIED.equals(stored.getApplyStatus())) {
            return;
        }

        ensureSnapshotExists(delta.targetType(), delta.targetId(), delta.counterField(), now);
        snapshotMapper.increment(delta.targetType(), delta.targetId(), delta.counterField(), delta.deltaValue(), now);
        long currentValue = currentSnapshotValue(delta.targetType(), delta.targetId(), delta.counterField());
        tryPutRedisAndMarkDirty(
                delta.targetType(),
                String.valueOf(delta.targetId()),
                Map.of(delta.counterField(), currentValue)
        );
        deltaLogMapper.markApplied(delta.deltaId());
        insertCounterDeltaCreatedOutbox(delta, sourceEvent, now);
        insertCounterChangedOutbox(delta, currentValue, sourceEvent, now);
    }

    private List<CounterDelta> deltasFromEvent(CounterConsumeEventRequest event) {
        Map<String, Object> payload = event.payload();
        return switch (event.eventType()) {
            case "UserFollowed" -> List.of(
                    newDelta(event, TARGET_USER, id(payload, "followerId"), "following_count", 1),
                    newDelta(event, TARGET_USER, id(payload, "followeeId"), "follower_count", 1)
            );
            case "UserUnfollowed" -> List.of(
                    newDelta(event, TARGET_USER, id(payload, "followerId"), "following_count", -1),
                    newDelta(event, TARGET_USER, id(payload, "followeeId"), "follower_count", -1)
            );
            case "NotePublished" -> publicPublished(payload)
                    ? List.of(newDelta(event, TARGET_USER, id(payload, "authorId"), "note_count", 1))
                    : List.of();
            case "NoteDeleted" -> publicPublished(payload)
                    ? List.of(newDelta(event, TARGET_USER, id(payload, "authorId"), "note_count", -1))
                    : List.of();
            case "NoteVisibilityChanged" -> noteVisibilityChangedDeltas(event);
            case "NoteStatusChanged" -> noteStatusChangedDeltas(event);
            case "NoteLiked" -> List.of(
                    newDelta(event, TARGET_NOTE, id(payload, "noteId"), "like_count", 1),
                    newDelta(event, TARGET_USER, id(payload, "authorId"), "liked_count", 1)
            );
            case "NoteUnliked" -> List.of(
                    newDelta(event, TARGET_NOTE, id(payload, "noteId"), "like_count", -1),
                    newDelta(event, TARGET_USER, id(payload, "authorId"), "liked_count", -1)
            );
            case "NoteCollected" -> List.of(newDelta(event, TARGET_NOTE, id(payload, "noteId"), "collect_count", 1));
            case "NoteUncollected" -> List.of(newDelta(event, TARGET_NOTE, id(payload, "noteId"), "collect_count", -1));
            case "CommentCreated" -> commentCreatedDeltas(event);
            case "CommentDeleted" -> commentDeletedDeltas(event);
            case "CommentLiked" -> List.of(newDelta(event, TARGET_COMMENT, id(payload, "commentId"), "like_count", 1));
            case "CommentUnliked" -> List.of(newDelta(event, TARGET_COMMENT, id(payload, "commentId"), "like_count", -1));
            default -> List.of();
        };
    }

    private List<CounterDelta> noteVisibilityChangedDeltas(CounterConsumeEventRequest event) {
        Map<String, Object> payload = event.payload();
        if (!"PUBLISHED".equals(String.valueOf(payload.get("noteStatus")))) {
            return List.of();
        }
        boolean fromPublic = "PUBLIC".equals(String.valueOf(payload.get("fromVisibility")));
        boolean toPublic = "PUBLIC".equals(String.valueOf(payload.get("toVisibility")));
        if (!fromPublic && toPublic) {
            return List.of(newDelta(event, TARGET_USER, id(payload, "authorId"), "note_count", 1));
        }
        if (fromPublic && !toPublic) {
            return List.of(newDelta(event, TARGET_USER, id(payload, "authorId"), "note_count", -1));
        }
        return List.of();
    }

    private List<CounterDelta> noteStatusChangedDeltas(CounterConsumeEventRequest event) {
        Map<String, Object> payload = event.payload();
        if (!"PUBLIC".equals(String.valueOf(payload.get("visibility")))) {
            return List.of();
        }
        boolean fromPublished = "PUBLISHED".equals(String.valueOf(payload.get("fromStatus")));
        boolean toPublished = "PUBLISHED".equals(String.valueOf(payload.get("toStatus")));
        if (!fromPublished && toPublished) {
            return List.of(newDelta(event, TARGET_USER, id(payload, "authorId"), "note_count", 1));
        }
        if (fromPublished && !toPublished) {
            return List.of(newDelta(event, TARGET_USER, id(payload, "authorId"), "note_count", -1));
        }
        return List.of();
    }

    private List<CounterDelta> commentCreatedDeltas(CounterConsumeEventRequest event) {
        Map<String, Object> payload = event.payload();
        List<CounterDelta> deltas = new ArrayList<>();
        deltas.add(newDelta(event, TARGET_NOTE, id(payload, "noteId"), "comment_count", 1));
        if (intValue(payload.get("level")) == 2) {
            deltas.add(newDelta(event, TARGET_COMMENT, id(payload, "rootId"), "reply_count", 1));
        }
        return deltas;
    }

    private List<CounterDelta> commentDeletedDeltas(CounterConsumeEventRequest event) {
        Map<String, Object> payload = event.payload();
        long affectedCommentCount = longValue(payload.getOrDefault("affectedCommentCount", 1));
        long affectedReplyCount = longValue(payload.getOrDefault("affectedReplyCount", 0));
        List<CounterDelta> deltas = new ArrayList<>();
        deltas.add(newDelta(event, TARGET_NOTE, id(payload, "noteId"), "comment_count", -affectedCommentCount));
        if (affectedReplyCount > 0) {
            deltas.add(newDelta(event, TARGET_COMMENT, id(payload, "rootId"), "reply_count", -affectedReplyCount));
        }
        return deltas;
    }

    private CounterDelta newDelta(
            CounterConsumeEventRequest event,
            String targetType,
            long targetId,
            String counterField,
            long deltaValue
    ) {
        String deltaId = event.eventId() + ":" + targetType + ":" + targetId + ":" + counterField;
        return new CounterDelta(deltaId, targetType, targetId, counterField, deltaValue);
    }

    private void reserveConsumeRecord(CounterConsumeEventRequest request, LocalDateTime now) {
        CounterConsumeRecordEntity entity = new CounterConsumeRecordEntity();
        entity.setId(idGenerator.nextId());
        entity.setConsumerGroup(request.consumerGroup());
        entity.setEventId(request.eventId());
        entity.setTopic(request.topic());
        entity.setEventType(request.eventType());
        entity.setBizKey(request.bizKey());
        entity.setConsumeStatus(CONSUME_PROCESSING);
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        consumeRecordMapper.insertIgnore(entity);
    }

    private Map<String, Long> snapshotCounts(CounterTarget target, List<String> fields) {
        Map<String, Long> counts = new LinkedHashMap<>();
        snapshotMapper.selectByTargetAndFields(target.targetType(), parseTargetId(target.targetId()), fields)
                .forEach(item -> counts.put(item.getCounterField(), Math.max(0L, item.getCounterValue())));
        return counts;
    }

    private Map<String, Long> sourceCounts(CounterTarget target, List<String> fields) {
        Map<String, Long> counts = zeroCounts(fields);
        CounterTarget scopedTarget = new CounterTarget(target.targetType(), target.targetId(), fields);
        List<String> relationFields = fieldsForSource(scopedTarget, Source.RELATION);
        if (!relationFields.isEmpty()) {
            counts.putAll(relationCounts(target.targetId(), relationFields));
        }
        List<String> noteFields = fieldsForSource(scopedTarget, Source.NOTE);
        if (!noteFields.isEmpty()) {
            counts.putAll(contentCounterSourceClient.noteCounts(target.targetType(), target.targetId(), noteFields));
        }
        List<String> commentFields = fieldsForSource(scopedTarget, Source.COMMENT);
        if (!commentFields.isEmpty()) {
            counts.putAll(contentCounterSourceClient.commentCounts(target.targetType(), target.targetId(), commentFields));
        }
        return counts;
    }

    private SourceCounts sourceCountsBestEffort(CounterTarget target, List<String> fields) {
        Map<String, Long> counts = new LinkedHashMap<>();
        boolean degraded = false;
        CounterTarget scopedTarget = new CounterTarget(target.targetType(), target.targetId(), fields);

        List<String> relationFields = fieldsForSource(scopedTarget, Source.RELATION);
        if (!relationFields.isEmpty()) {
            try {
                counts.putAll(relationCounts(target.targetId(), relationFields));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        List<String> noteFields = fieldsForSource(scopedTarget, Source.NOTE);
        if (!noteFields.isEmpty()) {
            try {
                counts.putAll(contentCounterSourceClient.noteCounts(target.targetType(), target.targetId(), noteFields));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        List<String> commentFields = fieldsForSource(scopedTarget, Source.COMMENT);
        if (!commentFields.isEmpty()) {
            try {
                counts.putAll(contentCounterSourceClient.commentCounts(target.targetType(), target.targetId(), commentFields));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        if (!counts.keySet().containsAll(fields)) {
            degraded = true;
        }
        return new SourceCounts(counts, degraded);
    }

    private void upsertSnapshots(String targetType, Long targetId, Map<String, Long> counts, LocalDateTime now) {
        counts.forEach((field, value) -> {
            CounterSnapshotEntity entity = new CounterSnapshotEntity();
            entity.setId(idGenerator.nextId());
            entity.setTargetType(targetType);
            entity.setTargetId(targetId);
            entity.setCounterField(field);
            entity.setCounterValue(Math.max(0L, value));
            entity.setSnapshotVersion(1L);
            entity.setFlushedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            snapshotMapper.upsert(entity);
        });
    }

    private void ensureSnapshotExists(String targetType, Long targetId, String field, LocalDateTime now) {
        CounterSnapshotEntity entity = new CounterSnapshotEntity();
        entity.setId(idGenerator.nextId());
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setCounterField(field);
        entity.setCounterValue(0L);
        entity.setSnapshotVersion(1L);
        entity.setFlushedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        snapshotMapper.insertIgnore(entity);
    }

    private long currentSnapshotValue(String targetType, Long targetId, String field) {
        return snapshotMapper.selectByTargetAndFields(targetType, targetId, List.of(field)).stream()
                .findFirst()
                .map(CounterSnapshotEntity::getCounterValue)
                .map(value -> Math.max(0L, value))
                .orElse(0L);
    }

    private void insertCounterDeltaCreatedOutbox(
            CounterDelta delta,
            CounterConsumeEventRequest sourceEvent,
            LocalDateTime now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deltaId", delta.deltaId());
        payload.put("sourceEventId", sourceEvent.eventId());
        payload.put("sourceEventType", sourceEvent.eventType());
        payload.put("targetType", delta.targetType());
        payload.put("targetId", String.valueOf(delta.targetId()));
        payload.put("counterField", delta.counterField());
        payload.put("deltaValue", delta.deltaValue());
        insertOutbox("CounterDeltaCreated", eventId("evt_counter_delta", delta.deltaId()), delta.aggregateId(), payload, now);
    }

    private void insertCounterChangedOutbox(
            CounterDelta delta,
            long currentValue,
            CounterConsumeEventRequest sourceEvent,
            LocalDateTime now
    ) {
        Map<String, Object> fieldChange = new LinkedHashMap<>();
        fieldChange.put("delta", delta.deltaValue());
        fieldChange.put("currentValue", currentValue);
        Map<String, Object> changedFields = new LinkedHashMap<>();
        changedFields.put(delta.counterField(), fieldChange);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", delta.targetType());
        payload.put("targetId", String.valueOf(delta.targetId()));
        payload.put("changedFields", changedFields);
        payload.put("sourceEventId", sourceEvent.eventId());
        insertOutbox(
                "CounterChanged",
                eventId("evt_counter_changed", delta.targetType() + ":" + delta.targetId() + ":"
                        + delta.counterField() + ":" + sourceEvent.eventId()),
                delta.aggregateId(),
                payload,
                now
        );
    }

    private void insertCounterRebuiltOutbox(CounterTarget target, Map<String, Long> counts, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", target.targetType());
        payload.put("targetId", target.targetId());
        payload.put("counts", counts);
        payload.put("rebuiltAt", toOffsetString(now));
        insertOutbox(
                "CounterRebuilt",
                eventId("evt_counter_rebuilt", target.targetType() + ":" + target.targetId() + ":"
                        + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(now)),
                target.targetType() + ":" + target.targetId(),
                payload,
                now
        );
    }

    private void insertOutbox(
            String eventType,
            String eventId,
            String aggregateId,
            Map<String, Object> payload,
            LocalDateTime now
    ) {
        CounterOutboxEventEntity entity = new CounterOutboxEventEntity();
        entity.setEventId(normalizeEventId(eventId));
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(entity.getEventId(), eventType, aggregateId, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            String aggregateId,
            Map<String, Object> payload,
            LocalDateTime occurredAt
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", toOffsetString(occurredAt));
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-counter");
        envelope.put("bizKey", aggregateId);
        envelope.put("payload", payload);
        return envelope;
    }

    private void validateTarget(CounterTarget target) {
        parseTargetId(target.targetId());
        if (!List.of(TARGET_NOTE, TARGET_USER, TARGET_COMMENT).contains(target.targetType())) {
            throw new BusinessException(ApiErrorCode.COUNTER_TARGET_TYPE_UNSUPPORTED);
        }
        for (String field : target.fields()) {
            if (!fieldSupported(target.targetType(), field)) {
                throw new BusinessException(ApiErrorCode.COUNTER_FIELD_NOT_SUPPORTED);
            }
        }
    }

    private Long parseTargetId(String targetId) {
        try {
            if (targetId == null || targetId.isBlank()) {
                throw new NumberFormatException("blank");
            }
            return Long.valueOf(targetId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.COUNTER_TARGET_ID_INVALID);
        }
    }

    private boolean fieldSupported(String targetType, String field) {
        return switch (targetType) {
            case TARGET_NOTE -> List.of("like_count", "collect_count", "comment_count").contains(field);
            case TARGET_USER -> List.of("following_count", "follower_count", "note_count", "liked_count").contains(field);
            case TARGET_COMMENT -> List.of("like_count", "reply_count").contains(field);
            default -> false;
        };
    }

    private List<String> defaultFields(String targetType) {
        return switch (targetType) {
            case TARGET_NOTE -> List.of("like_count", "collect_count", "comment_count");
            case TARGET_USER -> List.of("following_count", "follower_count", "note_count", "liked_count");
            case TARGET_COMMENT -> List.of("like_count", "reply_count");
            default -> throw new BusinessException(ApiErrorCode.COUNTER_TARGET_TYPE_UNSUPPORTED);
        };
    }

    private List<String> fieldsForSource(CounterTarget target, Source source) {
        return target.fields().stream()
                .distinct()
                .filter(field -> belongsToSource(target.targetType(), field, source))
                .toList();
    }

    private boolean belongsToSource(String targetType, String field, Source source) {
        return switch (source) {
            case RELATION -> TARGET_USER.equals(targetType)
                    && List.of("following_count", "follower_count").contains(field);
            case NOTE -> (TARGET_NOTE.equals(targetType) && List.of("like_count", "collect_count").contains(field))
                    || (TARGET_USER.equals(targetType) && List.of("note_count", "liked_count").contains(field));
            case COMMENT -> (TARGET_NOTE.equals(targetType) && "comment_count".equals(field))
                    || (TARGET_COMMENT.equals(targetType) && List.of("like_count", "reply_count").contains(field));
        };
    }

    private Map<String, Long> relationCounts(String targetId, List<String> fields) {
        var request = new com.bluenote.social.relation.api.dto.CounterSourceRequest(List.of(
                new com.bluenote.social.relation.api.dto.CounterSourceTarget(TARGET_USER, targetId, fields)
        ));
        return relationApplicationService.counterSource(request).items().stream()
                .findFirst()
                .map(com.bluenote.social.relation.api.dto.CounterSourceItem::counts)
                .orElseGet(Map::of);
    }

    private Map<String, Long> zeroCounts(List<String> fields) {
        Map<String, Long> counts = new LinkedHashMap<>();
        fields.stream().distinct().forEach(field -> counts.put(field, 0L));
        return counts;
    }

    private void tryPutRedis(String targetType, String targetId, Map<String, Long> counts) {
        tryPutRedis(targetType, targetId, counts, false);
    }

    private void tryPutRedisAndMarkDirty(String targetType, String targetId, Map<String, Long> counts) {
        tryPutRedis(targetType, targetId, counts, true);
    }

    private void tryPutRedis(String targetType, String targetId, Map<String, Long> counts, boolean markDirty) {
        if (counts.isEmpty()) {
            return;
        }
        runAfterCommit(() -> {
            try {
                redisStore.putCounts(targetType, targetId, counts);
                if (markDirty) {
                    redisStore.markDirty(targetType, targetId);
                }
            } catch (RuntimeException ignored) {
                // Redis is an online read model; MySQL snapshot remains the durable source.
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private boolean publicPublished(Map<String, Object> payload) {
        return "PUBLIC".equals(String.valueOf(payload.get("visibility")))
                && "PUBLISHED".equals(String.valueOf(payload.get("noteStatus")));
    }

    private long id(Map<String, Object> payload, String field) {
        return longValue(payload.get(field));
    }

    private int intValue(Object value) {
        return (int) longValue(value);
    }

    private long longValue(Object value) {
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.COUNTER_TARGET_ID_INVALID);
        }
    }

    private LocalDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
        } catch (RuntimeException exception) {
            return now();
        }
    }

    private CounterRebuildProgress parseProgress(String progressJson) {
        try {
            Map<String, Integer> progress = objectMapper.readValue(progressJson, new TypeReference<>() {
            });
            return new CounterRebuildProgress(
                    progress.getOrDefault("total", 0),
                    progress.getOrDefault("success", 0),
                    progress.getOrDefault("failed", 0)
            );
        } catch (JsonProcessingException exception) {
            return new CounterRebuildProgress(0, 0, 0);
        }
    }

    private String progressJson(int total, int success, int failed) {
        return jsonPayloads.stringify(Map.of("total", total, "success", success, "failed", failed));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String toOffsetString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(CHINA_ZONE).toOffsetDateTime().toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String eventId(String prefix, String raw) {
        return normalizeEventId(prefix + "_" + raw);
    }

    private String normalizeEventId(String raw) {
        String sanitized = raw.replaceAll("[^A-Za-z0-9_:-]", "_");
        if (sanitized.length() <= MAX_EVENT_ID_LENGTH) {
            return sanitized;
        }
        String hash = sha256(sanitized).substring(0, 16);
        int prefixLength = MAX_EVENT_ID_LENGTH - hash.length() - 1;
        return sanitized.substring(0, prefixLength) + "_" + hash;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append("%02x".formatted(item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private enum Source {
        RELATION,
        NOTE,
        COMMENT
    }

    private record CounterDelta(
            String deltaId,
            String targetType,
            Long targetId,
            String counterField,
            Long deltaValue
    ) {
        private String aggregateId() {
            return targetType + ":" + targetId;
        }
    }

    private record SourceCounts(
            Map<String, Long> counts,
            boolean degraded
    ) {
    }
}
