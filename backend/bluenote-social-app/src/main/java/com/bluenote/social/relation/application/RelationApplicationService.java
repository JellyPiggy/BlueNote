package com.bluenote.social.relation.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.relation.api.dto.BatchFollowStatusResponse;
import com.bluenote.social.relation.api.dto.CounterSourceItem;
import com.bluenote.social.relation.api.dto.CounterSourceRequest;
import com.bluenote.social.relation.api.dto.CounterSourceResponse;
import com.bluenote.social.relation.api.dto.FollowActionResponse;
import com.bluenote.social.relation.api.dto.FollowStatusItem;
import com.bluenote.social.relation.api.dto.FollowStatusResponse;
import com.bluenote.social.relation.api.dto.InternalFollowerPageItem;
import com.bluenote.social.relation.api.dto.InternalFollowingPageItem;
import com.bluenote.social.relation.api.dto.InternalRelationPageResponse;
import com.bluenote.social.relation.api.dto.RelationCursorPage;
import com.bluenote.social.relation.api.dto.RelationUserItem;
import com.bluenote.social.relation.api.dto.RelationUserSummary;
import com.bluenote.social.relation.infrastructure.client.MemberInternalClient;
import com.bluenote.social.relation.infrastructure.entity.RelationChangeLogEntity;
import com.bluenote.social.relation.infrastructure.entity.RelationFollowerEntity;
import com.bluenote.social.relation.infrastructure.entity.RelationFollowingEntity;
import com.bluenote.social.relation.infrastructure.entity.RelationOutboxEventEntity;
import com.bluenote.social.relation.infrastructure.mapper.RelationChangeLogMapper;
import com.bluenote.social.relation.infrastructure.mapper.RelationFollowerMapper;
import com.bluenote.social.relation.infrastructure.mapper.RelationFollowingMapper;
import com.bluenote.social.relation.infrastructure.mapper.RelationOutboxEventMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelationApplicationService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String FOLLOWING = "FOLLOWING";
    private static final String NOT_FOLLOWING = "NOT_FOLLOWING";
    private static final String UNKNOWN = "UNKNOWN";

    private final RelationFollowingMapper followingMapper;
    private final RelationFollowerMapper followerMapper;
    private final RelationChangeLogMapper changeLogMapper;
    private final RelationOutboxEventMapper outboxEventMapper;
    private final MemberInternalClient memberInternalClient;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;

    public RelationApplicationService(
            RelationFollowingMapper followingMapper,
            RelationFollowerMapper followerMapper,
            RelationChangeLogMapper changeLogMapper,
            RelationOutboxEventMapper outboxEventMapper,
            MemberInternalClient memberInternalClient,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads
    ) {
        this.followingMapper = followingMapper;
        this.followerMapper = followerMapper;
        this.changeLogMapper = changeLogMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.memberInternalClient = memberInternalClient;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
    }

    @Transactional
    public FollowActionResponse follow(String currentUserId, String followeeId) {
        Long followerId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedFolloweeId = parseId(followeeId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        if (followerId.equals(parsedFolloweeId)) {
            throw new BusinessException(ApiErrorCode.RELATION_SELF_FOLLOW_FORBIDDEN);
        }
        memberInternalClient.ensureActorAllowed(currentUserId);
        memberInternalClient.ensureTargetFollowable(followeeId);

        RelationFollowingEntity existing = followingMapper.selectByPair(followerId, parsedFolloweeId);
        if (existing == null) {
            return createFollow(followerId, parsedFolloweeId);
        }
        if (STATUS_ACTIVE.equals(existing.getRelationStatus())) {
            return toActionResponse(existing, FOLLOWING);
        }
        return reactivateFollow(existing);
    }

    @Transactional
    public FollowActionResponse unfollow(String currentUserId, String followeeId) {
        Long followerId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedFolloweeId = parseId(followeeId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        if (followerId.equals(parsedFolloweeId)) {
            throw new BusinessException(ApiErrorCode.RELATION_SELF_FOLLOW_FORBIDDEN);
        }

        RelationFollowingEntity existing = followingMapper.selectByPair(followerId, parsedFolloweeId);
        if (existing == null || STATUS_CANCELED.equals(existing.getRelationStatus())) {
            return new FollowActionResponse(currentUserId, followeeId, NOT_FOLLOWING, null, toOffsetString(
                    existing == null ? null : existing.getCanceledAt()
            ));
        }

        LocalDateTime now = now();
        long nextVersion = existing.getRelationVersion() + 1;
        followingMapper.cancel(existing.getId(), now, nextVersion, now);
        RelationFollowingEntity updated = followingMapper.selectByPair(followerId, parsedFolloweeId);
        syncFollowerReadModel(updated, now);
        insertChangeLog(updated, "UNFOLLOW", STATUS_ACTIVE, STATUS_CANCELED, now);
        insertOutbox("UserUnfollowed", updated, Map.of(
                "followerId", String.valueOf(followerId),
                "followeeId", String.valueOf(parsedFolloweeId),
                "canceledAt", toOffsetString(now),
                "relationVersion", nextVersion
        ), now);
        return toActionResponse(updated, NOT_FOLLOWING);
    }

    @Transactional(readOnly = true)
    public RelationCursorPage<RelationUserItem> followingList(String viewerId, String userId, String cursor, Integer size) {
        Long followerId = parseId(userId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size, 50, 20);
        List<RelationFollowingEntity> relations = followingMapper.selectFollowingPage(
                followerId,
                pageCursor.sortAt(),
                pageCursor.userId(),
                pageSize + 1
        );
        return toFollowingUserPage(viewerId, relations, pageSize);
    }

    @Transactional(readOnly = true)
    public RelationCursorPage<RelationUserItem> followersList(String viewerId, String userId, String cursor, Integer size) {
        Long followeeId = parseId(userId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size, 50, 20);
        List<RelationFollowerEntity> relations = followerMapper.selectFollowersPage(
                followeeId,
                pageCursor.sortAt(),
                pageCursor.userId(),
                pageSize + 1
        );
        return toFollowerUserPage(viewerId, relations, pageSize);
    }

    @Transactional(readOnly = true)
    public FollowStatusResponse followStatus(String currentUserId, String targetUserId) {
        Long followerId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long followeeId = parseId(targetUserId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        RelationFollowingEntity relation = followingMapper.selectByPair(followerId, followeeId);
        return new FollowStatusResponse(
                currentUserId,
                targetUserId,
                active(relation) ? FOLLOWING : NOT_FOLLOWING,
                active(relation) ? toOffsetString(relation.getFollowedAt()) : null
        );
    }

    @Transactional(readOnly = true)
    public BatchFollowStatusResponse batchStatus(String currentUserId, List<String> targetUserIds) {
        Long followerId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        if (targetUserIds.size() > 100) {
            throw new BusinessException(ApiErrorCode.RELATION_BATCH_SIZE_EXCEEDED);
        }
        List<Long> parsedTargets = targetUserIds.stream()
                .map(targetUserId -> parseId(targetUserId, ApiErrorCode.PARAM_INVALID))
                .distinct()
                .toList();
        Map<String, RelationFollowingEntity> activeRelations = parsedTargets.isEmpty()
                ? Map.of()
                : followingMapper.selectActiveByTargets(followerId, parsedTargets).stream()
                        .collect(Collectors.toMap(
                                relation -> String.valueOf(relation.getFolloweeId()),
                                Function.identity()
                        ));
        List<FollowStatusItem> items = targetUserIds.stream()
                .map(targetUserId -> {
                    RelationFollowingEntity relation = activeRelations.get(targetUserId);
                    return new FollowStatusItem(
                            targetUserId,
                            relation == null ? NOT_FOLLOWING : FOLLOWING,
                            relation == null ? null : toOffsetString(relation.getFollowedAt())
                    );
                })
                .toList();
        return new BatchFollowStatusResponse(items);
    }

    @Transactional(readOnly = true)
    public InternalRelationPageResponse<InternalFollowerPageItem> internalFollowersPage(
            String userId,
            String cursor,
            Integer size
    ) {
        Long followeeId = parseId(userId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size, 1000, 500);
        List<RelationFollowerEntity> relations = followerMapper.selectFollowersPage(
                followeeId,
                pageCursor.sortAt(),
                pageCursor.userId(),
                pageSize + 1
        );
        boolean hasMore = relations.size() > pageSize;
        List<RelationFollowerEntity> pageItems = hasMore ? relations.subList(0, pageSize) : relations;
        String nextCursor = relationCursor(pageItems, RelationFollowerEntity::getFollowedAt, RelationFollowerEntity::getFollowerId, hasMore);
        return new InternalRelationPageResponse<>(
                pageItems.stream()
                        .map(item -> new InternalFollowerPageItem(String.valueOf(item.getFollowerId()), toOffsetString(item.getFollowedAt())))
                        .toList(),
                nextCursor,
                hasMore
        );
    }

    @Transactional(readOnly = true)
    public InternalRelationPageResponse<InternalFollowingPageItem> internalFollowingPage(
            String userId,
            String cursor,
            Integer size
    ) {
        Long followerId = parseId(userId, ApiErrorCode.RELATION_TARGET_NOT_FOUND);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size, 1000, 500);
        List<RelationFollowingEntity> relations = followingMapper.selectFollowingPage(
                followerId,
                pageCursor.sortAt(),
                pageCursor.userId(),
                pageSize + 1
        );
        boolean hasMore = relations.size() > pageSize;
        List<RelationFollowingEntity> pageItems = hasMore ? relations.subList(0, pageSize) : relations;
        String nextCursor = relationCursor(pageItems, RelationFollowingEntity::getFollowedAt, RelationFollowingEntity::getFolloweeId, hasMore);
        return new InternalRelationPageResponse<>(
                pageItems.stream()
                        .map(item -> new InternalFollowingPageItem(String.valueOf(item.getFolloweeId()), toOffsetString(item.getFollowedAt())))
                        .toList(),
                nextCursor,
                hasMore
        );
    }

    @Transactional(readOnly = true)
    public CounterSourceResponse counterSource(CounterSourceRequest request) {
        List<CounterSourceItem> items = request.targets().stream()
                .map(target -> {
                    if (!"USER".equals(target.targetType())) {
                        throw new BusinessException(ApiErrorCode.PARAM_INVALID);
                    }
                    Long userId = parseId(target.targetId(), ApiErrorCode.PARAM_INVALID);
                    Map<String, Long> counts = new LinkedHashMap<>();
                    for (String field : target.fields()) {
                        if ("following_count".equals(field)) {
                            counts.put(field, followingMapper.countActiveByFollower(userId));
                        } else if ("follower_count".equals(field)) {
                            counts.put(field, followerMapper.countActiveByFollowee(userId));
                        }
                    }
                    return new CounterSourceItem(target.targetType(), target.targetId(), counts);
                })
                .toList();
        return new CounterSourceResponse(items);
    }

    private FollowActionResponse createFollow(Long followerId, Long followeeId) {
        LocalDateTime now = now();
        RelationFollowingEntity entity = new RelationFollowingEntity();
        entity.setId(idGenerator.nextId());
        entity.setFollowerId(followerId);
        entity.setFolloweeId(followeeId);
        entity.setRelationStatus(STATUS_ACTIVE);
        entity.setFollowedAt(now);
        entity.setRelationVersion(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            followingMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            RelationFollowingEntity duplicate = followingMapper.selectByPair(followerId, followeeId);
            if (duplicate != null && STATUS_ACTIVE.equals(duplicate.getRelationStatus())) {
                return toActionResponse(duplicate, FOLLOWING);
            }
            if (duplicate != null) {
                return reactivateFollow(duplicate);
            }
            throw exception;
        }
        syncFollowerReadModel(entity, now);
        insertChangeLog(entity, "FOLLOW", null, STATUS_ACTIVE, now);
        insertOutbox("UserFollowed", entity, Map.of(
                "followerId", String.valueOf(followerId),
                "followeeId", String.valueOf(followeeId),
                "followedAt", toOffsetString(now),
                "relationVersion", 1L
        ), now);
        return toActionResponse(entity, FOLLOWING);
    }

    private FollowActionResponse reactivateFollow(RelationFollowingEntity existing) {
        LocalDateTime now = now();
        long nextVersion = existing.getRelationVersion() + 1;
        followingMapper.activate(existing.getId(), now, nextVersion, now);
        RelationFollowingEntity updated = followingMapper.selectByPair(existing.getFollowerId(), existing.getFolloweeId());
        syncFollowerReadModel(updated, now);
        insertChangeLog(updated, "FOLLOW", STATUS_CANCELED, STATUS_ACTIVE, now);
        insertOutbox("UserFollowed", updated, Map.of(
                "followerId", String.valueOf(updated.getFollowerId()),
                "followeeId", String.valueOf(updated.getFolloweeId()),
                "followedAt", toOffsetString(now),
                "relationVersion", nextVersion
        ), now);
        return toActionResponse(updated, FOLLOWING);
    }

    private void syncFollowerReadModel(RelationFollowingEntity source, LocalDateTime now) {
        RelationFollowerEntity entity = new RelationFollowerEntity();
        entity.setId(idGenerator.nextId());
        entity.setFolloweeId(source.getFolloweeId());
        entity.setFollowerId(source.getFollowerId());
        entity.setRelationStatus(source.getRelationStatus());
        entity.setFollowedAt(source.getFollowedAt());
        entity.setCanceledAt(source.getCanceledAt());
        entity.setRelationVersion(source.getRelationVersion());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        followerMapper.upsert(entity);
    }

    private void insertChangeLog(
            RelationFollowingEntity relation,
            String actionType,
            String beforeStatus,
            String afterStatus,
            LocalDateTime now
    ) {
        RelationChangeLogEntity entity = new RelationChangeLogEntity();
        entity.setChangeId(idGenerator.nextId());
        entity.setFollowerId(relation.getFollowerId());
        entity.setFolloweeId(relation.getFolloweeId());
        entity.setActionType(actionType);
        entity.setBeforeStatus(beforeStatus);
        entity.setAfterStatus(afterStatus);
        entity.setRelationVersion(relation.getRelationVersion());
        entity.setTraceId(TraceIdHolder.currentOrNew());
        entity.setCreatedAt(now);
        changeLogMapper.insert(entity);
    }

    private void insertOutbox(
            String eventType,
            RelationFollowingEntity relation,
            Map<String, Object> payload,
            LocalDateTime now
    ) {
        String eventId = UUID.randomUUID().toString();
        RelationOutboxEventEntity entity = new RelationOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(relation.getFollowerId());
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, relation, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            RelationFollowingEntity relation,
            Map<String, Object> payload,
            LocalDateTime occurredAt
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", toOffsetString(occurredAt));
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-relation");
        envelope.put("bizKey", relation.getFollowerId() + ":" + relation.getFolloweeId());
        envelope.put("payload", payload);
        return envelope;
    }

    private RelationCursorPage<RelationUserItem> toFollowingUserPage(
            String viewerId,
            List<RelationFollowingEntity> relations,
            int pageSize
    ) {
        boolean hasMore = relations.size() > pageSize;
        List<RelationFollowingEntity> pageItems = hasMore ? relations.subList(0, pageSize) : relations;
        List<String> userIds = pageItems.stream()
                .map(item -> String.valueOf(item.getFolloweeId()))
                .toList();
        PageProfiles profiles = pageProfiles(userIds);
        Map<String, RelationFollowingEntity> viewerRelations = viewerRelationMap(viewerId, userIds);
        List<RelationUserItem> items = pageItems.stream()
                .map(item -> {
                    String itemUserId = String.valueOf(item.getFolloweeId());
                    return new RelationUserItem(
                            profiles.summary(itemUserId),
                            viewerFollowStatus(viewerId, itemUserId, viewerRelations),
                            toOffsetString(item.getFollowedAt())
                    );
                })
                .toList();
        String nextCursor = relationCursor(pageItems, RelationFollowingEntity::getFollowedAt, RelationFollowingEntity::getFolloweeId, hasMore);
        return new RelationCursorPage<>(items, nextCursor, hasMore, profiles.degraded());
    }

    private RelationCursorPage<RelationUserItem> toFollowerUserPage(
            String viewerId,
            List<RelationFollowerEntity> relations,
            int pageSize
    ) {
        boolean hasMore = relations.size() > pageSize;
        List<RelationFollowerEntity> pageItems = hasMore ? relations.subList(0, pageSize) : relations;
        List<String> userIds = pageItems.stream()
                .map(item -> String.valueOf(item.getFollowerId()))
                .toList();
        PageProfiles profiles = pageProfiles(userIds);
        Map<String, RelationFollowingEntity> viewerRelations = viewerRelationMap(viewerId, userIds);
        List<RelationUserItem> items = pageItems.stream()
                .map(item -> {
                    String itemUserId = String.valueOf(item.getFollowerId());
                    return new RelationUserItem(
                            profiles.summary(itemUserId),
                            viewerFollowStatus(viewerId, itemUserId, viewerRelations),
                            toOffsetString(item.getFollowedAt())
                    );
                })
                .toList();
        String nextCursor = relationCursor(pageItems, RelationFollowerEntity::getFollowedAt, RelationFollowerEntity::getFollowerId, hasMore);
        return new RelationCursorPage<>(items, nextCursor, hasMore, profiles.degraded());
    }

    private PageProfiles pageProfiles(List<String> userIds) {
        Map<String, RelationUserSummary> summaries = memberInternalClient.batchSummary(userIds);
        boolean degraded = summaries.size() < new LinkedHashSet<>(userIds).size();
        return new PageProfiles(summaries, degraded);
    }

    private Map<String, RelationFollowingEntity> viewerRelationMap(String viewerId, List<String> targetUserIds) {
        if (viewerId == null || viewerId.isBlank() || targetUserIds.isEmpty()) {
            return Map.of();
        }
        Long followerId = parseId(viewerId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        List<Long> targetIds = targetUserIds.stream()
                .filter(targetUserId -> !targetUserId.equals(viewerId))
                .map(targetUserId -> parseId(targetUserId, ApiErrorCode.PARAM_INVALID))
                .distinct()
                .toList();
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return followingMapper.selectActiveByTargets(followerId, targetIds).stream()
                .collect(Collectors.toMap(item -> String.valueOf(item.getFolloweeId()), Function.identity()));
    }

    private String viewerFollowStatus(
            String viewerId,
            String targetUserId,
            Map<String, RelationFollowingEntity> viewerRelations
    ) {
        if (viewerId == null || viewerId.isBlank()) {
            return UNKNOWN;
        }
        if (viewerId.equals(targetUserId)) {
            return NOT_FOLLOWING;
        }
        return viewerRelations.containsKey(targetUserId) ? FOLLOWING : NOT_FOLLOWING;
    }

    private <T> String relationCursor(
            List<T> items,
            Function<T, LocalDateTime> timeExtractor,
            Function<T, Long> userIdExtractor,
            boolean hasMore
    ) {
        if (!hasMore || items.isEmpty()) {
            return null;
        }
        T last = items.get(items.size() - 1);
        return toOffsetString(timeExtractor.apply(last)) + "_" + userIdExtractor.apply(last);
    }

    private FollowActionResponse toActionResponse(RelationFollowingEntity entity, String followStatus) {
        return new FollowActionResponse(
                String.valueOf(entity.getFollowerId()),
                String.valueOf(entity.getFolloweeId()),
                followStatus,
                FOLLOWING.equals(followStatus) ? toOffsetString(entity.getFollowedAt()) : null,
                NOT_FOLLOWING.equals(followStatus) ? toOffsetString(entity.getCanceledAt()) : null
        );
    }

    private boolean active(RelationFollowingEntity relation) {
        return relation != null && STATUS_ACTIVE.equals(relation.getRelationStatus());
    }

    private PageCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new PageCursor(null, null);
        }
        int separator = cursor.lastIndexOf('_');
        if (separator <= 0 || separator == cursor.length() - 1) {
            throw new BusinessException(ApiErrorCode.RELATION_CURSOR_INVALID);
        }
        try {
            LocalDateTime sortAt = OffsetDateTime.parse(cursor.substring(0, separator))
                    .atZoneSameInstant(CHINA_ZONE)
                    .toLocalDateTime();
            Long userId = Long.valueOf(cursor.substring(separator + 1));
            return new PageCursor(sortAt, userId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.RELATION_CURSOR_INVALID);
        }
    }

    private int normalizePageSize(Integer size, int max, int defaultValue) {
        if (size == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(size, max));
    }

    private Long parseId(String value, ApiErrorCode errorCode) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException("blank");
            }
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String toOffsetString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(CHINA_ZONE).toOffsetDateTime().toString();
    }

    private record PageCursor(LocalDateTime sortAt, Long userId) {
    }

    private record PageProfiles(Map<String, RelationUserSummary> summaries, boolean degraded) {

        private RelationUserSummary summary(String userId) {
            RelationUserSummary summary = summaries.get(userId);
            if (summary != null) {
                return summary;
            }
            return new RelationUserSummary(userId, null, null, null, null, "NOT_FOUND", null);
        }
    }
}
