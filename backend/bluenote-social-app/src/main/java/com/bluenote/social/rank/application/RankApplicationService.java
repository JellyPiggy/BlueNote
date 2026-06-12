package com.bluenote.social.rank.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterItem;
import com.bluenote.social.counter.api.dto.CounterTarget;
import com.bluenote.social.counter.application.CounterApplicationService;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient.NoteSummary;
import com.bluenote.social.rank.api.dto.RankDtos.CreatorRankResponse;
import com.bluenote.social.rank.api.dto.RankDtos.CreatorStats;
import com.bluenote.social.rank.api.dto.RankDtos.RankConsumeEventRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankConsumeEventResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankCounts;
import com.bluenote.social.rank.api.dto.RankDtos.RankMemberRankItem;
import com.bluenote.social.rank.api.dto.RankDtos.RankMemberRankRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankMemberRankResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankProgress;
import com.bluenote.social.rank.api.dto.RankDtos.RankRebuildRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankRebuildTaskResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotDetailResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotItem;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotResponse;
import com.bluenote.social.rank.api.dto.RankDtos.WeeklyHotNoteItem;
import com.bluenote.social.rank.api.dto.RankDtos.WeeklyHotNotesResponse;
import com.bluenote.social.rank.api.dto.RankDtos.YearlyCreatorGrowthResponse;
import com.bluenote.social.rank.api.dto.RankDtos.YearlyCreatorItem;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankConsumeRecordEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankMemberScoreEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankNoteIndexEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankOutboxEventEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankPeriodEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankRebuildTaskEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankScoreContributionEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankSnapshotEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankSnapshotItemEntity;
import com.bluenote.social.rank.infrastructure.mapper.RankConsumeRecordMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankMemberScoreMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankNoteIndexMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankOutboxEventMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankPeriodMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankRebuildTaskMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankScoreChangeLogMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankScoreContributionMapper;
import com.bluenote.social.rank.infrastructure.mapper.RankSnapshotMapper;
import com.bluenote.social.rank.infrastructure.redis.RankRedisStore;
import com.bluenote.social.rank.infrastructure.redis.RankRedisStore.RankRedisMember;
import com.bluenote.social.relation.api.dto.RelationUserSummary;
import com.bluenote.social.relation.infrastructure.client.MemberInternalClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RankApplicationService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final WeekFields ISO_WEEK = WeekFields.ISO;
    private static final DateTimeFormatter TASK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final TypeReference<Map<String, Integer>> PROGRESS_TYPE = new TypeReference<>() {
    };

    private static final String RANK_WEEKLY_HOT_NOTE = "WEEKLY_HOT_NOTE";
    private static final String RANK_YEARLY_CREATOR_GROWTH = "YEARLY_CREATOR_GROWTH";
    private static final String MEMBER_NOTE = "NOTE";
    private static final String MEMBER_USER = "USER";
    private static final String SOURCE_NOTE = "NOTE";
    private static final String PERIOD_WEEKLY = "WEEKLY";
    private static final String PERIOD_YEARLY = "YEARLY";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_REBUILD_REDIS = "REBUILD_REDIS";
    private static final String CONSUME_PROCESSING = "PROCESSING";
    private static final String CONSUME_SUCCESS = "SUCCESS";
    private static final String ELIGIBLE = "ELIGIBLE";
    private static final String INELIGIBLE = "INELIGIBLE";
    private static final String NOTE_PUBLIC = "PUBLIC";
    private static final String NOTE_PUBLISHED = "PUBLISHED";
    private static final String RANK_EXACT = "EXACT";
    private static final String RANK_ESTIMATED = "ESTIMATED";
    private static final String RANK_NOT_RANKED = "NOT_RANKED";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int TOP_LIMIT = 100;
    private static final int REDIS_LIMIT = 120;
    private static final int REBUILD_SCAN_LIMIT = 1000;
    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final Set<String> SCORE_FIELDS = Set.of("like_count", "comment_count", "collect_count");

    private final ContentNoteClient contentNoteClient;
    private final MemberInternalClient memberInternalClient;
    private final CounterApplicationService counterApplicationService;
    private final RankPeriodMapper periodMapper;
    private final RankNoteIndexMapper noteIndexMapper;
    private final RankMemberScoreMapper memberScoreMapper;
    private final RankScoreContributionMapper contributionMapper;
    private final RankScoreChangeLogMapper changeLogMapper;
    private final RankConsumeRecordMapper consumeRecordMapper;
    private final RankOutboxEventMapper outboxEventMapper;
    private final RankRebuildTaskMapper rebuildTaskMapper;
    private final RankSnapshotMapper snapshotMapper;
    private final RankRedisStore redisStore;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;

    public RankApplicationService(
            ContentNoteClient contentNoteClient,
            MemberInternalClient memberInternalClient,
            CounterApplicationService counterApplicationService,
            RankPeriodMapper periodMapper,
            RankNoteIndexMapper noteIndexMapper,
            RankMemberScoreMapper memberScoreMapper,
            RankScoreContributionMapper contributionMapper,
            RankScoreChangeLogMapper changeLogMapper,
            RankConsumeRecordMapper consumeRecordMapper,
            RankOutboxEventMapper outboxEventMapper,
            RankRebuildTaskMapper rebuildTaskMapper,
            RankSnapshotMapper snapshotMapper,
            RankRedisStore redisStore,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper
    ) {
        this.contentNoteClient = contentNoteClient;
        this.memberInternalClient = memberInternalClient;
        this.counterApplicationService = counterApplicationService;
        this.periodMapper = periodMapper;
        this.noteIndexMapper = noteIndexMapper;
        this.memberScoreMapper = memberScoreMapper;
        this.contributionMapper = contributionMapper;
        this.changeLogMapper = changeLogMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.rebuildTaskMapper = rebuildTaskMapper;
        this.snapshotMapper = snapshotMapper;
        this.redisStore = redisStore;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
    }

    public WeeklyHotNotesResponse weeklyHotNotes(String viewerId, String cursor, Integer size) {
        int pageSize = normalizeSize(size);
        RankCursor rankCursor = parseCursor(cursor);
        RankPeriod period = currentPeriod(RANK_WEEKLY_HOT_NOTE, now());
        ensurePeriod(period);
        RankPage page = rankPage(period.rankCode(), period.periodId(), MEMBER_NOTE, rankCursor, pageSize);
        if (page.items().isEmpty()) {
            return new WeeklyHotNotesResponse(period.rankCode(), period.periodId(), List.of(), null, false, page.degraded());
        }

        List<String> noteIds = page.items().stream().map(item -> String.valueOf(item.memberId())).toList();
        List<NoteSummary> summaries = contentNoteClient.batchSummary(noteIds, viewerId, false);
        Map<String, NoteSummary> notes = summaries.stream()
                .filter(note -> NOTE_PUBLISHED.equals(note.noteStatus()) && NOTE_PUBLIC.equals(note.visibility()))
                .collect(Collectors.toMap(NoteSummary::noteId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, RelationUserSummary> users = memberInternalClient.batchSummary(summaries.stream()
                .map(NoteSummary::authorId)
                .distinct()
                .toList());
        Map<String, CounterItem> counts = counterItems(noteIds, List.of("like_count", "comment_count", "collect_count"));

        List<WeeklyHotNoteItem> items = new ArrayList<>();
        boolean degraded = page.degraded();
        for (RankEntry entry : page.items()) {
            NoteSummary note = notes.get(String.valueOf(entry.memberId()));
            if (note == null) {
                degraded = true;
                continue;
            }
            RelationUserSummary user = users.get(note.authorId());
            CounterItem counter = counts.get(note.noteId());
            degraded = degraded || (counter != null && counter.degraded());
            Map<String, Long> countValues = counter == null ? Map.of() : counter.counts();
            items.add(new WeeklyHotNoteItem(
                    entry.rankNo(),
                    note.noteId(),
                    note.authorId(),
                    user == null ? null : user.nickname(),
                    user == null ? null : user.avatarUrl(),
                    note.title(),
                    note.coverUrl(),
                    entry.score(),
                    new RankCounts(
                            countValues.getOrDefault("like_count", 0L),
                            countValues.getOrDefault("comment_count", 0L),
                            countValues.getOrDefault("collect_count", 0L)
                    ),
                    note.publishedAt()
            ));
        }
        boolean hasMore = page.hasMore() && !items.isEmpty();
        return new WeeklyHotNotesResponse(
                period.rankCode(),
                period.periodId(),
                items,
                hasMore ? nextCursor(items.get(items.size() - 1).rankNo(), items.get(items.size() - 1).noteId()) : null,
                hasMore,
                degraded
        );
    }

    public YearlyCreatorGrowthResponse yearlyCreatorGrowth(String cursor, Integer size) {
        int pageSize = normalizeSize(size);
        RankCursor rankCursor = parseCursor(cursor);
        RankPeriod period = currentPeriod(RANK_YEARLY_CREATOR_GROWTH, now());
        ensurePeriod(period);
        RankPage page = rankPage(period.rankCode(), period.periodId(), MEMBER_USER, rankCursor, pageSize);
        if (page.items().isEmpty()) {
            return new YearlyCreatorGrowthResponse(period.rankCode(), period.periodId(), List.of(), null, false, page.degraded());
        }
        List<String> userIds = page.items().stream().map(item -> String.valueOf(item.memberId())).toList();
        Map<String, RelationUserSummary> users = memberInternalClient.batchSummary(userIds);
        Map<String, CounterItem> counts = counterItems(userIds, List.of("note_count", "follower_count", "liked_count"), "USER");

        List<YearlyCreatorItem> items = new ArrayList<>();
        boolean degraded = page.degraded();
        for (RankEntry entry : page.items()) {
            RelationUserSummary user = users.get(String.valueOf(entry.memberId()));
            CounterItem counter = counts.get(String.valueOf(entry.memberId()));
            degraded = degraded || (counter != null && counter.degraded());
            Map<String, Long> countValues = counter == null ? Map.of() : counter.counts();
            items.add(new YearlyCreatorItem(
                    entry.rankNo(),
                    String.valueOf(entry.memberId()),
                    user == null ? null : user.nickname(),
                    user == null ? null : user.avatarUrl(),
                    user == null ? null : user.bio(),
                    entry.score(),
                    RANK_EXACT,
                    new CreatorStats(
                            countValues.getOrDefault("note_count", 0L),
                            countValues.getOrDefault("follower_count", 0L),
                            countValues.getOrDefault("liked_count", 0L)
                    )
            ));
        }
        boolean hasMore = page.hasMore() && !items.isEmpty();
        return new YearlyCreatorGrowthResponse(
                period.rankCode(),
                period.periodId(),
                items,
                hasMore ? nextCursor(items.get(items.size() - 1).rankNo(), items.get(items.size() - 1).creatorId()) : null,
                hasMore,
                degraded
        );
    }

    public CreatorRankResponse myYearlyGrowthRank(String userId) {
        Long creatorId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        RankPeriod period = currentPeriod(RANK_YEARLY_CREATOR_GROWTH, now());
        ensurePeriod(period);
        RankMemberRankItem rank = memberRank(period.rankCode(), period.periodId(), MEMBER_USER, creatorId);
        return new CreatorRankResponse(
                period.rankCode(),
                period.periodId(),
                String.valueOf(creatorId),
                rank.rankNo(),
                rank.rankMode(),
                rank.score(),
                rank.notRanked(),
                false
        );
    }

    public RankMemberRankResponse batchRank(RankMemberRankRequest request) {
        if (request == null) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        String rankCode = normalizeRankCode(request.rankCode());
        String memberType = normalizeMemberType(request.memberType());
        String periodId = normalizePeriodId(request.periodId(), rankCode);
        List<Long> memberIds = request.memberIds() == null ? List.of() : request.memberIds().stream()
                .limit(101)
                .map(value -> parseId(value, ApiErrorCode.RANK_MEMBER_NOT_FOUND))
                .toList();
        if (memberIds.size() > 100) {
            throw new BusinessException(ApiErrorCode.RANK_QUERY_SIZE_EXCEEDED);
        }
        ensurePeriod(period(rankCode, periodId, now()));
        return new RankMemberRankResponse(
                rankCode,
                periodId,
                memberIds.stream().map(memberId -> memberRank(rankCode, periodId, memberType, memberId)).toList(),
                false
        );
    }

    @Transactional
    public RankConsumeEventResponse consumeEvent(RankConsumeEventRequest request) {
        LocalDateTime now = now();
        validateConsumeRequest(request);
        RankConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(request.consumerGroup(), request.eventId());
        if (existing != null && CONSUME_SUCCESS.equals(existing.getConsumeStatus())) {
            return new RankConsumeEventResponse(request.eventId(), request.eventType(), CONSUME_SUCCESS, 0);
        }
        reserveConsumeRecord(request, now);
        int updatedRanks = 0;
        try {
            updatedRanks = switch (request.eventType()) {
                case "NotePublished", "NoteUpdated" -> consumeNoteUpsert(request, now);
                case "NoteDeleted" -> consumeNoteDeleted(request, now);
                case "NoteVisibilityChanged" -> consumeNoteVisibilityChanged(request, now);
                case "NoteStatusChanged" -> consumeNoteStatusChanged(request, now);
                case "CounterChanged" -> consumeCounterChanged(request, now);
                case "CounterRebuilt" -> 0;
                default -> 0;
            };
            consumeRecordMapper.markSuccess(request.consumerGroup(), request.eventId());
            return new RankConsumeEventResponse(request.eventId(), request.eventType(), CONSUME_SUCCESS, updatedRanks);
        } catch (RuntimeException exception) {
            consumeRecordMapper.markFail(request.consumerGroup(), request.eventId(), truncate(exception.getMessage(), 512));
            throw exception;
        }
    }

    @Transactional
    public RankRebuildTaskResponse rebuild(RankRebuildRequest request) {
        String rankCode = normalizeRankCode(request == null ? RANK_WEEKLY_HOT_NOTE : request.rankCode());
        String periodId = normalizePeriodId(request == null ? null : request.periodId(), rankCode);
        String taskType = request == null || isBlank(request.taskType())
                ? TASK_REBUILD_REDIS
                : request.taskType().trim().toUpperCase(Locale.ROOT);
        if (!TASK_REBUILD_REDIS.equals(taskType)) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        ensurePeriod(period(rankCode, periodId, now()));
        LocalDateTime now = now();
        String taskId = "rank_rebuild_" + rankCode + "_" + periodId + "_" + TASK_TIME_FORMAT.format(now);
        RankRebuildTaskEntity task = new RankRebuildTaskEntity();
        task.setTaskId(taskId);
        task.setTaskType(taskType);
        task.setRankCode(rankCode);
        task.setPeriodId(periodId);
        task.setTaskStatus(TASK_PENDING);
        task.setProgressJson(progressJson(0, 0, 0));
        task.setReason(request == null || isBlank(request.reason()) ? "MANUAL" : truncate(request.reason(), 128));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        rebuildTaskMapper.insert(task);

        runRebuildTask(taskId);
        return rebuildTask(taskId);
    }

    public RankRebuildTaskResponse rebuildTask(String taskId) {
        RankRebuildTaskEntity task = rebuildTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.RANK_REBUILD_TASK_NOT_FOUND);
        }
        return toRebuildTaskResponse(task);
    }

    @Transactional
    public RankSnapshotResponse createSnapshot(RankSnapshotRequest request) {
        String rankCode = normalizeRankCode(request == null ? RANK_WEEKLY_HOT_NOTE : request.rankCode());
        String periodId = normalizePeriodId(request == null ? null : request.periodId(), rankCode);
        String snapshotType = request == null || isBlank(request.snapshotType())
                ? "MANUAL"
                : request.snapshotType().trim().toUpperCase(Locale.ROOT);
        ensurePeriod(period(rankCode, periodId, now()));
        List<RankEntry> entries = mysqlEntries(rankCode, periodId, TOP_LIMIT);
        LocalDateTime now = now();
        Long snapshotId = idGenerator.nextId();
        RankSnapshotEntity snapshot = new RankSnapshotEntity();
        snapshot.setSnapshotId(snapshotId);
        snapshot.setRankCode(rankCode);
        snapshot.setPeriodId(periodId);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setItemCount(entries.size());
        snapshot.setSourceVersion("rank-foundation");
        snapshot.setCreatedAt(now);
        snapshotMapper.insertSnapshot(snapshot);
        for (RankEntry entry : entries) {
            RankSnapshotItemEntity item = new RankSnapshotItemEntity();
            item.setId(idGenerator.nextId());
            item.setSnapshotId(snapshotId);
            item.setRankNo(entry.rankNo());
            item.setMemberType(memberTypeForRank(rankCode));
            item.setMemberId(entry.memberId());
            item.setScore(entry.score());
            item.setRankScore(entry.rankScore());
            item.setCreatedAt(now);
            snapshotMapper.insertSnapshotItem(item);
        }
        insertRankChangedOutbox(rankCode, periodId, "SNAPSHOT_CREATED", entries.size(), false, now);
        return new RankSnapshotResponse(String.valueOf(snapshotId), rankCode, periodId, snapshotType, entries.size(), toOffsetString(now));
    }

    public RankSnapshotDetailResponse latestSnapshot(String rankCode, String periodId, String snapshotType) {
        String resolvedRankCode = normalizeRankCode(rankCode);
        String resolvedPeriodId = normalizePeriodId(periodId, resolvedRankCode);
        String resolvedType = isBlank(snapshotType) ? "MANUAL" : snapshotType.trim().toUpperCase(Locale.ROOT);
        RankSnapshotEntity snapshot = snapshotMapper.selectLatest(resolvedRankCode, resolvedPeriodId, resolvedType);
        if (snapshot == null) {
            throw new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        List<RankSnapshotItem> items = snapshotMapper.selectItems(snapshot.getSnapshotId()).stream()
                .map(item -> new RankSnapshotItem(
                        item.getRankNo(),
                        item.getMemberType(),
                        String.valueOf(item.getMemberId()),
                        item.getScore()
                ))
                .toList();
        return new RankSnapshotDetailResponse(
                String.valueOf(snapshot.getSnapshotId()),
                snapshot.getRankCode(),
                snapshot.getPeriodId(),
                snapshot.getSnapshotType(),
                items,
                toOffsetString(snapshot.getCreatedAt())
        );
    }

    private int consumeNoteUpsert(RankConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = payload(request);
        Long noteId = id(payload, "noteId");
        Long authorId = id(payload, "authorId");
        RankNoteIndexEntity existing = noteIndexMapper.selectByNoteId(noteId);
        LocalDateTime publishedAt = payload.get("publishedAt") == null && existing != null
                ? existing.getPublishedAt()
                : parseOffsetDateTime(stringValue(payload.getOrDefault("publishedAt", request.occurredAt())));
        String visibility = valueOrDefault(payload, "visibility", NOTE_PUBLIC);
        String noteStatus = valueOrDefault(payload, "noteStatus", NOTE_PUBLISHED);
        RankNoteIndexEntity entity = new RankNoteIndexEntity();
        entity.setNoteId(noteId);
        entity.setAuthorId(authorId);
        entity.setTitleSnapshot(truncate(valueOrEmpty(payload.getOrDefault("title", existing == null ? null : existing.getTitleSnapshot())), 128));
        entity.setCoverUrlSnapshot(truncate(stringValue(payload.getOrDefault("coverUrl", existing == null ? null : existing.getCoverUrlSnapshot())), 512));
        entity.setVisibility(visibility);
        entity.setNoteStatus(noteStatus);
        entity.setEligibleStatus(eligibleStatus(visibility, noteStatus));
        entity.setPublishedAt(publishedAt);
        entity.setLastEventId(request.eventId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        noteIndexMapper.upsert(entity);
        if (INELIGIBLE.equals(entity.getEligibleStatus())) {
            suspendContributions(noteId, request.eventId(), now);
        }
        return 0;
    }

    private int consumeNoteDeleted(RankConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = payload(request);
        Long noteId = id(payload, "noteId");
        RankNoteIndexEntity existing = ensureNoteIndex(noteId, null, request);
        noteIndexMapper.markStatus(noteId, valueOrDefault(payload, "visibility", existing.getVisibility()),
                "DELETED", INELIGIBLE, request.eventId(), now);
        return suspendContributions(noteId, request.eventId(), now);
    }

    private int consumeNoteVisibilityChanged(RankConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = payload(request);
        Long noteId = id(payload, "noteId");
        RankNoteIndexEntity existing = ensureNoteIndex(noteId, null, request);
        String visibility = valueOrDefault(payload, "toVisibility", existing.getVisibility());
        String noteStatus = valueOrDefault(payload, "noteStatus", existing.getNoteStatus());
        String eligibility = eligibleStatus(visibility, noteStatus);
        noteIndexMapper.markStatus(noteId, visibility, noteStatus, eligibility, request.eventId(), now);
        return INELIGIBLE.equals(eligibility) ? suspendContributions(noteId, request.eventId(), now) : 0;
    }

    private int consumeNoteStatusChanged(RankConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = payload(request);
        Long noteId = id(payload, "noteId");
        RankNoteIndexEntity existing = ensureNoteIndex(noteId, null, request);
        String visibility = valueOrDefault(payload, "visibility", existing.getVisibility());
        String noteStatus = valueOrDefault(payload, "toStatus", existing.getNoteStatus());
        String eligibility = eligibleStatus(visibility, noteStatus);
        noteIndexMapper.markStatus(noteId, visibility, noteStatus, eligibility, request.eventId(), now);
        return INELIGIBLE.equals(eligibility) ? suspendContributions(noteId, request.eventId(), now) : 0;
    }

    private int consumeCounterChanged(RankConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = payload(request);
        if (!MEMBER_NOTE.equals(String.valueOf(payload.get("targetType")))) {
            return 0;
        }
        Long noteId = id(payload, "targetId");
        RankNoteIndexEntity note = ensureNoteIndex(noteId, null, request);
        if (!ELIGIBLE.equals(note.getEligibleStatus())) {
            return 0;
        }
        LocalDateTime occurredAt = parseOffsetDateTime(request.occurredAt());
        long scoreDelta = scoreDelta(payload.get("changedFields"));
        if (scoreDelta == 0) {
            return 0;
        }

        int updated = 0;
        RankPeriod week = currentPeriod(RANK_WEEKLY_HOT_NOTE, occurredAt);
        ensurePeriod(week);
        updated += applyScoreDelta(
                week.rankCode(),
                week.periodId(),
                MEMBER_NOTE,
                noteId,
                noteId,
                scoreDelta,
                request,
                occurredAt,
                now
        );

        if (note.getPublishedAt() != null && note.getPublishedAt().getYear() == occurredAt.getYear()) {
            RankPeriod year = currentPeriod(RANK_YEARLY_CREATOR_GROWTH, occurredAt);
            ensurePeriod(year);
            updated += applyScoreDelta(
                    year.rankCode(),
                    year.periodId(),
                    MEMBER_USER,
                    note.getAuthorId(),
                    noteId,
                    scoreDelta,
                    request,
                    occurredAt,
                    now
            );
        }
        return updated;
    }

    private int applyScoreDelta(
            String rankCode,
            String periodId,
            String memberType,
            Long memberId,
            Long sourceNoteId,
            long scoreDelta,
            RankConsumeEventRequest request,
            LocalDateTime occurredAt,
            LocalDateTime now
    ) {
        ensureMemberScore(rankCode, periodId, memberType, memberId, now);
        ensureContribution(rankCode, periodId, memberType, memberId, sourceNoteId, now);
        RankMemberScoreEntity score = memberScoreMapper.selectForUpdate(rankCode, periodId, memberType, memberId);
        RankScoreContributionEntity contribution = contributionMapper.selectForUpdate(
                rankCode, periodId, memberType, memberId, SOURCE_NOTE, sourceNoteId);
        long nextScore = Math.max(0L, nullToZero(score.getScore()) + scoreDelta);
        double rankScore = rankScore(nextScore, occurredAt, memberId);
        int inserted = changeLogMapper.insertIgnore(
                idGenerator.nextId(),
                request.eventId(),
                request.eventType(),
                rankCode,
                periodId,
                memberType,
                memberId,
                SOURCE_NOTE,
                sourceNoteId,
                "weighted_score",
                scoreDelta,
                scoreDelta,
                nextScore,
                occurredAt,
                now
        );
        if (inserted == 0) {
            return 0;
        }
        memberScoreMapper.updateScore(rankCode, periodId, memberType, memberId, nextScore, rankScore,
                nextScore > 0 ? STATUS_ACTIVE : STATUS_SUSPENDED, now, now);

        long currentContributionScore = STATUS_ACTIVE.equals(contribution.getContributionStatus())
                ? nullToZero(contribution.getScore())
                : 0L;
        contribution.setScore(Math.max(0L, currentContributionScore + scoreDelta));
        contribution.setContributionStatus(contribution.getScore() > 0 ? STATUS_ACTIVE : STATUS_SUSPENDED);
        contribution.setLastEventId(request.eventId());
        contribution.setUpdatedAt(now);
        contributionMapper.updateScore(contribution);

        runAfterCommit(() -> redisStore.updateMember(rankCode, periodId, memberId, nextScore, rankScore, REDIS_LIMIT));
        insertRankChangedOutbox(rankCode, periodId, "SCORE_UPDATED", 1, true, now);
        return 1;
    }

    private int suspendContributions(Long noteId, String eventId, LocalDateTime now) {
        List<RankScoreContributionEntity> contributions = contributionMapper.selectBySource(SOURCE_NOTE, noteId);
        int updated = 0;
        for (RankScoreContributionEntity contribution : contributions) {
            if (!STATUS_ACTIVE.equals(contribution.getContributionStatus()) || nullToZero(contribution.getScore()) <= 0) {
                continue;
            }
            ensureMemberScore(
                    contribution.getRankCode(),
                    contribution.getPeriodId(),
                    contribution.getMemberType(),
                    contribution.getMemberId(),
                    now
            );
            RankMemberScoreEntity score = memberScoreMapper.selectForUpdate(
                    contribution.getRankCode(),
                    contribution.getPeriodId(),
                    contribution.getMemberType(),
                    contribution.getMemberId()
            );
            long nextScore = Math.max(0L, nullToZero(score.getScore()) - nullToZero(contribution.getScore()));
            double nextRankScore = rankScore(nextScore, now, contribution.getMemberId());
            memberScoreMapper.updateScore(
                    contribution.getRankCode(),
                    contribution.getPeriodId(),
                    contribution.getMemberType(),
                    contribution.getMemberId(),
                    nextScore,
                    nextRankScore,
                    nextScore > 0 ? STATUS_ACTIVE : STATUS_SUSPENDED,
                    now,
                    now
            );
            contribution.setContributionStatus(STATUS_SUSPENDED);
            contribution.setLastEventId(eventId);
            contribution.setUpdatedAt(now);
            contributionMapper.updateScore(contribution);
            runAfterCommit(() -> redisStore.updateMember(
                    contribution.getRankCode(),
                    contribution.getPeriodId(),
                    contribution.getMemberId(),
                    nextScore,
                    nextRankScore,
                    REDIS_LIMIT
            ));
            insertRankChangedOutbox(contribution.getRankCode(), contribution.getPeriodId(), "MEMBER_SUSPENDED", 1, true, now);
            updated++;
        }
        return updated;
    }

    private RankPage rankPage(String rankCode, String periodId, String memberType, RankCursor cursor, int pageSize) {
        boolean degraded = false;
        List<RankEntry> entries;
        try {
            entries = redisStore.top(rankCode, periodId, TOP_LIMIT + 1).stream()
                    .map(item -> toRankEntry(rankCode, periodId, memberType, item))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException exception) {
            degraded = true;
            entries = mysqlEntries(rankCode, periodId, TOP_LIMIT + 1);
        }
        if (entries.isEmpty()) {
            entries = mysqlEntries(rankCode, periodId, TOP_LIMIT + 1);
            degraded = true;
        }
        List<RankEntry> afterCursor = entries.stream()
                .filter(item -> item.rankNo() > cursor.rankNo())
                .limit(pageSize + 1L)
                .toList();
        boolean hasMore = afterCursor.size() > pageSize;
        List<RankEntry> pageItems = hasMore ? afterCursor.subList(0, pageSize) : afterCursor;
        return new RankPage(pageItems, hasMore, degraded);
    }

    private RankEntry toRankEntry(String rankCode, String periodId, String memberType, RankRedisMember redisMember) {
        RankMemberScoreEntity score = memberScoreMapper.selectOne(rankCode, periodId, memberType, redisMember.memberId());
        if (score == null || nullToZero(score.getScore()) <= 0 || !STATUS_ACTIVE.equals(score.getMemberStatus())) {
            return null;
        }
        return new RankEntry(redisMember.rankNo(), redisMember.memberId(), score.getScore(), redisMember.rankScore());
    }

    private List<RankEntry> mysqlEntries(String rankCode, String periodId, int limit) {
        return memberScoreMapper.selectTop(rankCode, periodId, limit).stream()
                .map(entity -> new RankEntry(0, entity.getMemberId(), entity.getScore(), entity.getRankScore()))
                .sorted(rankEntryComparator())
                .limit(limit)
                .map(new RankingNumberer()::withRank)
                .toList();
    }

    private RankMemberRankItem memberRank(String rankCode, String periodId, String memberType, Long memberId) {
        RankMemberScoreEntity score = memberScoreMapper.selectOne(rankCode, periodId, memberType, memberId);
        if (score == null || nullToZero(score.getScore()) <= 0) {
            return new RankMemberRankItem(memberType, String.valueOf(memberId), null, RANK_NOT_RANKED, 0L, true);
        }
        try {
            Long redisRank = redisStore.reverseRank(rankCode, periodId, memberId);
            if (redisRank != null && redisRank < TOP_LIMIT) {
                return new RankMemberRankItem(memberType, String.valueOf(memberId), redisRank.intValue() + 1,
                        RANK_EXACT, score.getScore(), false);
            }
        } catch (RuntimeException ignored) {
            // Fall through to MySQL estimation.
        }
        int estimated = Math.max(TOP_LIMIT + 1, memberScoreMapper.countHigherScore(rankCode, periodId, score.getScore()) + 1);
        return new RankMemberRankItem(memberType, String.valueOf(memberId), estimated, RANK_ESTIMATED, score.getScore(), false);
    }

    private void runRebuildTask(String taskId) {
        RankRebuildTaskEntity task = rebuildTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.RANK_REBUILD_TASK_NOT_FOUND);
        }
        rebuildTaskMapper.markRunning(taskId);
        try {
            List<RankMemberScoreEntity> scores = memberScoreMapper.selectActiveByRank(
                    task.getRankCode(),
                    task.getPeriodId(),
                    REBUILD_SCAN_LIMIT
            );
            List<RankRedisMember> members = scores.stream()
                    .sorted(Comparator.comparing(RankMemberScoreEntity::getRankScore).reversed()
                            .thenComparing(RankMemberScoreEntity::getMemberId))
                    .limit(REDIS_LIMIT)
                    .map(entity -> new RankRedisMember(entity.getMemberId(), entity.getRankScore(), null))
                    .toList();
            redisStore.replaceExact(task.getRankCode(), task.getPeriodId(), members, REDIS_LIMIT);
            rebuildTaskMapper.markSuccess(taskId, progressJson(scores.size(), scores.size(), 0));
            insertRankChangedOutbox(task.getRankCode(), task.getPeriodId(), "REBUILD_REDIS", members.size(), true, now());
        } catch (RuntimeException exception) {
            rebuildTaskMapper.markFailed(taskId, progressJson(0, 0, 1), truncate(exception.getMessage(), 512));
        }
    }

    private void ensurePeriod(RankPeriod period) {
        RankPeriodEntity entity = new RankPeriodEntity();
        entity.setId(idGenerator.nextId());
        entity.setRankCode(period.rankCode());
        entity.setPeriodId(period.periodId());
        entity.setPeriodType(period.periodType());
        entity.setPeriodStatus(STATUS_OPEN);
        entity.setStartAt(period.startAt());
        entity.setEndAt(period.endAt());
        entity.setCreatedAt(now());
        entity.setUpdatedAt(now());
        periodMapper.insertIgnore(entity);
    }

    private void ensureMemberScore(String rankCode, String periodId, String memberType, Long memberId, LocalDateTime now) {
        RankMemberScoreEntity entity = new RankMemberScoreEntity();
        entity.setId(idGenerator.nextId());
        entity.setRankCode(rankCode);
        entity.setPeriodId(periodId);
        entity.setMemberType(memberType);
        entity.setMemberId(memberId);
        entity.setScore(0L);
        entity.setRankScore(0D);
        entity.setScoreVersion(1);
        entity.setMemberStatus(STATUS_SUSPENDED);
        entity.setLastScoreAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        memberScoreMapper.insertIgnore(entity);
    }

    private void ensureContribution(String rankCode, String periodId, String memberType, Long memberId, Long sourceNoteId, LocalDateTime now) {
        RankScoreContributionEntity entity = new RankScoreContributionEntity();
        entity.setId(idGenerator.nextId());
        entity.setRankCode(rankCode);
        entity.setPeriodId(periodId);
        entity.setMemberType(memberType);
        entity.setMemberId(memberId);
        entity.setSourceType(SOURCE_NOTE);
        entity.setSourceId(sourceNoteId);
        entity.setScore(0L);
        entity.setContributionStatus(STATUS_SUSPENDED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        contributionMapper.insertIgnore(entity);
    }

    private RankNoteIndexEntity ensureNoteIndex(Long noteId, String viewerId, RankConsumeEventRequest request) {
        RankNoteIndexEntity existing = noteIndexMapper.selectByNoteId(noteId);
        if (existing != null) {
            return existing;
        }
        List<NoteSummary> summaries = contentNoteClient.batchSummary(List.of(String.valueOf(noteId)), viewerId, true);
        if (summaries.isEmpty()) {
            throw new BusinessException(ApiErrorCode.NOTE_NOT_FOUND);
        }
        NoteSummary summary = summaries.get(0);
        LocalDateTime now = now();
        RankNoteIndexEntity entity = new RankNoteIndexEntity();
        entity.setNoteId(parseId(summary.noteId(), ApiErrorCode.NOTE_NOT_FOUND));
        entity.setAuthorId(parseId(summary.authorId(), ApiErrorCode.USER_NOT_FOUND));
        entity.setTitleSnapshot(truncate(valueOrEmpty(summary.title()), 128));
        entity.setCoverUrlSnapshot(truncate(summary.coverUrl(), 512));
        entity.setVisibility(summary.visibility());
        entity.setNoteStatus(summary.noteStatus());
        entity.setEligibleStatus(eligibleStatus(summary.visibility(), summary.noteStatus()));
        entity.setPublishedAt(parseOffsetDateTime(summary.publishedAt()));
        entity.setLastEventId(request == null ? null : request.eventId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        noteIndexMapper.upsert(entity);
        return entity;
    }

    private Map<String, CounterItem> counterItems(List<String> ids, List<String> fields) {
        return counterItems(ids, fields, "NOTE");
    }

    private Map<String, CounterItem> counterItems(List<String> ids, List<String> fields, String targetType) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return counterApplicationService.batch(new CounterBatchRequest(ids.stream()
                        .distinct()
                        .map(id -> new CounterTarget(targetType, id, fields))
                        .toList()))
                .items()
                .stream()
                .collect(Collectors.toMap(CounterItem::targetId, Function.identity(), (left, right) -> left));
    }

    private long scoreDelta(Object changedFieldsObject) {
        if (!(changedFieldsObject instanceof Map<?, ?> changedFields)) {
            return 0L;
        }
        long score = 0L;
        for (Map.Entry<?, ?> entry : changedFields.entrySet()) {
            String field = String.valueOf(entry.getKey());
            if (!SCORE_FIELDS.contains(field) || !(entry.getValue() instanceof Map<?, ?> fieldChange)) {
                continue;
            }
            long delta = longValue(fieldChange.get("delta"));
            score += delta * weight(field);
        }
        return score;
    }

    private int weight(String field) {
        return switch (field) {
            case "comment_count" -> 2;
            case "collect_count" -> 3;
            default -> 1;
        };
    }

    private Comparator<RankEntry> rankEntryComparator() {
        return Comparator.comparing(RankEntry::rankScore).reversed().thenComparing(RankEntry::memberId);
    }

    private void insertRankChangedOutbox(
            String rankCode,
            String periodId,
            String changeType,
            int changedMemberCount,
            boolean topChanged,
            LocalDateTime now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rankCode", rankCode);
        payload.put("periodId", periodId);
        payload.put("changeType", changeType);
        payload.put("changedMemberCount", changedMemberCount);
        payload.put("topChanged", topChanged);
        insertOutbox("RankChanged", "evt_rank_changed_" + rankCode + "_" + periodId + "_" + UUID.randomUUID(),
                rankCode + ":" + periodId, payload, now);
    }

    private void insertOutbox(String eventType, String eventId, String aggregateId, Map<String, Object> payload, LocalDateTime now) {
        RankOutboxEventEntity entity = new RankOutboxEventEntity();
        entity.setEventId(normalizeEventId(eventId));
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(entity.getEventId(), eventType, aggregateId, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            outboxEventMapper.insert(entity);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private Map<String, Object> eventEnvelope(String eventId, String eventType, String aggregateId, Map<String, Object> payload, LocalDateTime occurredAt) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", toOffsetString(occurredAt));
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-rank");
        envelope.put("bizKey", aggregateId);
        envelope.put("payload", payload);
        return envelope;
    }

    private void reserveConsumeRecord(RankConsumeEventRequest request, LocalDateTime now) {
        RankConsumeRecordEntity entity = new RankConsumeRecordEntity();
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

    private void validateConsumeRequest(RankConsumeEventRequest request) {
        if (request == null || isBlank(request.consumerGroup()) || isBlank(request.eventId()) || isBlank(request.eventType())) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private RankRebuildTaskResponse toRebuildTaskResponse(RankRebuildTaskEntity task) {
        return new RankRebuildTaskResponse(
                task.getTaskId(),
                task.getRankCode(),
                task.getPeriodId(),
                task.getTaskType(),
                task.getTaskStatus(),
                parseProgress(task.getProgressJson())
        );
    }

    private RankProgress parseProgress(String json) {
        try {
            Map<String, Integer> map = objectMapper.readValue(json, PROGRESS_TYPE);
            return new RankProgress(map.getOrDefault("total", 0), map.getOrDefault("success", 0), map.getOrDefault("failed", 0));
        } catch (JsonProcessingException exception) {
            return new RankProgress(0, 0, 0);
        }
    }

    private String progressJson(int total, int success, int failed) {
        return jsonPayloads.stringify(Map.of("total", total, "success", success, "failed", failed));
    }

    private RankPeriod currentPeriod(String rankCode, LocalDateTime dateTime) {
        return period(rankCode, null, dateTime);
    }

    private RankPeriod period(String rankCode, String preferredPeriodId, LocalDateTime dateTime) {
        String resolved = normalizeRankCode(rankCode);
        if (RANK_WEEKLY_HOT_NOTE.equals(resolved)) {
            LocalDate date = dateTime.toLocalDate();
            LocalDate weekStart = date.with(DayOfWeek.MONDAY);
            int weekYear = date.get(ISO_WEEK.weekBasedYear());
            int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
            String periodId = preferredPeriodId == null ? String.format("%dW%02d", weekYear, week) : preferredPeriodId;
            return new RankPeriod(resolved, periodId, PERIOD_WEEKLY, weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay());
        }
        LocalDate yearStart = LocalDate.of(dateTime.getYear(), 1, 1);
        String periodId = preferredPeriodId == null ? String.valueOf(dateTime.getYear()) : preferredPeriodId;
        return new RankPeriod(resolved, periodId, PERIOD_YEARLY, yearStart.atStartOfDay(), yearStart.plusYears(1).atStartOfDay());
    }

    private String normalizeRankCode(String rankCode) {
        String value = isBlank(rankCode) ? "" : rankCode.trim().toUpperCase(Locale.ROOT);
        if (!RANK_WEEKLY_HOT_NOTE.equals(value) && !RANK_YEARLY_CREATOR_GROWTH.equals(value)) {
            throw new BusinessException(ApiErrorCode.RANK_CODE_UNSUPPORTED);
        }
        return value;
    }

    private String normalizeMemberType(String memberType) {
        String value = isBlank(memberType) ? "" : memberType.trim().toUpperCase(Locale.ROOT);
        if (!MEMBER_NOTE.equals(value) && !MEMBER_USER.equals(value)) {
            throw new BusinessException(ApiErrorCode.RANK_MEMBER_TYPE_INVALID);
        }
        return value;
    }

    private String normalizePeriodId(String periodId, String rankCode) {
        if (!isBlank(periodId)) {
            return periodId.trim();
        }
        return currentPeriod(rankCode, now()).periodId();
    }

    private String memberTypeForRank(String rankCode) {
        return RANK_WEEKLY_HOT_NOTE.equals(rankCode) ? MEMBER_NOTE : MEMBER_USER;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1) {
            throw new BusinessException(ApiErrorCode.RANK_QUERY_SIZE_EXCEEDED);
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private RankCursor parseCursor(String cursor) {
        if (isBlank(cursor)) {
            return new RankCursor(0, 0L);
        }
        String[] parts = cursor.split("_", 2);
        if (parts.length != 2) {
            throw new BusinessException(ApiErrorCode.RANK_CURSOR_INVALID);
        }
        try {
            return new RankCursor(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.RANK_CURSOR_INVALID);
        }
    }

    private String nextCursor(Integer rankNo, String memberId) {
        return rankNo + "_" + memberId;
    }

    private String eligibleStatus(String visibility, String noteStatus) {
        return NOTE_PUBLIC.equals(visibility) && NOTE_PUBLISHED.equals(noteStatus) ? ELIGIBLE : INELIGIBLE;
    }

    private Map<String, Object> payload(RankConsumeEventRequest request) {
        return request.payload() == null ? Map.of() : request.payload();
    }

    private Long id(Map<String, Object> payload, String field) {
        return parseId(String.valueOf(payload.get(field)), ApiErrorCode.PARAM_INVALID);
    }

    private Long parseId(String value, ApiErrorCode errorCode) {
        try {
            if (value == null || value.isBlank() || "null".equals(value)) {
                throw new NumberFormatException("blank");
            }
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private String valueOrDefault(Map<String, Object> payload, String field, String defaultValue) {
        String value = stringValue(payload.get(field));
        return isBlank(value) ? defaultValue : value;
    }

    private String valueOrEmpty(Object value) {
        String text = stringValue(value);
        return text == null ? "" : text;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseOffsetDateTime(String value) {
        if (isBlank(value)) {
            return now();
        }
        return OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
    }

    private double rankScore(long score, LocalDateTime occurredAt, Long memberId) {
        if (score <= 0) {
            return 0D;
        }
        long millis = occurredAt.atZone(CHINA_ZONE).toInstant().toEpochMilli();
        double tie = 1D - ((millis % 1_000_000D) / 1_000_000_000_000D) - ((memberId % 1000D) / 1_000_000_000_000_000D);
        return score + tie;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String toOffsetString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(CHINA_ZONE).toOffsetDateTime().toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeEventId(String eventId) {
        if (eventId.length() <= MAX_EVENT_ID_LENGTH) {
            return eventId;
        }
        return eventId.substring(0, Math.min(96, eventId.length())) + "_" + UUID.nameUUIDFromBytes(eventId.getBytes());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    private record RankPeriod(String rankCode, String periodId, String periodType, LocalDateTime startAt, LocalDateTime endAt) {
    }

    private record RankCursor(Integer rankNo, Long memberId) {
    }

    private record RankEntry(Integer rankNo, Long memberId, Long score, Double rankScore) {
    }

    private record RankPage(List<RankEntry> items, boolean hasMore, boolean degraded) {
    }

    private static final class RankingNumberer {
        private int rankNo;

        private RankEntry withRank(RankEntry entry) {
            rankNo++;
            return new RankEntry(rankNo, entry.memberId(), entry.score(), entry.rankScore());
        }
    }
}
