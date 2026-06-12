package com.bluenote.social.push.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.push.api.dto.PushAttemptItem;
import com.bluenote.social.push.api.dto.PushClickRequest;
import com.bluenote.social.push.api.dto.PushClickResponse;
import com.bluenote.social.push.api.dto.PushConsumeEventRequest;
import com.bluenote.social.push.api.dto.PushConsumeEventResponse;
import com.bluenote.social.push.api.dto.PushDeviceItem;
import com.bluenote.social.push.api.dto.PushDeviceListResponse;
import com.bluenote.social.push.api.dto.PushDeviceRegisterRequest;
import com.bluenote.social.push.api.dto.PushDeviceRegisterResponse;
import com.bluenote.social.push.api.dto.PushDeviceUnbindResponse;
import com.bluenote.social.push.api.dto.PushKickRequest;
import com.bluenote.social.push.api.dto.PushKickResponse;
import com.bluenote.social.push.api.dto.PushOnlineStateResponse;
import com.bluenote.social.push.api.dto.PushPreferenceResponse;
import com.bluenote.social.push.api.dto.PushPreferenceUpdateRequest;
import com.bluenote.social.push.api.dto.PushReplayRequest;
import com.bluenote.social.push.api.dto.PushRequestDetailResponse;
import com.bluenote.social.push.api.dto.PushSendRequest;
import com.bluenote.social.push.api.dto.PushSendResponse;
import com.bluenote.social.push.infrastructure.entity.PushClickLogEntity;
import com.bluenote.social.push.infrastructure.entity.PushConsumeRecordEntity;
import com.bluenote.social.push.infrastructure.entity.PushDeliveryAttemptEntity;
import com.bluenote.social.push.infrastructure.entity.PushDeliveryRequestEntity;
import com.bluenote.social.push.infrastructure.entity.PushDeviceEntity;
import com.bluenote.social.push.infrastructure.entity.PushOutboxEventEntity;
import com.bluenote.social.push.infrastructure.entity.PushPreferenceEntity;
import com.bluenote.social.push.infrastructure.mapper.PushClickLogMapper;
import com.bluenote.social.push.infrastructure.mapper.PushConsumeRecordMapper;
import com.bluenote.social.push.infrastructure.mapper.PushDeliveryAttemptMapper;
import com.bluenote.social.push.infrastructure.mapper.PushDeliveryRequestMapper;
import com.bluenote.social.push.infrastructure.mapper.PushDeviceMapper;
import com.bluenote.social.push.infrastructure.mapper.PushOutboxEventMapper;
import com.bluenote.social.push.infrastructure.mapper.PushPreferenceMapper;
import com.bluenote.social.push.infrastructure.realtime.RealtimeDeliveryResult;
import com.bluenote.social.push.infrastructure.realtime.RealtimeMessageSender;
import com.bluenote.social.push.infrastructure.realtime.RealtimeSessionRegistry;
import com.bluenote.social.push.infrastructure.redis.PushRedisStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PushApplicationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEVICE_ACTIVE = "ACTIVE";
    private static final String DEVICE_UNBOUND = "UNBOUND";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_FILTERED = "FILTERED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String CONSUME_PROCESSING = "PROCESSING";
    private static final String CONSUME_SUCCESS = "SUCCESS";
    private static final String CONSUME_SKIPPED = "SKIPPED";
    private static final String CONSUMER_GROUP = "bluenote-push-request-consumer";
    private static final String CHANNEL_WEBSOCKET = "WEBSOCKET";
    private static final String CHANNEL_UNI_PUSH = "UNI_PUSH";
    private static final String CHANNEL_NOOP = "NOOP";
    private static final String ATTEMPT_SENT_TO_CONNECTION = "SENT_TO_CONNECTION";
    private static final String ATTEMPT_SENT_TO_PROVIDER = "SENT_TO_PROVIDER";
    private static final String ATTEMPT_SKIPPED = "SKIPPED";
    private static final String ATTEMPT_FAILED = "FAILED";

    private final PushDeviceMapper deviceMapper;
    private final PushPreferenceMapper preferenceMapper;
    private final PushDeliveryRequestMapper requestMapper;
    private final PushDeliveryAttemptMapper attemptMapper;
    private final PushClickLogMapper clickLogMapper;
    private final PushConsumeRecordMapper consumeRecordMapper;
    private final PushOutboxEventMapper outboxEventMapper;
    private final PushRedisStore redisStore;
    private final RealtimeMessageSender realtimeMessageSender;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;
    private final String websocketUrl;
    private final boolean offlinePushEnabled;

    public PushApplicationService(
            PushDeviceMapper deviceMapper,
            PushPreferenceMapper preferenceMapper,
            PushDeliveryRequestMapper requestMapper,
            PushDeliveryAttemptMapper attemptMapper,
            PushClickLogMapper clickLogMapper,
            PushConsumeRecordMapper consumeRecordMapper,
            PushOutboxEventMapper outboxEventMapper,
            PushRedisStore redisStore,
            RealtimeMessageSender realtimeMessageSender,
            RealtimeSessionRegistry realtimeSessionRegistry,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper,
            @Value("${bluenote.push.realtime.websocket-url:/ws/realtime}") String websocketUrl,
            @Value("${bluenote.push.offline.enabled:false}") boolean offlinePushEnabled
    ) {
        this.deviceMapper = deviceMapper;
        this.preferenceMapper = preferenceMapper;
        this.requestMapper = requestMapper;
        this.attemptMapper = attemptMapper;
        this.clickLogMapper = clickLogMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.redisStore = redisStore;
        this.realtimeMessageSender = realtimeMessageSender;
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
        this.websocketUrl = websocketUrl;
        this.offlinePushEnabled = offlinePushEnabled;
    }

    @Transactional
    public PushDeviceRegisterResponse registerDevice(String userId, PushDeviceRegisterRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String deviceId = normalizeDeviceId(request == null ? null : request.deviceId());
        String platform = normalizePlatform(request.platform());
        String provider = normalizeProvider(request.pushProvider());
        LocalDateTime now = now();

        PushDeviceEntity entity = new PushDeviceEntity();
        entity.setDeviceId(deviceId);
        entity.setUserId(parsedUserId);
        entity.setPlatform(platform);
        entity.setPushProvider(provider);
        entity.setProviderClientId(blankToNull(request.providerClientId()));
        entity.setAppVersion(safeText(request.appVersion(), 64));
        entity.setOsVersion(safeText(request.osVersion(), 64));
        entity.setDeviceModel(safeText(request.deviceModel(), 128));
        entity.setDeviceStatus(DEVICE_ACTIVE);
        entity.setRegisteredAt(now);
        entity.setLastActiveAt(now);
        entity.setUnboundAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        deviceMapper.upsert(entity);
        ensurePreference(parsedUserId, now);
        redisStore.markDeviceActive(parsedUserId, deviceId, now);

        return new PushDeviceRegisterResponse(
                deviceId,
                String.valueOf(parsedUserId),
                platform,
                provider,
                DEVICE_ACTIVE,
                true,
                websocketUrl,
                toOffsetString(now),
                toOffsetString(now)
        );
    }

    @Transactional(readOnly = true)
    public PushDeviceListResponse devices(String userId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        return new PushDeviceListResponse(deviceMapper.selectByUser(parsedUserId).stream()
                .map(device -> new PushDeviceItem(
                        device.getDeviceId(),
                        device.getPlatform(),
                        device.getPushProvider(),
                        device.getDeviceStatus(),
                        device.getAppVersion(),
                        toOffsetString(device.getLastActiveAt())
                ))
                .toList());
    }

    @Transactional
    public PushDeviceUnbindResponse unbindDevice(String userId, String deviceId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LocalDateTime now = now();
        PushDeviceEntity existing = deviceMapper.selectByDeviceId(normalizedDeviceId);
        if (existing == null) {
            throw new BusinessException(ApiErrorCode.PUSH_DEVICE_NOT_FOUND);
        }
        if (!Objects.equals(existing.getUserId(), parsedUserId)) {
            throw new BusinessException(ApiErrorCode.PUSH_DEVICE_NOT_FOUND);
        }
        if (!DEVICE_UNBOUND.equals(existing.getDeviceStatus())) {
            deviceMapper.unbind(normalizedDeviceId, parsedUserId, now, now);
        }
        realtimeSessionRegistry.kick(parsedUserId, normalizedDeviceId, "UNBOUND");
        redisStore.removeDevice(parsedUserId, normalizedDeviceId);
        return new PushDeviceUnbindResponse(normalizedDeviceId, DEVICE_UNBOUND, toOffsetString(now));
    }

    @Transactional(readOnly = true)
    public PushOnlineStateResponse onlineState(String userId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        return realtimeSessionRegistry.onlineState(parsedUserId);
    }

    public PushKickResponse kickUserDevice(String userId, PushKickRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.PARAM_INVALID);
        String deviceId = request == null ? null : request.deviceId();
        boolean kicked = false;
        if (!isBlank(deviceId)) {
            kicked = realtimeSessionRegistry.kick(parsedUserId, normalizeDeviceId(deviceId), request.reason());
        } else {
            for (var connection : realtimeSessionRegistry.onlineConnections(parsedUserId)) {
                kicked = realtimeSessionRegistry.kick(parsedUserId, connection.deviceId(), request == null ? null : request.reason()) || kicked;
            }
        }
        return new PushKickResponse(String.valueOf(parsedUserId), deviceId, kicked);
    }

    @Transactional
    public PushPreferenceResponse preference(String userId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        PushPreferenceEntity entity = ensurePreference(parsedUserId, now());
        cachePreference(entity);
        return toPreferenceResponse(entity);
    }

    @Transactional
    public PushPreferenceResponse updatePreference(String userId, PushPreferenceUpdateRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        LocalDateTime now = now();
        PushPreferenceEntity entity = ensurePreference(parsedUserId, now);
        if (request != null) {
            entity.setGlobalEnabled(mergeFlag(entity.getGlobalEnabled(), request.globalEnabled()));
            entity.setInteractionEnabled(mergeFlag(entity.getInteractionEnabled(), request.interactionEnabled()));
            entity.setFollowEnabled(mergeFlag(entity.getFollowEnabled(), request.followEnabled()));
            entity.setSystemEnabled(mergeFlag(entity.getSystemEnabled(), request.systemEnabled()));
            entity.setOrderEnabled(mergeFlag(entity.getOrderEnabled(), request.orderEnabled()));
            entity.setImEnabled(mergeFlag(entity.getImEnabled(), request.imEnabled()));
            entity.setShowImDetail(mergeFlag(entity.getShowImDetail(), request.showImDetail()));
            entity.setQuietHoursEnabled(mergeFlag(entity.getQuietHoursEnabled(), request.quietHoursEnabled()));
            entity.setQuietStart(normalizeQuietTime(request.quietStart(), entity.getQuietStart()));
            entity.setQuietEnd(normalizeQuietTime(request.quietEnd(), entity.getQuietEnd()));
        }
        entity.setUpdatedAt(now);
        preferenceMapper.update(entity);
        redisStore.evictPreference(parsedUserId);
        cachePreference(entity);
        return toPreferenceResponse(entity);
    }

    @Transactional
    public PushClickResponse recordClick(String userId, PushClickRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        if (request == null || isBlank(request.requestId())) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_INVALID);
        }
        LocalDateTime clickedAt = parseOptionalTime(request.clickedAt(), now());
        PushClickLogEntity entity = new PushClickLogEntity();
        entity.setId(idGenerator.nextId());
        entity.setRequestId(safeRequired(request.requestId(), 128, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setUserId(parsedUserId);
        entity.setDeviceId(request.deviceId() == null ? null : normalizeDeviceId(request.deviceId()));
        entity.setDataJson(json(request.data() == null ? Map.of() : request.data()));
        entity.setClickedAt(clickedAt);
        entity.setCreatedAt(now());
        clickLogMapper.insert(entity);
        return new PushClickResponse(entity.getRequestId(), true);
    }

    @Transactional
    public PushSendResponse send(PushSendRequest request) {
        PushDeliveryRequestEntity entity = persistRequest(request, now());
        return deliver(entity.getRequestId());
    }

    @Transactional(readOnly = true)
    public PushRequestDetailResponse requestDetail(String requestId) {
        PushDeliveryRequestEntity request = requestMapper.selectByRequestId(requestId);
        if (request == null) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_NOT_FOUND);
        }
        return new PushRequestDetailResponse(
                request.getRequestId(),
                request.getRequestStatus(),
                String.valueOf(request.getTargetUserId()),
                request.getSourceBizType(),
                request.getSourceBizId(),
                request.getScene(),
                request.getTitle(),
                request.getBody(),
                request.getDeliveryStrategy(),
                parseJsonMap(request.getDataJson()),
                attemptMapper.selectByRequestId(request.getRequestId()).stream()
                        .map(this::toAttemptItem)
                        .toList(),
                toOffsetString(request.getCreatedAt()),
                toOffsetString(request.getCompletedAt())
        );
    }

    @Transactional
    public PushSendResponse retry(String requestId) {
        PushDeliveryRequestEntity request = requestMapper.selectByRequestId(requestId);
        if (request == null) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_NOT_FOUND);
        }
        return deliver(requestId);
    }

    @Transactional
    public PushConsumeEventResponse consumeEvent(PushConsumeEventRequest request) {
        if (!"PushSendRequested".equals(request.eventType())) {
            return new PushConsumeEventResponse(request.eventId(), request.eventType(), CONSUME_SKIPPED, null);
        }
        LocalDateTime now = now();
        PushConsumeRecordEntity record = reserveConsumeRecord(request, now);
        if (CONSUME_SUCCESS.equals(record.getConsumeStatus())) {
            return new PushConsumeEventResponse(request.eventId(), request.eventType(), CONSUME_SUCCESS, requestId(request.payload()));
        }
        try {
            PushSendRequest sendRequest = sendRequestFromPayload(request.payload());
            PushSendResponse response = send(sendRequest);
            consumeRecordMapper.markSuccess(record.getId(), now(), now());
            return new PushConsumeEventResponse(request.eventId(), request.eventType(), CONSUME_SUCCESS, response.requestId());
        } catch (RuntimeException exception) {
            consumeRecordMapper.markFail(record.getId(), truncate(exception.getMessage(), 512), now());
            throw exception;
        }
    }

    @Transactional
    public PushConsumeEventResponse replay(PushReplayRequest request) {
        if (request == null || isBlank(request.eventId())) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        PushConsumeRecordEntity record = consumeRecordMapper.selectByGroupAndEvent(CONSUMER_GROUP, request.eventId());
        if (record == null || isBlank(record.getEnvelopeJson())) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_NOT_FOUND);
        }
        return consumeEvent(parseConsumeRecord(record));
    }

    private PushDeliveryRequestEntity persistRequest(PushSendRequest request, LocalDateTime now) {
        validateSendRequest(request);
        PushDeliveryRequestEntity entity = new PushDeliveryRequestEntity();
        entity.setRequestId(safeRequired(request.requestId(), 128, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setSourceService(safeRequired(request.sourceService(), 64, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setSourceBizType(safeRequired(request.sourceBizType(), 64, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setSourceBizId(safeRequired(request.sourceBizId(), 128, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setScene(safeRequired(request.scene(), 64, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setTargetUserId(parseId(request.targetUserId(), ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setTargetDevicePolicy(normalizeDevicePolicy(request.targetDevicePolicy()));
        entity.setDeliveryStrategy(normalizeDeliveryStrategy(request.deliveryStrategy()));
        entity.setPriority(request.priority() == null ? 5 : Math.max(1, Math.min(request.priority(), 10)));
        entity.setTitle(safeRequired(request.title(), 128, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setBody(safeRequired(request.body(), 512, ApiErrorCode.PUSH_REQUEST_INVALID));
        entity.setDataJson(json(request.data() == null ? Map.of() : request.data()));
        entity.setRequestStatus(STATUS_RECEIVED);
        entity.setFilteredReason(null);
        entity.setDeliveredDeviceCount(0);
        entity.setExpireAt(parseOptionalTime(request.expireAt(), null));
        entity.setCompletedAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        requestMapper.insertIgnore(entity);
        PushDeliveryRequestEntity stored = requestMapper.selectByRequestId(entity.getRequestId());
        if (stored != null) {
            return stored;
        }
        PushDeliveryRequestEntity sourceExisting = requestMapper.selectBySourceBiz(
                entity.getSourceService(),
                entity.getSourceBizType(),
                entity.getSourceBizId(),
                entity.getScene()
        );
        return sourceExisting == null ? entity : sourceExisting;
    }

    private PushSendResponse deliver(String requestId) {
        LocalDateTime now = now();
        PushDeliveryRequestEntity request = requestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_NOT_FOUND);
        }
        if (request.getExpireAt() != null && request.getExpireAt().isBefore(now)) {
            requestMapper.updateStatus(requestId, STATUS_EXPIRED, "EXPIRED", 0, now, now);
            insertResultOutbox("PushFiltered", request, STATUS_EXPIRED, 0, "EXPIRED", null, now);
            return new PushSendResponse(requestId, STATUS_EXPIRED, 0, "EXPIRED");
        }
        if ("NO_PUSH".equals(request.getDeliveryStrategy())) {
            requestMapper.updateStatus(requestId, STATUS_FILTERED, "NO_PUSH", 0, now, now);
            insertAttempt(request, null, "NOOP", "SKIPPED", "NO_PUSH", null, null, now);
            insertResultOutbox("PushFiltered", request, STATUS_FILTERED, 0, "NO_PUSH", null, now);
            return new PushSendResponse(requestId, STATUS_FILTERED, 0, "NO_PUSH");
        }

        PushPreferenceEntity preference = ensurePreference(request.getTargetUserId(), now);
        String filteredReason = filterReason(request, preference, now);
        if (filteredReason != null) {
            requestMapper.updateStatus(requestId, STATUS_FILTERED, filteredReason, 0, now, now);
            insertAttempt(request, null, "NOOP", "SKIPPED", filteredReason, null, null, now);
            insertResultOutbox("PushFiltered", request, STATUS_FILTERED, 0, filteredReason, null, now);
            return new PushSendResponse(requestId, STATUS_FILTERED, 0, filteredReason);
        }

        List<PushDeviceEntity> devices = deviceMapper.selectActiveByUser(request.getTargetUserId());
        if (devices.isEmpty()) {
            requestMapper.updateStatus(requestId, STATUS_FILTERED, "NO_ACTIVE_DEVICE", 0, now, now);
            insertAttempt(request, null, "NOOP", "SKIPPED", "NO_ACTIVE_DEVICE", null, null, now);
            insertResultOutbox("PushFiltered", request, STATUS_FILTERED, 0, "NO_ACTIVE_DEVICE", null, now);
            return new PushSendResponse(requestId, STATUS_FILTERED, 0, "NO_ACTIVE_DEVICE");
        }

        Map<String, Object> data = parseJsonMap(request.getDataJson());
        int deliveredCount = 0;
        for (PushDeviceEntity device : devices) {
            DeliveryAttemptResult result = deliverToDevice(request, device, data, now);
            deliveredCount += result.delivered() ? 1 : 0;
        }
        if (deliveredCount > 0) {
            requestMapper.updateStatus(requestId, STATUS_DELIVERED, null, deliveredCount, now, now);
            insertResultOutbox("PushDelivered", request, STATUS_DELIVERED, deliveredCount, null, null, now);
            return new PushSendResponse(requestId, STATUS_DELIVERED, deliveredCount, null);
        }
        requestMapper.updateStatus(requestId, STATUS_FAILED, "NO_CHANNEL_AVAILABLE", 0, now, now);
        insertResultOutbox("PushFailed", request, STATUS_FAILED, 0, null, "NO_CHANNEL_AVAILABLE", now);
        return new PushSendResponse(requestId, STATUS_FAILED, 0, "NO_CHANNEL_AVAILABLE");
    }

    private DeliveryAttemptResult deliverToDevice(
            PushDeliveryRequestEntity request,
            PushDeviceEntity device,
            Map<String, Object> data,
            LocalDateTime now
    ) {
        String strategy = request.getDeliveryStrategy();
        boolean tryOnline = "ONLINE_ONLY".equals(strategy)
                || "ONLINE_THEN_OFFLINE".equals(strategy)
                || "ONLINE_AND_OFFLINE".equals(strategy);
        boolean tryOffline = "OFFLINE_PUSH_ONLY".equals(strategy)
                || "ONLINE_THEN_OFFLINE".equals(strategy)
                || "ONLINE_AND_OFFLINE".equals(strategy);

        boolean delivered = false;
        if (tryOnline) {
            if (realtimeMessageSender.isOnline(request.getTargetUserId(), device.getDeviceId())) {
                RealtimeDeliveryResult result = realtimeMessageSender.send(request, device.getDeviceId(), data);
                if (result.delivered()) {
                    insertAttempt(request, device.getDeviceId(), CHANNEL_WEBSOCKET, ATTEMPT_SENT_TO_CONNECTION, null,
                            result.connectionId(), null, now);
                    delivered = true;
                } else {
                    insertAttempt(request, device.getDeviceId(), CHANNEL_WEBSOCKET, ATTEMPT_FAILED, null,
                            null, result.errorMessage(), now);
                }
            } else {
                insertAttempt(request, device.getDeviceId(), CHANNEL_WEBSOCKET, ATTEMPT_SKIPPED, "DEVICE_OFFLINE", null, null, now);
            }
        }

        if (tryOffline && (!delivered || "ONLINE_AND_OFFLINE".equals(strategy))) {
            DeliveryAttemptResult offline = deliverOffline(request, device, now);
            delivered = offline.delivered() || delivered;
        }
        return new DeliveryAttemptResult(delivered);
    }

    private DeliveryAttemptResult deliverOffline(PushDeliveryRequestEntity request, PushDeviceEntity device, LocalDateTime now) {
        if (!offlinePushEnabled || "NOOP".equals(device.getPushProvider()) || isBlank(device.getProviderClientId())) {
            insertAttempt(request, device.getDeviceId(), channelFor(device), ATTEMPT_SKIPPED, "OFFLINE_CHANNEL_UNAVAILABLE", null, null, now);
            return new DeliveryAttemptResult(false);
        }
        insertAttempt(request, device.getDeviceId(), channelFor(device), ATTEMPT_SENT_TO_PROVIDER, null, null, null, now);
        return new DeliveryAttemptResult(true);
    }

    private PushConsumeRecordEntity reserveConsumeRecord(PushConsumeEventRequest request, LocalDateTime now) {
        PushConsumeRecordEntity entity = new PushConsumeRecordEntity();
        entity.setId(idGenerator.nextId());
        entity.setConsumerGroup(request.consumerGroup());
        entity.setEventId(request.eventId());
        entity.setTopic(request.topic());
        entity.setEventType(request.eventType());
        entity.setBizKey(request.bizKey());
        entity.setEnvelopeJson(request.envelopeJson());
        entity.setConsumeStatus(CONSUME_PROCESSING);
        entity.setRetryCount(0);
        entity.setErrorMessage(null);
        entity.setConsumedAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            consumeRecordMapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException exception) {
            PushConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(request.consumerGroup(), request.eventId());
            return existing == null ? entity : existing;
        }
    }

    private PushPreferenceEntity ensurePreference(Long userId, LocalDateTime now) {
        PushPreferenceEntity existing = preferenceMapper.selectByUserId(userId);
        if (existing != null) {
            return existing;
        }
        PushPreferenceEntity entity = defaultPreference(userId, now);
        preferenceMapper.insertIgnore(entity);
        PushPreferenceEntity stored = preferenceMapper.selectByUserId(userId);
        return stored == null ? entity : stored;
    }

    private PushPreferenceEntity defaultPreference(Long userId, LocalDateTime now) {
        PushPreferenceEntity entity = new PushPreferenceEntity();
        entity.setUserId(userId);
        entity.setGlobalEnabled(1);
        entity.setInteractionEnabled(1);
        entity.setFollowEnabled(1);
        entity.setSystemEnabled(1);
        entity.setOrderEnabled(1);
        entity.setImEnabled(1);
        entity.setShowImDetail(1);
        entity.setQuietHoursEnabled(0);
        entity.setQuietStart(null);
        entity.setQuietEnd(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private String filterReason(PushDeliveryRequestEntity request, PushPreferenceEntity preference, LocalDateTime now) {
        if (!enabled(preference.getGlobalEnabled())) {
            return "GLOBAL_DISABLED";
        }
        String scene = request.getScene();
        if (scene != null && scene.contains("FOLLOW") && !enabled(preference.getFollowEnabled())) {
            return "FOLLOW_DISABLED";
        }
        if (scene != null && scene.contains("SYSTEM") && !enabled(preference.getSystemEnabled())) {
            return "SYSTEM_DISABLED";
        }
        if (scene != null && scene.contains("ORDER") && !enabled(preference.getOrderEnabled())) {
            return "ORDER_DISABLED";
        }
        if (scene != null && scene.contains("IM") && !enabled(preference.getImEnabled())) {
            return "IM_DISABLED";
        }
        if (isInteractionScene(scene) && !enabled(preference.getInteractionEnabled())) {
            return "INTERACTION_DISABLED";
        }
        if (enabled(preference.getQuietHoursEnabled()) && request.getPriority() != null && request.getPriority() < 8
                && inQuietHours(preference.getQuietStart(), preference.getQuietEnd(), now.toLocalTime())) {
            return "QUIET_HOURS";
        }
        return null;
    }

    private boolean isInteractionScene(String scene) {
        return scene != null && (scene.contains("LIKE")
                || scene.contains("COLLECT")
                || scene.contains("COMMENT")
                || scene.contains("REPLY"));
    }

    private boolean inQuietHours(String start, String end, LocalTime now) {
        if (isBlank(start) || isBlank(end)) {
            return false;
        }
        try {
            LocalTime startTime = LocalTime.parse(start);
            LocalTime endTime = LocalTime.parse(end);
            if (startTime.equals(endTime)) {
                return false;
            }
            if (startTime.isBefore(endTime)) {
                return !now.isBefore(startTime) && now.isBefore(endTime);
            }
            return !now.isBefore(startTime) || now.isBefore(endTime);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private PushSendRequest sendRequestFromPayload(Map<String, Object> payload) {
        return new PushSendRequest(
                stringValue(payload.get("requestId")),
                stringValue(payload.get("sourceService")),
                stringValue(payload.get("sourceBizType")),
                stringValue(payload.get("sourceBizId")),
                stringValue(payload.get("scene")),
                stringValue(payload.get("targetUserId")),
                stringValue(payload.get("targetDevicePolicy")),
                stringValue(payload.get("deliveryStrategy")),
                intValue(payload.get("priority")),
                stringValue(payload.get("title")),
                stringValue(payload.get("body")),
                mapValue(payload.get("data")),
                stringValue(payload.get("expireAt"))
        );
    }

    private PushConsumeEventRequest parseConsumeRecord(PushConsumeRecordEntity record) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(record.getEnvelopeJson(), MAP_TYPE);
            return new PushConsumeEventRequest(
                    record.getTopic(),
                    record.getConsumerGroup(),
                    stringValue(envelope.get("eventId")),
                    stringValue(envelope.get("eventType")),
                    intValue(envelope.get("eventVersion")),
                    stringValue(envelope.get("occurredAt")),
                    stringValue(envelope.get("traceId")),
                    stringValue(envelope.get("producer")),
                    stringValue(envelope.get("bizKey")),
                    mapValue(envelope.get("payload")),
                    record.getEnvelopeJson()
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_INVALID);
        }
    }

    private void insertAttempt(
            PushDeliveryRequestEntity request,
            String deviceId,
            String channel,
            String attemptStatus,
            String skipReason,
            String providerMessageId,
            String errorMessage,
            LocalDateTime now
    ) {
        PushDeliveryAttemptEntity attempt = new PushDeliveryAttemptEntity();
        attempt.setAttemptId(idGenerator.nextId());
        attempt.setRequestId(request.getRequestId());
        attempt.setTargetUserId(request.getTargetUserId());
        attempt.setDeviceId(deviceId);
        attempt.setChannel(channel);
        attempt.setAttemptStatus(attemptStatus);
        attempt.setSkipReason(skipReason);
        attempt.setProviderMessageId(providerMessageId);
        attempt.setErrorMessage(truncate(errorMessage, 512));
        attempt.setAckedAt(null);
        attempt.setAttemptedAt(now);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        attemptMapper.insert(attempt);
    }

    private void insertResultOutbox(
            String eventType,
            PushDeliveryRequestEntity request,
            String requestStatus,
            int deliveredDeviceCount,
            String filteredReason,
            String errorMessage,
            LocalDateTime now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.getRequestId());
        payload.put("targetUserId", String.valueOf(request.getTargetUserId()));
        payload.put("sourceBizType", request.getSourceBizType());
        payload.put("sourceBizId", request.getSourceBizId());
        payload.put("scene", request.getScene());
        payload.put("requestStatus", requestStatus);
        payload.put("deliveredDeviceCount", deliveredDeviceCount);
        payload.put("filteredReason", filteredReason);
        if (errorMessage != null) {
            payload.put("errorMessage", truncate(errorMessage, 512));
        }
        payload.put("completedAt", toOffsetString(now));
        insertOutbox(eventType, request.getRequestId(), payload, now);
    }

    private void insertOutbox(String eventType, String bizKey, Map<String, Object> payload, LocalDateTime now) {
        String eventId = "evt_" + eventType + "_" + bizKey + "_" + UUID.randomUUID();
        PushOutboxEventEntity entity = new PushOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(bizKey);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, bizKey, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setNextRetryAt(null);
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
        envelope.put("producer", "bluenote-push");
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload);
        return envelope;
    }

    private void cachePreference(PushPreferenceEntity entity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("globalEnabled", enabled(entity.getGlobalEnabled()));
        values.put("interactionEnabled", enabled(entity.getInteractionEnabled()));
        values.put("followEnabled", enabled(entity.getFollowEnabled()));
        values.put("systemEnabled", enabled(entity.getSystemEnabled()));
        values.put("orderEnabled", enabled(entity.getOrderEnabled()));
        values.put("imEnabled", enabled(entity.getImEnabled()));
        values.put("showImDetail", enabled(entity.getShowImDetail()));
        values.put("quietHoursEnabled", enabled(entity.getQuietHoursEnabled()));
        values.put("quietStart", entity.getQuietStart());
        values.put("quietEnd", entity.getQuietEnd());
        redisStore.cachePreference(entity.getUserId(), values);
    }

    private PushPreferenceResponse toPreferenceResponse(PushPreferenceEntity entity) {
        return new PushPreferenceResponse(
                enabled(entity.getGlobalEnabled()),
                enabled(entity.getInteractionEnabled()),
                enabled(entity.getFollowEnabled()),
                enabled(entity.getSystemEnabled()),
                enabled(entity.getOrderEnabled()),
                enabled(entity.getImEnabled()),
                enabled(entity.getShowImDetail()),
                enabled(entity.getQuietHoursEnabled()),
                entity.getQuietStart(),
                entity.getQuietEnd(),
                toOffsetString(entity.getUpdatedAt())
        );
    }

    private PushAttemptItem toAttemptItem(PushDeliveryAttemptEntity attempt) {
        return new PushAttemptItem(
                String.valueOf(attempt.getAttemptId()),
                attempt.getDeviceId(),
                attempt.getChannel(),
                attempt.getAttemptStatus(),
                attempt.getSkipReason(),
                attempt.getErrorMessage(),
                toOffsetString(attempt.getAttemptedAt()),
                toOffsetString(attempt.getAckedAt())
        );
    }

    private String channelFor(PushDeviceEntity device) {
        if ("UNI_PUSH".equals(device.getPushProvider())) {
            return CHANNEL_UNI_PUSH;
        }
        return "NOOP".equals(device.getPushProvider()) ? CHANNEL_NOOP : device.getPushProvider();
    }

    private void validateSendRequest(PushSendRequest request) {
        if (request == null || isBlank(request.requestId()) || isBlank(request.sourceService())
                || isBlank(request.sourceBizType()) || isBlank(request.sourceBizId())
                || isBlank(request.scene()) || isBlank(request.targetUserId())
                || isBlank(request.title()) || isBlank(request.body())) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_INVALID);
        }
    }

    private String normalizeDeviceId(String deviceId) {
        return safeRequired(deviceId, 128, ApiErrorCode.PUSH_DEVICE_INVALID);
    }

    private String normalizePlatform(String platform) {
        String normalized = safeRequired(platform, 32, ApiErrorCode.PUSH_DEVICE_INVALID).toUpperCase();
        if (!List.of("IOS", "ANDROID", "H5").contains(normalized)) {
            throw new BusinessException(ApiErrorCode.PUSH_DEVICE_INVALID);
        }
        return normalized;
    }

    private String normalizeProvider(String provider) {
        String normalized = isBlank(provider) ? "NOOP" : provider.trim().toUpperCase();
        if (!List.of("UNI_PUSH", "APNS", "FCM", "VENDOR_PUSH", "NOOP").contains(normalized)) {
            throw new BusinessException(ApiErrorCode.PUSH_DEVICE_INVALID);
        }
        return normalized;
    }

    private String normalizeDevicePolicy(String policy) {
        return isBlank(policy) ? "ALL_ACTIVE_DEVICES" : safeText(policy, 64);
    }

    private String normalizeDeliveryStrategy(String strategy) {
        String normalized = isBlank(strategy) ? "ONLINE_THEN_OFFLINE" : strategy.trim().toUpperCase();
        if (!List.of("ONLINE_ONLY", "OFFLINE_PUSH_ONLY", "ONLINE_THEN_OFFLINE", "ONLINE_AND_OFFLINE", "NO_PUSH").contains(normalized)) {
            throw new BusinessException(ApiErrorCode.PUSH_REQUEST_INVALID);
        }
        return normalized;
    }

    private String normalizeQuietTime(String value, String current) {
        if (value == null) {
            return current;
        }
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ApiErrorCode.PUSH_PREFERENCE_INVALID);
        }
    }

    private int mergeFlag(Integer current, Boolean incoming) {
        if (incoming == null) {
            return current == null ? 1 : current;
        }
        return incoming ? 1 : 0;
    }

    private boolean enabled(Integer value) {
        return value != null && value == 1;
    }

    private String requestId(Map<String, Object> payload) {
        return stringValue(payload.get("requestId"));
    }

    private LocalDateTime parseOptionalTime(String value, LocalDateTime fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
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

    private Long parseId(String value, ApiErrorCode errorCode) {
        try {
            if (isBlank(value)) {
                throw new NumberFormatException("blank");
            }
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private String safeRequired(String value, int maxLength, ApiErrorCode errorCode) {
        String text = safeText(value, maxLength);
        if (isBlank(text)) {
            throw new BusinessException(errorCode);
        }
        return text;
    }

    private String safeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            return text.substring(0, maxLength);
        }
        return text;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize push JSON", exception);
        }
    }

    private String toOffsetString(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZONE).toOffsetDateTime().toString();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    private record DeliveryAttemptResult(boolean delivered) {
    }
}
