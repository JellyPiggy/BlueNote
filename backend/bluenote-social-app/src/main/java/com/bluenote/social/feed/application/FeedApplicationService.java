package com.bluenote.social.feed.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.mq.RocketMqProducerClient;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterItem;
import com.bluenote.social.counter.api.dto.CounterTarget;
import com.bluenote.social.counter.application.CounterApplicationService;
import com.bluenote.social.feed.api.dto.FeedCardResponse;
import com.bluenote.social.feed.api.dto.FeedConsumeEventRequest;
import com.bluenote.social.feed.api.dto.FeedCountsResponse;
import com.bluenote.social.feed.api.dto.FeedFanoutSubTaskResponse;
import com.bluenote.social.feed.api.dto.FeedFanoutTaskResponse;
import com.bluenote.social.feed.api.dto.FeedRebuildRequest;
import com.bluenote.social.feed.api.dto.FeedRebuildResponse;
import com.bluenote.social.feed.api.dto.FeedRebuildTaskResponse;
import com.bluenote.social.feed.api.dto.FeedTaskProgress;
import com.bluenote.social.feed.api.dto.FollowingFeedResponse;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient.AuthorRecentNotes;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient.NoteSummary;
import com.bluenote.social.feed.infrastructure.entity.FeedCleanupTaskEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedConsumeRecordEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedFanoutSubTaskEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedFanoutTaskEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedInboxItemEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedNoteIndexEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedOutboxEventEntity;
import com.bluenote.social.feed.infrastructure.entity.FeedRebuildTaskEntity;
import com.bluenote.social.feed.infrastructure.mapper.FeedCleanupTaskMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedConsumeRecordMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedFanoutSubTaskMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedFanoutTaskMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedInboxItemMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedNoteIndexMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedOutboxEventMapper;
import com.bluenote.social.feed.infrastructure.mapper.FeedRebuildTaskMapper;
import com.bluenote.social.feed.infrastructure.redis.FeedRedisStore;
import com.bluenote.social.feed.infrastructure.redis.FeedRedisStore.FeedRedisItem;
import com.bluenote.social.relation.api.dto.InternalFollowerPageItem;
import com.bluenote.social.relation.api.dto.InternalFollowingPageItem;
import com.bluenote.social.relation.api.dto.RelationUserSummary;
import com.bluenote.social.relation.application.RelationApplicationService;
import com.bluenote.social.relation.infrastructure.client.MemberInternalClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FeedApplicationService {

    private static final Logger log = LoggerFactory.getLogger(FeedApplicationService.class);
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> PROGRESS_TYPE = new TypeReference<>() {
    };

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int FOLLOWING_SCAN_SIZE = 500;
    private static final int FOLLOWER_SCAN_SIZE = 500;
    private static final int AUTHOR_RECENT_LIMIT = 50;
    private static final int BACKFILL_LIMIT = 20;
    private static final int FANOUT_BATCH_SIZE = 500;
    private static final int FANOUT_DISPATCH_BATCH_SIZE = 100;
    private static final int INBOX_LIMIT = 2000;
    private static final int AUTHOR_OUTBOX_LIMIT = 1000;
    private static final int MAX_EVENT_ID_LENGTH = 128;

    private static final String ITEM_VISIBLE = "VISIBLE";
    private static final String ITEM_HIDDEN = "HIDDEN";
    private static final String ITEM_DELETED = "DELETED";
    private static final String NOTE_PUBLIC = "PUBLIC";
    private static final String NOTE_PUBLISHED = "PUBLISHED";
    private static final String SOURCE_PUSH = "PUSH";
    private static final String SOURCE_PULL = "PULL";
    private static final String SOURCE_FOLLOW_BACKFILL = "FOLLOW_BACKFILL";
    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_RUNNING = "RUNNING";
    private static final String TASK_SUCCESS = "SUCCESS";
    private static final String MESSAGE_PENDING = "PENDING";
    private static final String CONSUME_PROCESSING = "PROCESSING";
    private static final String CONSUME_SUCCESS = "SUCCESS";

    private final RelationApplicationService relationApplicationService;
    private final ContentNoteClient contentNoteClient;
    private final CounterApplicationService counterApplicationService;
    private final MemberInternalClient memberInternalClient;
    private final FeedNoteIndexMapper noteIndexMapper;
    private final FeedInboxItemMapper inboxItemMapper;
    private final FeedFanoutTaskMapper fanoutTaskMapper;
    private final FeedFanoutSubTaskMapper fanoutSubTaskMapper;
    private final FeedRebuildTaskMapper rebuildTaskMapper;
    private final FeedCleanupTaskMapper cleanupTaskMapper;
    private final FeedConsumeRecordMapper consumeRecordMapper;
    private final FeedOutboxEventMapper outboxEventMapper;
    private final FeedRedisStore redisStore;
    private final RocketMqProducerClient rocketMqProducerClient;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;

    public FeedApplicationService(
            RelationApplicationService relationApplicationService,
            ContentNoteClient contentNoteClient,
            CounterApplicationService counterApplicationService,
            MemberInternalClient memberInternalClient,
            FeedNoteIndexMapper noteIndexMapper,
            FeedInboxItemMapper inboxItemMapper,
            FeedFanoutTaskMapper fanoutTaskMapper,
            FeedFanoutSubTaskMapper fanoutSubTaskMapper,
            FeedRebuildTaskMapper rebuildTaskMapper,
            FeedCleanupTaskMapper cleanupTaskMapper,
            FeedConsumeRecordMapper consumeRecordMapper,
            FeedOutboxEventMapper outboxEventMapper,
            FeedRedisStore redisStore,
            RocketMqProducerClient rocketMqProducerClient,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper
    ) {
        this.relationApplicationService = relationApplicationService;
        this.contentNoteClient = contentNoteClient;
        this.counterApplicationService = counterApplicationService;
        this.memberInternalClient = memberInternalClient;
        this.noteIndexMapper = noteIndexMapper;
        this.inboxItemMapper = inboxItemMapper;
        this.fanoutTaskMapper = fanoutTaskMapper;
        this.fanoutSubTaskMapper = fanoutSubTaskMapper;
        this.rebuildTaskMapper = rebuildTaskMapper;
        this.cleanupTaskMapper = cleanupTaskMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.redisStore = redisStore;
        this.rocketMqProducerClient = rocketMqProducerClient;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
    }

    public FollowingFeedResponse followingFeed(String userId, String cursor, Integer size) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        FeedCursor feedCursor = parseCursor(cursor);
        int pageSize = normalizeSize(size);
        LocalDateTime now = now();
        tryRedis(() -> redisStore.markActive(parsedUserId, now));

        Set<Long> followingAuthors = followingAuthorIds(userId).stream()
                .map(authorId -> parseId(authorId, ApiErrorCode.PARAM_INVALID))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (followingAuthors.isEmpty()) {
            return new FollowingFeedResponse(List.of(), null, false, false);
        }

        CandidatePage candidatePage = candidatePage(parsedUserId, followingAuthors, feedCursor, pageSize);
        List<FeedCandidate> candidates = dedupeCandidates(candidatePage.items().stream()
                .filter(candidate -> followingAuthors.contains(candidate.authorId()))
                .filter(candidate -> beforeCursor(candidate.publishedAt(), candidate.noteId(), feedCursor))
                .sorted(candidateComparator())
                .toList(), pageSize * 3 + 1);
        if (candidates.isEmpty()) {
            candidates = fallbackPullCandidates(userId, feedCursor, pageSize + 1);
        }
        if (candidates.isEmpty()) {
            return new FollowingFeedResponse(List.of(), null, false, candidatePage.degraded());
        }

        NoteResolution noteResolution = resolveNotes(userId, candidates);
        List<FeedCandidateNote> visibleNotes = noteResolution.items().stream()
                .filter(item -> followingAuthors.contains(item.candidate().authorId()))
                .filter(item -> NOTE_PUBLISHED.equals(item.note().noteStatus()) && NOTE_PUBLIC.equals(item.note().visibility()))
                .limit(pageSize + 1L)
                .toList();
        boolean hasMore = visibleNotes.size() > pageSize;
        List<FeedCandidateNote> pageNotes = hasMore ? visibleNotes.subList(0, pageSize) : visibleNotes;
        if (pageNotes.isEmpty()) {
            return new FollowingFeedResponse(List.of(), null, false, true);
        }

        PageDependencies dependencies = pageDependencies(pageNotes.stream().map(FeedCandidateNote::note).toList());
        boolean degraded = candidatePage.degraded() || noteResolution.degraded() || dependencies.degraded();
        List<FeedCardResponse> items = pageNotes.stream()
                .map(item -> toFeedCard(userId, item, dependencies))
                .toList();
        return new FollowingFeedResponse(items, nextCursor(pageNotes, hasMore), hasMore, degraded);
    }

    @Transactional
    public void consumeEvent(FeedConsumeEventRequest request) {
        LocalDateTime now = now();
        FeedConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(
                request.consumerGroup(),
                request.eventId()
        );
        if (existing != null && CONSUME_SUCCESS.equals(existing.getConsumeStatus())) {
            return;
        }

        reserveConsumeRecord(request, now);
        try {
            switch (request.eventType()) {
                case "NotePublished" -> consumeNotePublished(request, now);
                case "NoteDeleted" -> consumeNoteDeleted(request, now);
                case "NoteUpdated" -> consumeNoteUpdated(request, now);
                case "NoteVisibilityChanged" -> consumeNoteVisibilityChanged(request, now);
                case "NoteStatusChanged" -> consumeNoteStatusChanged(request, now);
                case "UserFollowed" -> consumeUserFollowed(request, now);
                case "UserUnfollowed" -> consumeUserUnfollowed(request, now);
                case "FeedFanoutSubTaskCreated" -> executeFanoutSubTask(stringValue(request.payload().get("subTaskId")));
                default -> {
                }
            }
            consumeRecordMapper.markSuccess(request.consumerGroup(), request.eventId());
        } catch (RuntimeException exception) {
            consumeRecordMapper.markFail(request.consumerGroup(), request.eventId(), truncate(exception.getMessage(), 512));
            throw exception;
        }
    }

    @Transactional
    public FeedRebuildResponse rebuildUser(String userId, FeedRebuildRequest request) {
        Long parsedUserId = parseId(userId, ApiErrorCode.PARAM_INVALID);
        LocalDateTime now = now();
        String taskId = "feed_rebuild_" + parsedUserId + "_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(now);
        FeedRebuildTaskEntity task = new FeedRebuildTaskEntity();
        task.setTaskId(taskId);
        task.setUserId(parsedUserId);
        task.setReason(request == null || request.reason() == null || request.reason().isBlank() ? "MANUAL" : request.reason());
        task.setTaskStatus(TASK_PENDING);
        task.setProgressJson(progressJson(0, 0, 0));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        rebuildTaskMapper.insert(task);

        runRebuildTask(taskId);
        FeedRebuildTaskEntity stored = rebuildTaskMapper.selectByTaskId(taskId);
        return new FeedRebuildResponse(taskId, stored.getTaskStatus());
    }

    public FeedRebuildTaskResponse rebuildTask(String taskId) {
        FeedRebuildTaskEntity task = rebuildTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.FEED_REBUILD_TASK_NOT_FOUND);
        }
        return new FeedRebuildTaskResponse(
                task.getTaskId(),
                String.valueOf(task.getUserId()),
                task.getReason(),
                task.getTaskStatus(),
                parseProgress(task.getProgressJson())
        );
    }

    public FeedFanoutTaskResponse fanoutTask(String taskId) {
        FeedFanoutTaskEntity task = fanoutTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.FEED_FANOUT_TASK_NOT_FOUND);
        }
        List<FeedFanoutSubTaskResponse> subTasks = fanoutSubTaskMapper.selectByTaskId(taskId).stream()
                .map(this::toSubTaskResponse)
                .toList();
        return new FeedFanoutTaskResponse(
                task.getTaskId(),
                String.valueOf(task.getNoteId()),
                String.valueOf(task.getAuthorId()),
                task.getTaskStatus(),
                safeInt(task.getTargetCount()),
                safeInt(task.getSuccessCount()),
                safeInt(task.getFailedCount()),
                subTasks
        );
    }

    @Transactional
    public FeedFanoutTaskResponse retryFanoutTask(String taskId) {
        FeedFanoutTaskEntity task = fanoutTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.FEED_FANOUT_TASK_NOT_FOUND);
        }
        fanoutTaskMapper.markRunning(taskId);
        for (FeedFanoutSubTaskEntity subTask : fanoutSubTaskMapper.selectByTaskId(taskId)) {
            if (!TASK_SUCCESS.equals(subTask.getSubTaskStatus())) {
                executeFanoutSubTask(subTask.getSubTaskId());
            }
        }
        return fanoutTask(taskId);
    }

    @Scheduled(initialDelayString = "${bluenote.feed.fanout-dispatch.initial-delay-ms:5000}",
            fixedDelayString = "${bluenote.feed.fanout-dispatch.fixed-delay-ms:3000}")
    public void dispatchPendingFanoutMessages() {
        if (!rocketMqProducerClient.available()) {
            return;
        }
        List<FeedFanoutSubTaskEntity> subTasks = fanoutSubTaskMapper.selectRetryableMessages(now(), FANOUT_DISPATCH_BATCH_SIZE);
        for (FeedFanoutSubTaskEntity subTask : subTasks) {
            try {
                rocketMqProducerClient.send("feed-fanout-task-event", subTask.getSubTaskId(), fanoutMessageBody(subTask));
                fanoutSubTaskMapper.markMessageSent(subTask.getSubTaskId());
            } catch (Exception exception) {
                fanoutSubTaskMapper.markMessageFailed(
                        subTask.getSubTaskId(),
                        now().plusSeconds(30L * (safeInt(subTask.getRetryCount()) + 1L)),
                        truncate(exception.getMessage(), 512)
                );
            }
        }
    }

    private CandidatePage candidatePage(Long userId, Set<Long> followingAuthors, FeedCursor cursor, int pageSize) {
        boolean degraded = false;
        List<FeedCandidate> candidates = List.of();
        if (cursor.sortAt() == null) {
            try {
                List<Long> redisNoteIds = redisStore.inboxNoteIds(userId, pageSize * 3 + 1);
                candidates = candidatesFromRedis(userId, redisNoteIds);
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }
        if (candidates.isEmpty()) {
            List<FeedInboxItemEntity> inboxItems = inboxItemMapper.selectUserPage(
                    userId,
                    cursor.sortAt() == null ? null : LocalDateTime.ofInstant(cursor.sortAt(), CHINA_ZONE),
                    cursor.noteId(),
                    pageSize * 3 + 1
            );
            candidates = candidatesFromInbox(inboxItems);
        }
        if (candidates.isEmpty()) {
            candidates = fallbackPullCandidates(String.valueOf(userId), cursor, pageSize + 1);
            degraded = true;
        }
        return new CandidatePage(candidates.stream()
                .filter(candidate -> followingAuthors.contains(candidate.authorId()))
                .toList(), degraded);
    }

    private List<FeedCandidate> dedupeCandidates(List<FeedCandidate> sortedCandidates, int limit) {
        Map<Long, FeedCandidate> deduped = new LinkedHashMap<>();
        for (FeedCandidate candidate : sortedCandidates) {
            deduped.putIfAbsent(candidate.noteId(), candidate);
            if (deduped.size() >= limit) {
                break;
            }
        }
        return List.copyOf(deduped.values());
    }

    private List<FeedCandidate> candidatesFromRedis(Long userId, List<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return List.of();
        }
        Map<Long, FeedInboxItemEntity> inboxItems = inboxItemMapper.selectUserItemsByNoteIds(userId, noteIds).stream()
                .collect(Collectors.toMap(FeedInboxItemEntity::getNoteId, Function.identity()));
        Map<Long, FeedNoteIndexEntity> indexes = noteIndexMapper.selectByNoteIds(noteIds).stream()
                .collect(Collectors.toMap(FeedNoteIndexEntity::getNoteId, Function.identity()));
        return noteIds.stream()
                .map(noteId -> {
                    FeedInboxItemEntity item = inboxItems.get(noteId);
                    FeedNoteIndexEntity index = indexes.get(noteId);
                    if (item == null || index == null || !ITEM_VISIBLE.equals(index.getItemStatus())) {
                        return null;
                    }
                    return new FeedCandidate(
                            item.getNoteId(),
                            item.getAuthorId(),
                            item.getPublishedAt(),
                            item.getSourceType(),
                            valueOrEmpty(index.getTitleSnapshot()),
                            valueOrEmpty(index.getContentPreviewSnapshot()),
                            index.getCoverUrlSnapshot()
                    );
                })
                .filter(candidate -> candidate != null)
                .toList();
    }

    private List<FeedCandidate> candidatesFromInbox(List<FeedInboxItemEntity> inboxItems) {
        if (inboxItems.isEmpty()) {
            return List.of();
        }
        Map<Long, FeedNoteIndexEntity> indexes = noteIndexMapper.selectByNoteIds(inboxItems.stream()
                        .map(FeedInboxItemEntity::getNoteId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(FeedNoteIndexEntity::getNoteId, Function.identity()));
        return inboxItems.stream()
                .map(item -> {
                    FeedNoteIndexEntity index = indexes.get(item.getNoteId());
                    if (index != null && !ITEM_VISIBLE.equals(index.getItemStatus())) {
                        return null;
                    }
                    return new FeedCandidate(
                            item.getNoteId(),
                            item.getAuthorId(),
                            item.getPublishedAt(),
                            item.getSourceType(),
                            index == null ? "" : valueOrEmpty(index.getTitleSnapshot()),
                            index == null ? "" : valueOrEmpty(index.getContentPreviewSnapshot()),
                            index == null ? null : index.getCoverUrlSnapshot()
                    );
                })
                .filter(candidate -> candidate != null)
                .toList();
    }

    private List<FeedCandidate> fallbackPullCandidates(String userId, FeedCursor feedCursor, int limit) {
        List<String> authorIds = followingAuthorIds(userId);
        if (authorIds.isEmpty()) {
            return List.of();
        }
        try {
            return recentNotes(authorIds, feedCursor).stream()
                    .filter(note -> NOTE_PUBLISHED.equals(note.noteStatus()) && NOTE_PUBLIC.equals(note.visibility()))
                    .filter(note -> beforeCursor(parsePublishedAt(note.publishedAt()).toInstant(), parseNoteId(note.noteId()), feedCursor))
                    .sorted(noteComparator())
                    .limit(limit)
                    .map(note -> new FeedCandidate(
                            parseNoteId(note.noteId()),
                            parseId(note.authorId(), ApiErrorCode.PARAM_INVALID),
                            parsePublishedAt(note.publishedAt()).atZoneSameInstant(CHINA_ZONE).toLocalDateTime(),
                            SOURCE_PULL,
                            valueOrEmpty(note.title()),
                            valueOrEmpty(note.contentPreview()),
                            note.coverUrl()
                    ))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private NoteResolution resolveNotes(String userId, List<FeedCandidate> candidates) {
        List<String> noteIds = candidates.stream()
                .map(candidate -> String.valueOf(candidate.noteId()))
                .distinct()
                .toList();
        Map<String, NoteSummary> summaries;
        boolean degraded = false;
        try {
            summaries = contentNoteClient.batchSummary(noteIds, userId, false).stream()
                    .collect(Collectors.toMap(NoteSummary::noteId, Function.identity()));
            degraded = summaries.size() < noteIds.size();
        } catch (RuntimeException exception) {
            degraded = true;
            summaries = Map.of();
        }

        List<FeedCandidateNote> items = new ArrayList<>();
        for (FeedCandidate candidate : candidates) {
            NoteSummary summary = summaries.get(String.valueOf(candidate.noteId()));
            if (summary == null) {
                if (degraded) {
                    summary = new NoteSummary(
                            String.valueOf(candidate.noteId()),
                            String.valueOf(candidate.authorId()),
                            candidate.title(),
                            candidate.contentPreview(),
                            candidate.coverUrl(),
                            NOTE_PUBLISHED,
                            NOTE_PUBLIC,
                            toOffsetString(candidate.publishedAt())
                    );
                } else {
                    continue;
                }
            }
            items.add(new FeedCandidateNote(candidate, summary));
        }
        return new NoteResolution(items, degraded);
    }

    private PageDependencies pageDependencies(List<NoteSummary> notes) {
        boolean degraded = false;
        List<String> authorIds = notes.stream()
                .map(NoteSummary::authorId)
                .distinct()
                .toList();
        Map<String, RelationUserSummary> authors;
        try {
            authors = memberInternalClient.batchSummary(authorIds);
            degraded = authors.size() < authorIds.size();
        } catch (RuntimeException exception) {
            degraded = true;
            authors = Map.of();
        }

        Map<String, CounterItem> counters;
        try {
            counters = counterApplicationService.batch(new CounterBatchRequest(notes.stream()
                            .map(note -> new CounterTarget(
                                    "NOTE",
                                    note.noteId(),
                                    List.of("like_count", "collect_count", "comment_count")
                            ))
                            .toList()))
                    .items()
                    .stream()
                    .collect(Collectors.toMap(CounterItem::targetId, Function.identity()));
            degraded = degraded || counters.values().stream().anyMatch(CounterItem::degraded);
        } catch (RuntimeException exception) {
            degraded = true;
            counters = Map.of();
        }

        return new PageDependencies(authors, counters, degraded);
    }

    private FeedCardResponse toFeedCard(
            String userId,
            FeedCandidateNote item,
            PageDependencies dependencies
    ) {
        NoteSummary note = item.note();
        CounterItem counter = dependencies.counters().get(note.noteId());
        Map<String, Long> counts = counter == null ? Map.of() : counter.counts();
        boolean itemDegraded = dependencies.degraded()
                || counter == null
                || counter.degraded()
                || !dependencies.authors().containsKey(note.authorId());
        return new FeedCardResponse(
                userId + "_" + note.noteId(),
                note.noteId(),
                dependencies.authors().getOrDefault(note.authorId(), fallbackAuthor(note.authorId())),
                valueOrEmpty(note.title()),
                valueOrEmpty(note.contentPreview()),
                note.coverUrl(),
                "IMAGE_TEXT",
                new FeedCountsResponse(
                        counts.getOrDefault("like_count", 0L),
                        counts.getOrDefault("collect_count", 0L),
                        counts.getOrDefault("comment_count", 0L)
                ),
                null,
                note.publishedAt(),
                item.candidate().sourceType(),
                itemDegraded
        );
    }

    private void consumeNotePublished(FeedConsumeEventRequest request, LocalDateTime now) {
        FeedNote note = noteFromPayload(request.payload());
        if (!note.publicPublished()) {
            return;
        }
        upsertVisibleNote(note, now);
        createFanoutTask(note, request.eventId(), now);
    }

    private void consumeNoteDeleted(FeedConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long noteId = id(payload, "noteId");
        Long authorId = optionalId(payload.get("authorId"));
        hideNote(noteId, authorId, ITEM_DELETED, "DELETED", stringValue(payload.get("visibility")), now);
    }

    private void consumeNoteUpdated(FeedConsumeEventRequest request, LocalDateTime now) {
        Long noteId = id(request.payload(), "noteId");
        FeedNote note = noteFromBatchSummary(noteId);
        if (note != null && note.publicPublished()) {
            upsertVisibleNote(note, now);
        }
    }

    private void consumeNoteVisibilityChanged(FeedConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long noteId = id(payload, "noteId");
        Long authorId = id(payload, "authorId");
        String toVisibility = stringValue(payload.get("toVisibility"));
        String noteStatus = stringValue(payload.get("noteStatus"));
        if (NOTE_PUBLIC.equals(toVisibility) && NOTE_PUBLISHED.equals(noteStatus)) {
            FeedNote note = noteFromBatchSummary(noteId);
            if (note != null && note.publicPublished()) {
                upsertVisibleNote(note, now);
                createFanoutTask(note, request.eventId(), now);
            }
            return;
        }
        hideNote(noteId, authorId, ITEM_HIDDEN, noteStatus, toVisibility, now);
    }

    private void consumeNoteStatusChanged(FeedConsumeEventRequest request, LocalDateTime now) {
        Map<String, Object> payload = request.payload();
        Long noteId = id(payload, "noteId");
        Long authorId = id(payload, "authorId");
        String toStatus = stringValue(payload.get("toStatus"));
        String visibility = stringValue(payload.get("visibility"));
        if (NOTE_PUBLIC.equals(visibility) && NOTE_PUBLISHED.equals(toStatus)) {
            FeedNote note = noteFromBatchSummary(noteId);
            if (note != null && note.publicPublished()) {
                upsertVisibleNote(note, now);
                createFanoutTask(note, request.eventId(), now);
            }
            return;
        }
        hideNote(noteId, authorId, ITEM_HIDDEN, toStatus, visibility, now);
    }

    private void consumeUserFollowed(FeedConsumeEventRequest request, LocalDateTime now) {
        Long followerId = id(request.payload(), "followerId");
        Long followeeId = id(request.payload(), "followeeId");
        List<FeedNote> notes = recentFeedNotes(followeeId, BACKFILL_LIMIT);
        for (FeedNote note : notes) {
            upsertVisibleNote(note, now);
            deliverToInbox(followerId, note, SOURCE_FOLLOW_BACKFILL, now);
        }
    }

    private void consumeUserUnfollowed(FeedConsumeEventRequest request, LocalDateTime now) {
        Long followerId = id(request.payload(), "followerId");
        Long followeeId = id(request.payload(), "followeeId");
        int affected = inboxItemMapper.hideByUserAndAuthor(followerId, followeeId, now);
        insertCleanupTask("UNFOLLOW", followerId, followeeId, null, affected, now);
    }

    private void createFanoutTask(FeedNote note, String sourceEventId, LocalDateTime now) {
        List<List<Long>> batches = followerBatches(note.authorId());
        String taskId = taskId("feed_fanout", note.noteId() + "_" + sourceEventId);
        int targetCount = batches.stream().mapToInt(List::size).sum();

        FeedFanoutTaskEntity task = new FeedFanoutTaskEntity();
        task.setTaskId(taskId);
        task.setNoteId(note.noteId());
        task.setAuthorId(note.authorId());
        task.setSourceEventId(sourceEventId);
        task.setTaskStatus(targetCount == 0 ? TASK_SUCCESS : TASK_PENDING);
        task.setTargetCount(targetCount);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        fanoutTaskMapper.insertIgnore(task);

        for (int index = 0; index < batches.size(); index++) {
            FeedFanoutSubTaskEntity subTask = new FeedFanoutSubTaskEntity();
            subTask.setSubTaskId(taskId(taskId, String.valueOf(index + 1)));
            subTask.setTaskId(taskId);
            subTask.setNoteId(note.noteId());
            subTask.setAuthorId(note.authorId());
            subTask.setPublishedAt(note.publishedAt());
            subTask.setTargetUserIdsJson(writeJson(batches.get(index).stream().map(String::valueOf).toList()));
            subTask.setSubTaskStatus(TASK_PENDING);
            subTask.setMessageStatus(MESSAGE_PENDING);
            subTask.setRetryCount(0);
            subTask.setCreatedAt(now);
            subTask.setUpdatedAt(now);
            fanoutSubTaskMapper.insertIgnore(subTask);
        }

        if (targetCount == 0) {
            insertFeedDeliveredOutbox(taskId, note.noteId(), note.authorId(), 0, 0, now);
        } else {
            runAfterCommit(this::dispatchPendingFanoutMessages);
        }
    }

    private void executeFanoutSubTask(String subTaskId) {
        if (subTaskId == null || subTaskId.isBlank()) {
            throw new BusinessException(ApiErrorCode.FEED_FANOUT_TASK_NOT_FOUND);
        }
        FeedFanoutSubTaskEntity subTask = fanoutSubTaskMapper.selectBySubTaskId(subTaskId);
        if (subTask == null) {
            throw new BusinessException(ApiErrorCode.FEED_FANOUT_TASK_NOT_FOUND);
        }
        if (TASK_SUCCESS.equals(subTask.getSubTaskStatus())) {
            return;
        }
        fanoutTaskMapper.markRunning(subTask.getTaskId());
        fanoutSubTaskMapper.markRunning(subTaskId);
        try {
            FeedNote note = noteForSubTask(subTask);
            List<Long> targetUserIds = targetUserIds(subTask);
            int startIndex = nextFanoutStartIndex(targetUserIds, subTask.getProgressUserId());
            for (int index = startIndex; index < targetUserIds.size(); index++) {
                Long targetUserId = targetUserIds.get(index);
                deliverToInbox(targetUserId, note, SOURCE_PUSH, now());
                fanoutSubTaskMapper.markProgress(subTaskId, targetUserId);
            }
            fanoutSubTaskMapper.markSuccess(subTaskId);
            refreshFanoutTaskStatus(subTask.getTaskId(), now());
        } catch (RuntimeException exception) {
            fanoutSubTaskMapper.markFailed(subTaskId, truncate(exception.getMessage(), 512));
            fanoutTaskMapper.markFailed(subTask.getTaskId(), truncate(exception.getMessage(), 512));
            throw exception;
        }
    }

    private FeedNote noteForSubTask(FeedFanoutSubTaskEntity subTask) {
        FeedNoteIndexEntity index = noteIndexMapper.selectByNoteId(subTask.getNoteId());
        if (index == null || !ITEM_VISIBLE.equals(index.getItemStatus())) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
        }
        return noteFromIndex(index);
    }

    private void refreshFanoutTaskStatus(String taskId, LocalDateTime now) {
        List<FeedFanoutSubTaskEntity> subTasks = fanoutSubTaskMapper.selectByTaskId(taskId);
        int successCount = 0;
        int failedCount = 0;
        boolean allSuccess = true;
        for (FeedFanoutSubTaskEntity subTask : subTasks) {
            int targetCount = targetUserIds(subTask).size();
            if (TASK_SUCCESS.equals(subTask.getSubTaskStatus())) {
                successCount += targetCount;
            } else {
                allSuccess = false;
                if (!TASK_PENDING.equals(subTask.getSubTaskStatus()) && !TASK_RUNNING.equals(subTask.getSubTaskStatus())) {
                    failedCount += targetCount;
                }
            }
        }
        if (allSuccess) {
            fanoutTaskMapper.markSuccess(taskId, successCount, failedCount);
            FeedFanoutTaskEntity task = fanoutTaskMapper.selectByTaskId(taskId);
            insertFeedDeliveredOutbox(taskId, task.getNoteId(), task.getAuthorId(), successCount, failedCount, now);
        }
    }

    private void runRebuildTask(String taskId) {
        FeedRebuildTaskEntity task = rebuildTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ApiErrorCode.FEED_REBUILD_TASK_NOT_FOUND);
        }
        rebuildTaskMapper.markRunning(taskId);
        try {
            List<FeedNote> candidates = new ArrayList<>();
            for (String authorId : followingAuthorIds(String.valueOf(task.getUserId()))) {
                Long parsedAuthorId = parseId(authorId, ApiErrorCode.PARAM_INVALID);
                candidates.addAll(recentFeedNotes(parsedAuthorId, AUTHOR_RECENT_LIMIT));
            }
            List<FeedNote> page = candidates.stream()
                    .filter(FeedNote::publicPublished)
                    .sorted(feedNoteComparator())
                    .limit(INBOX_LIMIT)
                    .toList();
            LocalDateTime now = now();
            for (FeedNote note : page) {
                upsertVisibleNote(note, now);
                deliverToInbox(task.getUserId(), note, SOURCE_PULL, now);
            }
            runAfterCommit(() -> redisStore.replaceInbox(
                    task.getUserId(),
                    page.stream().map(note -> new FeedRedisItem(note.noteId(), note.publishedAt())).toList(),
                    INBOX_LIMIT
            ));
            insertFeedRebuiltOutbox(taskId, task.getUserId(), page.size(), now);
            rebuildTaskMapper.markSuccess(taskId, progressJson(page.size(), page.size(), 0));
        } catch (RuntimeException exception) {
            rebuildTaskMapper.markFailed(taskId, progressJson(1, 0, 1), truncate(exception.getMessage(), 512));
            throw exception;
        }
    }

    private void upsertVisibleNote(FeedNote note, LocalDateTime now) {
        FeedNoteIndexEntity entity = new FeedNoteIndexEntity();
        entity.setNoteId(note.noteId());
        entity.setAuthorId(note.authorId());
        entity.setTitleSnapshot(truncate(valueOrEmpty(note.title()), 128));
        entity.setContentPreviewSnapshot(truncate(valueOrEmpty(note.contentPreview()), 256));
        entity.setCoverUrlSnapshot(note.coverUrl());
        entity.setVisibility(note.visibility());
        entity.setNoteStatus(note.noteStatus());
        entity.setItemStatus(ITEM_VISIBLE);
        entity.setPublishedAt(note.publishedAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        noteIndexMapper.upsert(entity);
        runAfterCommit(() -> redisStore.addAuthorOutbox(note.authorId(), note.noteId(), note.publishedAt(), AUTHOR_OUTBOX_LIMIT));
    }

    private void hideNote(
            Long noteId,
            Long authorId,
            String itemStatus,
            String noteStatus,
            String visibility,
            LocalDateTime now
    ) {
        FeedNoteIndexEntity existing = noteIndexMapper.selectByNoteId(noteId);
        Long resolvedAuthorId = authorId == null && existing != null ? existing.getAuthorId() : authorId;
        String resolvedStatus = noteStatus == null || noteStatus.isBlank()
                ? existing == null ? "UNKNOWN" : existing.getNoteStatus()
                : noteStatus;
        String resolvedVisibility = visibility == null || visibility.isBlank()
                ? existing == null ? "UNKNOWN" : existing.getVisibility()
                : visibility;
        noteIndexMapper.markStatus(noteId, resolvedVisibility, resolvedStatus, itemStatus, now);
        int affected = inboxItemMapper.markByNote(noteId, itemStatus, now);
        if (resolvedAuthorId != null) {
            runAfterCommit(() -> redisStore.removeAuthorOutbox(resolvedAuthorId, noteId));
        }
        insertCleanupTask("NOTE_" + itemStatus, null, resolvedAuthorId, noteId, affected, now);
    }

    private void deliverToInbox(Long userId, FeedNote note, String sourceType, LocalDateTime now) {
        FeedInboxItemEntity entity = new FeedInboxItemEntity();
        entity.setId(idGenerator.nextId());
        entity.setUserId(userId);
        entity.setNoteId(note.noteId());
        entity.setAuthorId(note.authorId());
        entity.setPublishedAt(note.publishedAt());
        entity.setSourceType(sourceType);
        entity.setItemStatus(ITEM_VISIBLE);
        entity.setDeliveredAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        inboxItemMapper.upsert(entity);
        runAfterCommit(() -> redisStore.addInbox(userId, note.noteId(), note.publishedAt(), INBOX_LIMIT));
    }

    private void insertCleanupTask(
            String cleanupType,
            Long userId,
            Long authorId,
            Long noteId,
            int affected,
            LocalDateTime now
    ) {
        FeedCleanupTaskEntity task = new FeedCleanupTaskEntity();
        task.setTaskId(taskId("feed_cleanup", cleanupType + "_" + userId + "_" + authorId + "_" + noteId + "_" + now));
        task.setCleanupType(cleanupType);
        task.setUserId(userId);
        task.setAuthorId(authorId);
        task.setNoteId(noteId);
        task.setTaskStatus(TASK_SUCCESS);
        task.setProgressJson(progressJson(affected, affected, 0));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        cleanupTaskMapper.insert(task);
    }

    private void insertFeedDeliveredOutbox(
            String taskId,
            Long noteId,
            Long authorId,
            int deliveredCount,
            int failedCount,
            LocalDateTime now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("noteId", String.valueOf(noteId));
        payload.put("authorId", String.valueOf(authorId));
        payload.put("deliveredCount", deliveredCount);
        payload.put("failedCount", failedCount);
        insertOutbox("FeedDelivered", eventId("evt_feed_delivered", taskId), String.valueOf(noteId), payload, now);
    }

    private void insertFeedRebuiltOutbox(String taskId, Long userId, int rebuiltCount, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("userId", String.valueOf(userId));
        payload.put("rebuiltCount", rebuiltCount);
        payload.put("rebuiltAt", toOffsetString(now));
        insertOutbox("FeedRebuilt", eventId("evt_feed_rebuilt", taskId), String.valueOf(userId), payload, now);
    }

    private void insertOutbox(
            String eventType,
            String eventId,
            String aggregateId,
            Map<String, Object> payload,
            LocalDateTime now
    ) {
        FeedOutboxEventEntity entity = new FeedOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, aggregateId, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            outboxEventMapper.insert(entity);
        } catch (DuplicateKeyException ignored) {
            // Task completion can be retried; feed events are identified by deterministic event IDs.
        }
    }

    private void reserveConsumeRecord(FeedConsumeEventRequest request, LocalDateTime now) {
        FeedConsumeRecordEntity entity = new FeedConsumeRecordEntity();
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

    private String fanoutMessageBody(FeedFanoutSubTaskEntity subTask) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", subTask.getTaskId());
        payload.put("subTaskId", subTask.getSubTaskId());
        payload.put("noteId", String.valueOf(subTask.getNoteId()));
        payload.put("authorId", String.valueOf(subTask.getAuthorId()));
        payload.put("publishedAt", toOffsetString(subTask.getPublishedAt()));
        payload.put("targetUserIds", targetUserIds(subTask).stream().map(String::valueOf).toList());
        return jsonPayloads.stringify(eventEnvelope(
                eventId("evt_feed_fanout_sub_task", subTask.getSubTaskId()),
                "FeedFanoutSubTaskCreated",
                subTask.getTaskId(),
                payload,
                now()
        ));
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
        envelope.put("producer", "bluenote-feed");
        envelope.put("bizKey", aggregateId);
        envelope.put("payload", payload);
        return envelope;
    }

    private List<String> followingAuthorIds(String userId) {
        List<String> authorIds = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            var page = relationApplicationService.internalFollowingPage(userId, cursor, FOLLOWING_SCAN_SIZE);
            authorIds.addAll(page.items().stream()
                    .map(InternalFollowingPageItem::followeeId)
                    .toList());
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
        return authorIds.stream().distinct().toList();
    }

    private List<List<Long>> followerBatches(Long authorId) {
        List<List<Long>> batches = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            var page = relationApplicationService.internalFollowersPage(String.valueOf(authorId), cursor, FOLLOWER_SCAN_SIZE);
            List<Long> followerIds = page.items().stream()
                    .map(InternalFollowerPageItem::followerId)
                    .map(value -> parseId(value, ApiErrorCode.PARAM_INVALID))
                    .toList();
            for (int index = 0; index < followerIds.size(); index += FANOUT_BATCH_SIZE) {
                batches.add(followerIds.subList(index, Math.min(index + FANOUT_BATCH_SIZE, followerIds.size())));
            }
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
        return batches;
    }

    private List<FeedNote> recentFeedNotes(Long authorId, int limit) {
        List<FeedNote> indexed = noteIndexMapper.selectRecentVisibleByAuthor(authorId, limit).stream()
                .map(this::noteFromIndex)
                .toList();
        if (!indexed.isEmpty()) {
            return indexed;
        }
        return contentNoteClient.authorRecentNotes(List.of(String.valueOf(authorId)), limit, null).stream()
                .map(AuthorRecentNotes::notes)
                .flatMap(List::stream)
                .filter(note -> NOTE_PUBLISHED.equals(note.noteStatus()) && NOTE_PUBLIC.equals(note.visibility()))
                .map(this::noteFromSummary)
                .toList();
    }

    private List<NoteSummary> recentNotes(List<String> authorIds, FeedCursor feedCursor) {
        String publishedAfter = feedCursor.sortAt() == null
                ? OffsetDateTime.now(CHINA_ZONE).minusDays(90).toString()
                : null;
        List<NoteSummary> notes = new ArrayList<>();
        for (int index = 0; index < authorIds.size(); index += 50) {
            int end = Math.min(index + 50, authorIds.size());
            notes.addAll(contentNoteClient.authorRecentNotes(
                            authorIds.subList(index, end),
                            AUTHOR_RECENT_LIMIT,
                            publishedAfter
                    ).stream()
                    .map(AuthorRecentNotes::notes)
                    .flatMap(List::stream)
                    .toList());
        }
        return notes;
    }

    private FeedNote noteFromPayload(Map<String, Object> payload) {
        return new FeedNote(
                id(payload, "noteId"),
                id(payload, "authorId"),
                valueOrEmpty(stringValue(payload.get("title"))),
                valueOrEmpty(stringValue(payload.get("contentPreview"))),
                stringValue(payload.get("coverUrl")),
                stringValue(payload.get("visibility")),
                stringValue(payload.get("noteStatus")),
                parseOffsetDateTime(stringValue(payload.get("publishedAt")))
        );
    }

    private FeedNote noteFromBatchSummary(Long noteId) {
        return contentNoteClient.batchSummary(List.of(String.valueOf(noteId)), null, true).stream()
                .findFirst()
                .map(this::noteFromSummary)
                .orElse(null);
    }

    private FeedNote noteFromSummary(NoteSummary summary) {
        return new FeedNote(
                parseNoteId(summary.noteId()),
                parseId(summary.authorId(), ApiErrorCode.PARAM_INVALID),
                valueOrEmpty(summary.title()),
                valueOrEmpty(summary.contentPreview()),
                summary.coverUrl(),
                summary.visibility(),
                summary.noteStatus(),
                parsePublishedAt(summary.publishedAt()).atZoneSameInstant(CHINA_ZONE).toLocalDateTime()
        );
    }

    private FeedNote noteFromIndex(FeedNoteIndexEntity index) {
        return new FeedNote(
                index.getNoteId(),
                index.getAuthorId(),
                valueOrEmpty(index.getTitleSnapshot()),
                valueOrEmpty(index.getContentPreviewSnapshot()),
                index.getCoverUrlSnapshot(),
                index.getVisibility(),
                index.getNoteStatus(),
                index.getPublishedAt()
        );
    }

    private FeedCandidate candidateFromIndex(FeedNoteIndexEntity index, String sourceType) {
        return new FeedCandidate(
                index.getNoteId(),
                index.getAuthorId(),
                index.getPublishedAt(),
                sourceType,
                valueOrEmpty(index.getTitleSnapshot()),
                valueOrEmpty(index.getContentPreviewSnapshot()),
                index.getCoverUrlSnapshot()
        );
    }

    private RelationUserSummary fallbackAuthor(String authorId) {
        return new RelationUserSummary(authorId, null, null, null, null, "UNKNOWN", null);
    }

    private boolean beforeCursor(LocalDateTime publishedAt, Long noteId, FeedCursor cursor) {
        return beforeCursor(publishedAt.atZone(CHINA_ZONE).toInstant(), noteId, cursor);
    }

    private boolean beforeCursor(Instant publishedAt, Long noteId, FeedCursor cursor) {
        if (cursor.sortAt() == null || cursor.noteId() == null) {
            return true;
        }
        int compared = publishedAt.compareTo(cursor.sortAt());
        return compared < 0 || (compared == 0 && noteId < cursor.noteId());
    }

    private Comparator<FeedCandidate> candidateComparator() {
        return Comparator
                .comparing(FeedCandidate::publishedAt)
                .reversed()
                .thenComparing(FeedCandidate::noteId, Comparator.reverseOrder());
    }

    private Comparator<FeedNote> feedNoteComparator() {
        return Comparator
                .comparing(FeedNote::publishedAt)
                .reversed()
                .thenComparing(FeedNote::noteId, Comparator.reverseOrder());
    }

    private Comparator<NoteSummary> noteComparator() {
        return Comparator
                .comparing((NoteSummary note) -> parsePublishedAt(note.publishedAt()).toInstant())
                .reversed()
                .thenComparing((NoteSummary note) -> parseNoteId(note.noteId()), Comparator.reverseOrder());
    }

    private String nextCursor(List<FeedCandidateNote> items, boolean hasMore) {
        if (!hasMore || items.isEmpty()) {
            return null;
        }
        FeedCandidateNote last = items.get(items.size() - 1);
        return last.candidate().publishedAt().atZone(CHINA_ZONE).toInstant().toEpochMilli() + "_"
                + last.candidate().noteId();
    }

    private FeedCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new FeedCursor(null, null);
        }
        int separator = cursor.lastIndexOf('_');
        if (separator <= 0 || separator == cursor.length() - 1) {
            throw new BusinessException(ApiErrorCode.FEED_CURSOR_INVALID);
        }
        try {
            long publishedAtMillis = Long.parseLong(cursor.substring(0, separator));
            long noteId = Long.parseLong(cursor.substring(separator + 1));
            return new FeedCursor(Instant.ofEpochMilli(publishedAtMillis), noteId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.FEED_CURSOR_INVALID);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ApiErrorCode.FEED_SIZE_EXCEEDED);
        }
        return Math.max(1, size);
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

    private Long parseNoteId(String noteId) {
        return parseId(noteId, ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
    }

    private OffsetDateTime parsePublishedAt(String publishedAt) {
        try {
            if (publishedAt == null || publishedAt.isBlank()) {
                throw new IllegalArgumentException("blank");
            }
            return OffsetDateTime.parse(publishedAt);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
        }
    }

    private LocalDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
        } catch (RuntimeException exception) {
            return now();
        }
    }

    private Long id(Map<String, Object> payload, String field) {
        Long value = optionalId(payload.get(field));
        if (value == null) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        return value;
    }

    private Long optionalId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String toOffsetString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(CHINA_ZONE).toOffsetDateTime().toString();
    }

    private String progressJson(int total, int success, int failed) {
        return jsonPayloads.stringify(Map.of("total", total, "success", success, "failed", failed));
    }

    private FeedTaskProgress parseProgress(String progressJson) {
        try {
            Map<String, Integer> progress = objectMapper.readValue(progressJson, PROGRESS_TYPE);
            return new FeedTaskProgress(
                    progress.getOrDefault("total", 0),
                    progress.getOrDefault("success", 0),
                    progress.getOrDefault("failed", 0)
            );
        } catch (JsonProcessingException exception) {
            return new FeedTaskProgress(0, 0, 0);
        }
    }

    private List<Long> targetUserIds(FeedFanoutSubTaskEntity subTask) {
        try {
            return objectMapper.readValue(subTask.getTargetUserIdsJson(), STRING_LIST_TYPE).stream()
                    .map(value -> parseId(value, ApiErrorCode.PARAM_INVALID))
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
        }
    }

    private int nextFanoutStartIndex(List<Long> targetUserIds, Long progressUserId) {
        if (progressUserId == null) {
            return 0;
        }
        int progressIndex = targetUserIds.indexOf(progressUserId);
        if (progressIndex < 0) {
            return 0;
        }
        return Math.min(progressIndex + 1, targetUserIds.size());
    }

    private FeedFanoutSubTaskResponse toSubTaskResponse(FeedFanoutSubTaskEntity subTask) {
        return new FeedFanoutSubTaskResponse(
                subTask.getSubTaskId(),
                subTask.getSubTaskStatus(),
                subTask.getMessageStatus(),
                targetUserIds(subTask).size(),
                subTask.getProgressUserId() == null ? null : String.valueOf(subTask.getProgressUserId()),
                safeInt(subTask.getRetryCount())
        );
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize feed JSON", exception);
        }
    }

    private void tryRedis(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Redis is an online read model; MySQL snapshots remain the durable fallback.
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryRedis(action);
            }
        });
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String taskId(String prefix, String raw) {
        return normalizeId(prefix + "_" + raw);
    }

    private String eventId(String prefix, String raw) {
        return normalizeId(prefix + "_" + raw);
    }

    private String normalizeId(String raw) {
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

    private record FeedCursor(Instant sortAt, Long noteId) {
    }

    private record CandidatePage(List<FeedCandidate> items, boolean degraded) {
    }

    private record FeedCandidate(
            Long noteId,
            Long authorId,
            LocalDateTime publishedAt,
            String sourceType,
            String title,
            String contentPreview,
            String coverUrl
    ) {
    }

    private record FeedCandidateNote(FeedCandidate candidate, NoteSummary note) {
    }

    private record NoteResolution(List<FeedCandidateNote> items, boolean degraded) {
    }

    private record PageDependencies(
            Map<String, RelationUserSummary> authors,
            Map<String, CounterItem> counters,
            boolean degraded
    ) {
    }

    private record FeedNote(
            Long noteId,
            Long authorId,
            String title,
            String contentPreview,
            String coverUrl,
            String visibility,
            String noteStatus,
            LocalDateTime publishedAt
    ) {
        private boolean publicPublished() {
            return NOTE_PUBLIC.equals(visibility) && NOTE_PUBLISHED.equals(noteStatus);
        }
    }
}
