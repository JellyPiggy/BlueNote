package com.bluenote.content.comment.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.core.CursorPage;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.comment.api.dto.BatchCommentSummaryRequest;
import com.bluenote.content.comment.api.dto.BatchCommentSummaryResponse;
import com.bluenote.content.comment.api.dto.CommentAuthorResponse;
import com.bluenote.content.comment.api.dto.CommentCounterSourceItem;
import com.bluenote.content.comment.api.dto.CommentCounterSourceRequest;
import com.bluenote.content.comment.api.dto.CommentCounterSourceResponse;
import com.bluenote.content.comment.api.dto.CommentConsumeEventRequest;
import com.bluenote.content.comment.api.dto.CommentCursorPage;
import com.bluenote.content.comment.api.dto.CommentHotRebuildResponse;
import com.bluenote.content.comment.api.dto.CommentItemResponse;
import com.bluenote.content.comment.api.dto.CommentLikeResponse;
import com.bluenote.content.comment.api.dto.CommentSummaryItem;
import com.bluenote.content.comment.api.dto.CommentViewerActionResponse;
import com.bluenote.content.comment.api.dto.CreateCommentRequest;
import com.bluenote.content.comment.api.dto.CreateCommentResponse;
import com.bluenote.content.comment.api.dto.DeleteCommentResponse;
import com.bluenote.content.comment.api.dto.MyCommentItemResponse;
import com.bluenote.content.comment.api.dto.MyCommentNoteResponse;
import com.bluenote.content.comment.infrastructure.client.MemberInternalClient;
import com.bluenote.content.comment.infrastructure.entity.CommentConsumeRecordEntity;
import com.bluenote.content.comment.infrastructure.entity.CommentContentEntity;
import com.bluenote.content.comment.infrastructure.entity.CommentIdempotentRequestEntity;
import com.bluenote.content.comment.infrastructure.entity.CommentLikeEntity;
import com.bluenote.content.comment.infrastructure.entity.CommentOperationLogEntity;
import com.bluenote.content.comment.infrastructure.entity.CommentOutboxEventEntity;
import com.bluenote.content.comment.infrastructure.entity.ContentCommentEntity;
import com.bluenote.content.comment.infrastructure.entity.UserCommentEntity;
import com.bluenote.content.comment.infrastructure.mapper.CommentConsumeRecordMapper;
import com.bluenote.content.comment.infrastructure.mapper.CommentContentMapper;
import com.bluenote.content.comment.infrastructure.mapper.CommentIdempotentRequestMapper;
import com.bluenote.content.comment.infrastructure.mapper.CommentLikeMapper;
import com.bluenote.content.comment.infrastructure.mapper.CommentOperationLogMapper;
import com.bluenote.content.comment.infrastructure.mapper.CommentOutboxEventMapper;
import com.bluenote.content.comment.infrastructure.mapper.ContentCommentMapper;
import com.bluenote.content.comment.infrastructure.mapper.UserCommentMapper;
import com.bluenote.content.comment.infrastructure.redis.CommentRedisStore;
import com.bluenote.content.comment.infrastructure.redis.CommentRedisStore.CommentTimeMember;
import com.bluenote.content.comment.infrastructure.redis.CommentRedisStore.HotCommentMember;
import com.bluenote.content.common.ContentIdGenerator;
import com.bluenote.content.common.JsonPayloads;
import com.bluenote.content.note.api.dto.BatchNoteSummaryRequest;
import com.bluenote.content.note.api.dto.CommentCheckRequest;
import com.bluenote.content.note.api.dto.CommentCheckResponse;
import com.bluenote.content.note.api.dto.NoteSummaryItem;
import com.bluenote.content.note.application.NoteApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentApplicationService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String STATUS_VISIBLE = "VISIBLE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String LIKE_ACTIVE = "ACTIVE";
    private static final String LIKE_CANCELED = "CANCELED";
    private static final String SORT_HOT = "HOT";
    private static final String SORT_TIME_DESC = "TIME_DESC";
    private static final String SORT_TIME_ASC = "TIME_ASC";
    private static final String OP_CREATE_COMMENT = "COMMENT_CREATE";
    private static final String OP_REPLY_COMMENT = "COMMENT_REPLY";
    private static final int HOT_COMMENT_LIMIT = 1000;
    private static final int TIME_COMMENT_LIMIT = 1000;
    private static final int REPLY_COMMENT_LIMIT = 1000;
    private static final long HOT_LIKE_WEIGHT = 4L;
    private static final long HOT_REPLY_WEIGHT = 6L;
    private static final int LEVEL_ROOT = 1;
    private static final int LEVEL_REPLY = 2;
    private static final String CONSUME_SUCCESS = "SUCCESS";
    private static final String CONSUME_PROCESSING = "PROCESSING";

    private final ContentCommentMapper contentCommentMapper;
    private final UserCommentMapper userCommentMapper;
    private final CommentContentMapper commentContentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final CommentIdempotentRequestMapper idempotentRequestMapper;
    private final CommentOperationLogMapper operationLogMapper;
    private final CommentOutboxEventMapper outboxEventMapper;
    private final CommentConsumeRecordMapper consumeRecordMapper;
    private final MemberInternalClient memberInternalClient;
    private final NoteApplicationService noteApplicationService;
    private final CommentRedisStore commentRedisStore;
    private final ContentIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;

    public CommentApplicationService(
            ContentCommentMapper contentCommentMapper,
            UserCommentMapper userCommentMapper,
            CommentContentMapper commentContentMapper,
            CommentLikeMapper commentLikeMapper,
            CommentIdempotentRequestMapper idempotentRequestMapper,
            CommentOperationLogMapper operationLogMapper,
            CommentOutboxEventMapper outboxEventMapper,
            CommentConsumeRecordMapper consumeRecordMapper,
            MemberInternalClient memberInternalClient,
            NoteApplicationService noteApplicationService,
            CommentRedisStore commentRedisStore,
            ContentIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper
    ) {
        this.contentCommentMapper = contentCommentMapper;
        this.userCommentMapper = userCommentMapper;
        this.commentContentMapper = commentContentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.idempotentRequestMapper = idempotentRequestMapper;
        this.operationLogMapper = operationLogMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.memberInternalClient = memberInternalClient;
        this.noteApplicationService = noteApplicationService;
        this.commentRedisStore = commentRedisStore;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateCommentResponse createRootComment(
            String currentUserId,
            String noteId,
            String idempotencyKey,
            CreateCommentRequest request
    ) {
        Long userId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        String key = resolveIdempotencyKey(idempotencyKey, request.clientRequestId());
        return executeIdempotently(
                userId,
                OP_CREATE_COMMENT,
                key,
                Map.of("noteId", noteId, "request", request),
                CreateCommentResponse.class,
                () -> {
                    memberInternalClient.ensureUserAllowed(currentUserId);
                    CommentCheckResponse check = commentCheck(parsedNoteId, currentUserId);
                    String content = normalizeContent(request.content());
                    NoteSnapshot noteSnapshot = noteSnapshot(parsedNoteId, currentUserId);
                    LocalDateTime now = now();
                    ContentCommentEntity comment = insertComment(
                            parsedNoteId,
                            parseId(check.authorId(), ApiErrorCode.NOTE_NOT_FOUND),
                            userId,
                            null,
                            null,
                            null,
                            LEVEL_ROOT,
                            content,
                            noteSnapshot,
                            now
                    );
                    insertOperationLog(comment.getCommentId(), userId, "CREATE", null, STATUS_VISIBLE, now);
                    insertCommentCreatedOutbox(comment, contentPreview(content), now);
                    CreateCommentResponse response = createResponse(comment, now);
                    return new IdempotentResult<>(comment.getCommentId(), response);
                }
        );
    }

    @Transactional
    public CreateCommentResponse replyComment(
            String currentUserId,
            String parentCommentId,
            String idempotencyKey,
            CreateCommentRequest request
    ) {
        Long userId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedParentId = parseId(parentCommentId, ApiErrorCode.COMMENT_NOT_FOUND);
        String key = resolveIdempotencyKey(idempotencyKey, request.clientRequestId());
        return executeIdempotently(
                userId,
                OP_REPLY_COMMENT,
                key,
                Map.of("parentCommentId", parentCommentId, "request", request),
                CreateCommentResponse.class,
                () -> {
                    memberInternalClient.ensureUserAllowed(currentUserId);
                    ContentCommentEntity parent = requireVisibleComment(parsedParentId);
                    commentCheck(parent.getNoteId(), currentUserId);
                    String content = normalizeContent(request.content());
                    NoteSnapshot noteSnapshot = noteSnapshot(parent.getNoteId(), currentUserId);
                    LocalDateTime now = now();
                    Long rootId = LEVEL_ROOT == parent.getLevel() ? parent.getCommentId() : parent.getRootId();
                    ContentCommentEntity comment = insertComment(
                            parent.getNoteId(),
                            parent.getNoteAuthorId(),
                            userId,
                            rootId,
                            parent.getCommentId(),
                            parent.getUserId(),
                            LEVEL_REPLY,
                            content,
                            noteSnapshot,
                            now
                    );
                    contentCommentMapper.incrementReplySnapshot(rootId, 1, HOT_REPLY_WEIGHT, now);
                    insertOperationLog(comment.getCommentId(), userId, "REPLY", null, STATUS_VISIBLE, now);
                    insertCommentCreatedOutbox(comment, contentPreview(content), now);
                    CreateCommentResponse response = createResponse(comment, now);
                    return new IdempotentResult<>(comment.getCommentId(), response);
                }
        );
    }

    @Transactional
    public DeleteCommentResponse deleteComment(String currentUserId, String commentId) {
        Long userId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedCommentId = parseId(commentId, ApiErrorCode.COMMENT_NOT_FOUND);
        ContentCommentEntity comment = requireComment(parsedCommentId);
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException(ApiErrorCode.COMMENT_AUTHOR_FORBIDDEN);
        }
        if (!STATUS_VISIBLE.equals(comment.getCommentStatus())) {
            return new DeleteCommentResponse(commentId, STATUS_DELETED, toOffsetString(comment.getUpdatedAt()));
        }

        LocalDateTime now = now();
        long affectedCommentCount;
        long affectedReplyCount;
        if (LEVEL_ROOT == comment.getLevel()) {
            affectedCommentCount = contentCommentMapper.countVisibleByRoot(comment.getRootId());
            affectedReplyCount = Math.max(affectedCommentCount - 1, 0);
            contentCommentMapper.markDeletedByRoot(comment.getRootId(), now);
            userCommentMapper.markDeletedByRoot(comment.getRootId(), now);
        } else {
            affectedCommentCount = 1;
            affectedReplyCount = 1;
            contentCommentMapper.markDeleted(comment.getCommentId(), now);
            userCommentMapper.markDeleted(comment.getCommentId(), now);
            contentCommentMapper.incrementReplySnapshot(comment.getRootId(), -1, -HOT_REPLY_WEIGHT, now);
        }
        insertOperationLog(comment.getCommentId(), userId, "DELETE", STATUS_VISIBLE, STATUS_DELETED, now);
        insertCommentDeletedOutbox(comment, affectedCommentCount, affectedReplyCount, now);
        return new DeleteCommentResponse(commentId, STATUS_DELETED, toOffsetString(now));
    }

    @Transactional(readOnly = true)
    public CommentCursorPage<CommentItemResponse> noteComments(
            String noteId,
            String sort,
            String cursor,
            Integer size,
            String viewerId
    ) {
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        String normalizedSort = normalizeSort(sort);
        ensureNoteVisible(parsedNoteId, viewerId);
        PageCursor pageCursor = parseCursor(cursor, normalizedSort);
        int pageSize = normalizePageSize(size);
        List<ContentCommentEntity> comments = SORT_HOT.equals(normalizedSort)
                ? hotRootComments(parsedNoteId, pageCursor, pageSize + 1)
                : timeRootComments(parsedNoteId, normalizedSort, pageCursor, pageSize + 1);
        return toCommentPage(comments, pageSize, normalizedSort, viewerId);
    }

    @Transactional(readOnly = true)
    public CommentCursorPage<CommentItemResponse> replies(
            String rootCommentId,
            String cursor,
            Integer size,
            String viewerId
    ) {
        Long parsedRootId = parseId(rootCommentId, ApiErrorCode.COMMENT_NOT_FOUND);
        ContentCommentEntity root = requireVisibleComment(parsedRootId);
        if (LEVEL_ROOT != root.getLevel()) {
            throw new BusinessException(ApiErrorCode.COMMENT_REPLY_TARGET_INVALID);
        }
        ensureNoteVisible(root.getNoteId(), viewerId);
        PageCursor pageCursor = parseCursor(cursor, SORT_TIME_ASC);
        int pageSize = normalizePageSize(size);
        List<ContentCommentEntity> replies = replyComments(parsedRootId, pageCursor, pageSize + 1);
        return toCommentPage(replies, pageSize, SORT_TIME_ASC, viewerId);
    }

    @Transactional
    public CommentLikeResponse like(String currentUserId, String commentId) {
        Long userId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedCommentId = parseId(commentId, ApiErrorCode.COMMENT_NOT_FOUND);
        memberInternalClient.ensureUserAllowed(currentUserId);
        ContentCommentEntity comment = requireVisibleComment(parsedCommentId);
        CommentLikeEntity existing = commentLikeMapper.selectByPair(parsedCommentId, userId);
        if (existing != null && LIKE_ACTIVE.equals(existing.getLikeStatus())) {
            return new CommentLikeResponse(commentId, true);
        }
        LocalDateTime now = now();
        boolean changed = false;
        if (existing == null) {
            CommentLikeEntity entity = new CommentLikeEntity();
            entity.setId(idGenerator.nextId());
            entity.setCommentId(parsedCommentId);
            entity.setCommentUserId(comment.getUserId());
            entity.setUserId(userId);
            entity.setLikeStatus(LIKE_ACTIVE);
            entity.setLikedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            try {
                commentLikeMapper.insert(entity);
                changed = true;
            } catch (DuplicateKeyException exception) {
                existing = commentLikeMapper.selectByPair(parsedCommentId, userId);
            }
        }
        if (!changed && existing != null && LIKE_CANCELED.equals(existing.getLikeStatus())) {
            changed = commentLikeMapper.activate(existing.getId(), now, now) > 0;
        }
        if (changed) {
            contentCommentMapper.incrementLikeSnapshot(parsedCommentId, 1, hotLikeDelta(comment, 1), now);
            insertCommentLikeOutbox("CommentLiked", comment, userId, now);
        }
        return new CommentLikeResponse(commentId, true);
    }

    @Transactional
    public CommentLikeResponse unlike(String currentUserId, String commentId) {
        Long userId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedCommentId = parseId(commentId, ApiErrorCode.COMMENT_NOT_FOUND);
        ContentCommentEntity comment = requireVisibleComment(parsedCommentId);
        CommentLikeEntity existing = commentLikeMapper.selectByPair(parsedCommentId, userId);
        if (existing == null || LIKE_CANCELED.equals(existing.getLikeStatus())) {
            return new CommentLikeResponse(commentId, false);
        }
        LocalDateTime now = now();
        boolean changed = commentLikeMapper.cancel(existing.getId(), now, now) > 0;
        if (changed) {
            contentCommentMapper.incrementLikeSnapshot(parsedCommentId, -1, hotLikeDelta(comment, -1), now);
            insertCommentLikeOutbox("CommentUnliked", comment, userId, now);
        }
        return new CommentLikeResponse(commentId, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<MyCommentItemResponse> myComments(String currentUserId, String cursor, Integer size) {
        Long userId = parseId(currentUserId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        PageCursor pageCursor = parseCursor(cursor, SORT_TIME_DESC);
        int pageSize = normalizePageSize(size);
        List<UserCommentEntity> comments = userCommentMapper.selectByUserPage(
                userId,
                pageCursor.sortAt(),
                pageCursor.commentId(),
                pageSize + 1
        );
        boolean hasMore = comments.size() > pageSize;
        List<UserCommentEntity> pageItems = hasMore ? comments.subList(0, pageSize) : comments;
        List<MyCommentItemResponse> items = pageItems.stream()
                .map(item -> new MyCommentItemResponse(
                        String.valueOf(item.getCommentId()),
                        String.valueOf(item.getNoteId()),
                        item.getContentPreview(),
                        item.getCommentStatus(),
                        toOffsetString(item.getCreatedAt()),
                        new MyCommentNoteResponse(
                                String.valueOf(item.getNoteId()),
                                item.getNoteTitleSnapshot(),
                                item.getNoteCoverUrlSnapshot()
                        )
                ))
                .toList();
        return new CursorPage<>(items, plainCursor(pageItems, hasMore), hasMore);
    }

    @Transactional(readOnly = true)
    public BatchCommentSummaryResponse batchSummary(BatchCommentSummaryRequest request) {
        List<Long> commentIds = request.commentIds().stream()
                .map(commentId -> parseId(commentId, ApiErrorCode.PARAM_INVALID))
                .distinct()
                .toList();
        if (commentIds.isEmpty()) {
            return new BatchCommentSummaryResponse(List.of());
        }
        Map<Long, ContentCommentEntity> comments = contentCommentMapper.selectByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(ContentCommentEntity::getCommentId, item -> item));
        Map<Long, CommentContentEntity> contents = commentContentMapper.selectByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(CommentContentEntity::getCommentId, item -> item));
        List<CommentSummaryItem> summaries = request.commentIds().stream()
                .map(commentId -> parseId(commentId, ApiErrorCode.PARAM_INVALID))
                .map(parsedCommentId -> {
                    ContentCommentEntity comment = comments.get(parsedCommentId);
                    if (comment == null) {
                        return null;
                    }
                    CommentContentEntity content = contents.get(parsedCommentId);
                    return new CommentSummaryItem(
                            String.valueOf(comment.getCommentId()),
                            String.valueOf(comment.getNoteId()),
                            String.valueOf(comment.getUserId()),
                            String.valueOf(comment.getRootId()),
                            content == null ? "" : content.getContentPreview(),
                            comment.getCommentStatus(),
                            toOffsetString(comment.getCreatedAt())
                    );
                })
                .filter(item -> item != null)
                .toList();
        return new BatchCommentSummaryResponse(summaries);
    }

    @Transactional(readOnly = true)
    public CommentCounterSourceResponse counterSource(CommentCounterSourceRequest request) {
        List<CommentCounterSourceItem> items = request.targets().stream()
                .map(target -> {
                    Long targetId = parseId(target.targetId(), ApiErrorCode.PARAM_INVALID);
                    Map<String, Long> counts = new LinkedHashMap<>();
                    if ("NOTE".equals(target.targetType())) {
                        for (String field : target.fields()) {
                            if ("comment_count".equals(field)) {
                                counts.put(field, contentCommentMapper.countVisibleByNote(targetId));
                            }
                        }
                    } else if ("COMMENT".equals(target.targetType())) {
                        for (String field : target.fields()) {
                            if ("like_count".equals(field)) {
                                counts.put(field, commentLikeMapper.countActiveByComment(targetId));
                            } else if ("reply_count".equals(field)) {
                                counts.put(field, contentCommentMapper.countVisibleReplies(targetId));
                            }
                        }
                    } else {
                        throw new BusinessException(ApiErrorCode.PARAM_INVALID);
                    }
                    return new CommentCounterSourceItem(target.targetType(), target.targetId(), counts);
                })
                .toList();
        return new CommentCounterSourceResponse(items);
    }

    @Transactional(readOnly = true)
    public CommentHotRebuildResponse rebuildNoteCommentCaches(String noteId) {
        Long parsedNoteId = parseId(noteId, ApiErrorCode.PARAM_INVALID);
        int hotCount = rebuildHotComments(parsedNoteId);
        int timeCount = rebuildRootTimeComments(parsedNoteId);
        return new CommentHotRebuildResponse(noteId, hotCount, timeCount, toOffsetString(now()));
    }

    @Transactional
    public void consumeEvent(CommentConsumeEventRequest request) {
        LocalDateTime now = now();
        CommentConsumeRecordEntity existing = consumeRecordMapper.selectByGroupAndEvent(
                request.consumerGroup(),
                request.eventId()
        );
        if (existing != null && CONSUME_SUCCESS.equals(existing.getConsumeStatus())) {
            return;
        }

        reserveConsumeRecord(request, now);
        try {
            switch (request.eventType()) {
                case "CommentCreated" -> consumeCommentCreated(request);
                case "CommentDeleted" -> consumeCommentDeleted(request);
                case "CommentLiked", "CommentUnliked", "CommentStatusChanged" -> refreshRootCaches(
                        id(request.payload(), "noteId"),
                        id(request.payload(), "rootId")
                );
                default -> {
                }
            }
            consumeRecordMapper.markSuccess(request.consumerGroup(), request.eventId());
        } catch (RuntimeException exception) {
            consumeRecordMapper.markFail(
                    request.consumerGroup(),
                    request.eventId(),
                    truncate(exception.getMessage(), 512)
            );
            throw exception;
        }
    }

    private ContentCommentEntity insertComment(
            Long noteId,
            Long noteAuthorId,
            Long userId,
            Long rootId,
            Long parentCommentId,
            Long replyToUserId,
            int level,
            String content,
            NoteSnapshot noteSnapshot,
            LocalDateTime now
    ) {
        Long commentId = idGenerator.nextId();
        ContentCommentEntity comment = new ContentCommentEntity();
        comment.setCommentId(commentId);
        comment.setNoteId(noteId);
        comment.setNoteAuthorId(noteAuthorId);
        comment.setUserId(userId);
        comment.setRootId(rootId == null ? commentId : rootId);
        comment.setParentCommentId(parentCommentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setLevel(level);
        comment.setCommentStatus(STATUS_VISIBLE);
        comment.setLikeCountSnapshot(0L);
        comment.setReplyCountSnapshot(0L);
        comment.setHotScoreSnapshot(0L);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        contentCommentMapper.insert(comment);

        CommentContentEntity contentEntity = new CommentContentEntity();
        contentEntity.setCommentId(commentId);
        contentEntity.setContent(content);
        contentEntity.setContentPreview(contentPreview(content));
        contentEntity.setAuditStatus("SKIPPED");
        contentEntity.setCreatedAt(now);
        contentEntity.setUpdatedAt(now);
        commentContentMapper.insert(contentEntity);

        UserCommentEntity userComment = new UserCommentEntity();
        userComment.setId(idGenerator.nextId());
        userComment.setCommentId(commentId);
        userComment.setUserId(userId);
        userComment.setNoteId(noteId);
        userComment.setRootId(comment.getRootId());
        userComment.setParentCommentId(parentCommentId);
        userComment.setCommentStatus(STATUS_VISIBLE);
        userComment.setContentPreview(contentEntity.getContentPreview());
        userComment.setNoteTitleSnapshot(noteSnapshot.title());
        userComment.setNoteCoverUrlSnapshot(noteSnapshot.coverUrl());
        userComment.setCreatedAt(now);
        userComment.setUpdatedAt(now);
        userCommentMapper.insert(userComment);
        return comment;
    }

    private CommentCursorPage<CommentItemResponse> toCommentPage(
            List<ContentCommentEntity> comments,
            int pageSize,
            String sort,
            String viewerId
    ) {
        boolean hasMore = comments.size() > pageSize;
        List<ContentCommentEntity> pageItems = hasMore ? comments.subList(0, pageSize) : comments;
        List<Long> commentIds = pageItems.stream().map(ContentCommentEntity::getCommentId).toList();
        Map<Long, CommentContentEntity> contents = commentContentMap(commentIds);
        PageAuthors authors = pageAuthors(pageItems);
        Set<Long> likedCommentIds = viewerLikedCommentIds(viewerId, commentIds);
        boolean degraded = authors.degraded();

        List<CommentItemResponse> items = pageItems.stream()
                .map(comment -> {
                    CommentContentEntity content = contents.get(comment.getCommentId());
                    boolean itemDegraded = degraded || content == null;
                    CommentAuthorResponse author = authors.author(comment.getUserId());
                    CommentAuthorResponse replyToUser = comment.getReplyToUserId() == null
                            ? null
                            : authors.author(comment.getReplyToUserId());
                    return new CommentItemResponse(
                            String.valueOf(comment.getCommentId()),
                            String.valueOf(comment.getNoteId()),
                            String.valueOf(comment.getRootId()),
                            stringValue(comment.getParentCommentId()),
                            replyToUser,
                            author,
                            content == null ? "" : content.getContent(),
                            comment.getLevel(),
                            comment.getCommentStatus(),
                            safeLong(comment.getLikeCountSnapshot()),
                            LEVEL_ROOT == comment.getLevel() ? safeLong(comment.getReplyCountSnapshot()) : 0L,
                            new CommentViewerActionResponse(likedCommentIds.contains(comment.getCommentId())),
                            toOffsetString(comment.getCreatedAt()),
                            itemDegraded
                    );
                })
                .toList();
        return new CommentCursorPage<>(items, pageCursor(pageItems, hasMore, sort), hasMore, degraded);
    }

    private List<ContentCommentEntity> hotRootComments(Long noteId, PageCursor cursor, int limit) {
        List<ContentCommentEntity> hotComments = List.of();
        try {
            hotComments = hotRootCommentsFromRedis(noteId, cursor, limit);
            if (hotComments.size() >= limit) {
                return hotComments;
            }
        } catch (RuntimeException exception) {
            // Redis only accelerates hot comments. MySQL remains the source of truth.
        }

        if (hotComments.isEmpty()) {
            List<ContentCommentEntity> comments = contentCommentMapper.selectRootPage(
                    noteId,
                    SORT_HOT,
                    cursor.hotScore(),
                    cursor.sortAt(),
                    cursor.commentId(),
                    limit
            );
            rebuildHotComments(noteId);
            rebuildRootTimeComments(noteId);
            return comments;
        }

        List<ContentCommentEntity> fillers = timeRootCommentsFromMysql(
                noteId,
                SORT_TIME_DESC,
                cursor.sortAt(),
                cursor.commentId(),
                limit + hotComments.size() + 1
        );
        List<ContentCommentEntity> merged = mergeUnique(hotComments, fillers, limit);
        rebuildHotComments(noteId);
        rebuildRootTimeComments(noteId);
        return merged;
    }

    private List<ContentCommentEntity> hotRootCommentsFromRedis(Long noteId, PageCursor cursor, int limit) {
        int fetchLimit = Math.max(limit * 3, limit + 20);
        List<HotCommentMember> members = commentRedisStore.hotComments(noteId, fetchLimit);
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> commentIds = members.stream().map(HotCommentMember::commentId).toList();
        Map<Long, ContentCommentEntity> comments = visibleRootMap(commentIds, noteId);
        List<ContentCommentEntity> sorted = members.stream()
                .map(member -> comments.get(member.commentId()))
                .filter(comment -> comment != null && afterHotCursor(comment, cursor))
                .sorted(hotCommentComparator())
                .limit(limit)
                .toList();
        if (sorted.size() < Math.min(limit, members.size())) {
            rebuildHotComments(noteId);
        }
        return sorted;
    }

    private List<ContentCommentEntity> timeRootComments(Long noteId, String sort, PageCursor cursor, int limit) {
        if (SORT_TIME_DESC.equals(sort)) {
            try {
                List<ContentCommentEntity> cached = timeRootCommentsFromRedis(noteId, sort, cursor, limit);
                if (cached.size() >= limit) {
                    return cached;
                }
            } catch (RuntimeException exception) {
                // Redis is a read-through acceleration path only.
            }
        }
        List<ContentCommentEntity> comments = timeRootCommentsFromMysql(
                noteId,
                sort,
                cursor.sortAt(),
                cursor.commentId(),
                limit
        );
        rebuildRootTimeComments(noteId);
        return comments;
    }

    private List<ContentCommentEntity> timeRootCommentsFromRedis(
            Long noteId,
            String sort,
            PageCursor cursor,
            int limit
    ) {
        int fetchLimit = Math.max(limit * 3, limit + 20);
        boolean descending = !SORT_TIME_ASC.equals(sort);
        List<Long> commentIds = commentRedisStore.rootTimeComments(noteId, descending, fetchLimit);
        if (commentIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ContentCommentEntity> comments = visibleRootMap(commentIds, noteId);
        List<ContentCommentEntity> sorted = commentIds.stream()
                .map(comments::get)
                .filter(comment -> comment != null && afterTimeCursor(comment, cursor, sort))
                .sorted(timeCommentComparator(sort))
                .limit(limit)
                .toList();
        if (sorted.size() < Math.min(limit, commentIds.size())) {
            rebuildRootTimeComments(noteId);
        }
        return sorted;
    }

    private List<ContentCommentEntity> timeRootCommentsFromMysql(
            Long noteId,
            String sort,
            LocalDateTime cursorCreatedAt,
            Long cursorCommentId,
            int limit
    ) {
        return contentCommentMapper.selectRootPage(
                noteId,
                sort,
                null,
                cursorCreatedAt,
                cursorCommentId,
                limit
        );
    }

    private List<ContentCommentEntity> replyComments(Long rootId, PageCursor cursor, int limit) {
        try {
            List<ContentCommentEntity> cached = replyCommentsFromRedis(rootId, cursor, limit);
            if (cached.size() >= limit) {
                return cached;
            }
        } catch (RuntimeException exception) {
            // Redis is a read-through acceleration path only.
        }
        List<ContentCommentEntity> replies = contentCommentMapper.selectReplyPage(
                rootId,
                cursor.sortAt(),
                cursor.commentId(),
                limit
        );
        rebuildReplyComments(rootId);
        return replies;
    }

    private List<ContentCommentEntity> replyCommentsFromRedis(Long rootId, PageCursor cursor, int limit) {
        int fetchLimit = Math.max(limit * 3, limit + 20);
        List<Long> commentIds = commentRedisStore.replyComments(rootId, fetchLimit);
        if (commentIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ContentCommentEntity> comments = visibleCommentMap(commentIds);
        List<ContentCommentEntity> sorted = commentIds.stream()
                .map(comments::get)
                .filter(comment -> comment != null
                        && rootId.equals(comment.getRootId())
                        && LEVEL_REPLY == safeInt(comment.getLevel())
                        && STATUS_VISIBLE.equals(comment.getCommentStatus())
                        && afterTimeCursor(comment, cursor, SORT_TIME_ASC))
                .sorted(timeCommentComparator(SORT_TIME_ASC))
                .limit(limit)
                .toList();
        if (sorted.size() < Math.min(limit, commentIds.size())) {
            rebuildReplyComments(rootId);
        }
        return sorted;
    }

    private Comparator<ContentCommentEntity> hotCommentComparator() {
        return Comparator.comparingLong(this::hotScore).reversed()
                .thenComparing(ContentCommentEntity::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(ContentCommentEntity::getCommentId, Comparator.reverseOrder());
    }

    private boolean afterHotCursor(ContentCommentEntity comment, PageCursor cursor) {
        if (cursor.hotScore() == null || cursor.sortAt() == null || cursor.commentId() == null) {
            return true;
        }
        long score = hotScore(comment);
        int scoreCompare = Long.compare(score, cursor.hotScore());
        if (scoreCompare != 0) {
            return scoreCompare < 0;
        }
        int timeCompare = comment.getCreatedAt().compareTo(cursor.sortAt());
        if (timeCompare != 0) {
            return timeCompare < 0;
        }
        return comment.getCommentId() < cursor.commentId();
    }

    private boolean afterTimeCursor(ContentCommentEntity comment, PageCursor cursor, String sort) {
        if (cursor.sortAt() == null || cursor.commentId() == null) {
            return true;
        }
        int timeCompare = comment.getCreatedAt().compareTo(cursor.sortAt());
        if (SORT_TIME_ASC.equals(sort)) {
            return timeCompare > 0 || (timeCompare == 0 && comment.getCommentId() > cursor.commentId());
        }
        return timeCompare < 0 || (timeCompare == 0 && comment.getCommentId() < cursor.commentId());
    }

    private Comparator<ContentCommentEntity> timeCommentComparator(String sort) {
        Comparator<ContentCommentEntity> comparator = Comparator.comparing(ContentCommentEntity::getCreatedAt)
                .thenComparing(ContentCommentEntity::getCommentId);
        return SORT_TIME_ASC.equals(sort) ? comparator : comparator.reversed();
    }

    private List<ContentCommentEntity> mergeUnique(
            List<ContentCommentEntity> preferred,
            List<ContentCommentEntity> fillers,
            int limit
    ) {
        List<ContentCommentEntity> merged = new ArrayList<>(Math.min(limit, preferred.size() + fillers.size()));
        Set<Long> seen = new LinkedHashSet<>();
        for (ContentCommentEntity comment : preferred) {
            if (seen.add(comment.getCommentId())) {
                merged.add(comment);
            }
            if (merged.size() >= limit) {
                return merged;
            }
        }
        for (ContentCommentEntity comment : fillers) {
            if (seen.add(comment.getCommentId())) {
                merged.add(comment);
            }
            if (merged.size() >= limit) {
                break;
            }
        }
        return merged;
    }

    private Map<Long, ContentCommentEntity> visibleRootMap(List<Long> commentIds, Long noteId) {
        List<Long> ids = distinctIds(commentIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return contentCommentMapper.selectVisibleRootsByCommentIds(ids).stream()
                .filter(comment -> noteId.equals(comment.getNoteId()))
                .collect(Collectors.toMap(ContentCommentEntity::getCommentId, item -> item, (left, right) -> left));
    }

    private Map<Long, ContentCommentEntity> visibleCommentMap(List<Long> commentIds) {
        List<Long> ids = distinctIds(commentIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return contentCommentMapper.selectByCommentIds(ids).stream()
                .filter(comment -> STATUS_VISIBLE.equals(comment.getCommentStatus()))
                .collect(Collectors.toMap(ContentCommentEntity::getCommentId, item -> item, (left, right) -> left));
    }

    private List<Long> distinctIds(List<Long> ids) {
        return ids.stream().distinct().toList();
    }

    private int rebuildHotComments(Long noteId) {
        try {
            List<HotCommentMember> members = contentCommentMapper
                    .selectHotRootCandidates(noteId, HOT_COMMENT_LIMIT)
                    .stream()
                    .map(comment -> new HotCommentMember(comment.getCommentId(), (double) hotScore(comment)))
                    .toList();
            commentRedisStore.replaceHotComments(noteId, members, HOT_COMMENT_LIMIT);
            return members.size();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private int rebuildRootTimeComments(Long noteId) {
        try {
            List<CommentTimeMember> members = contentCommentMapper
                    .selectRootPage(noteId, SORT_TIME_DESC, null, null, null, TIME_COMMENT_LIMIT)
                    .stream()
                    .map(comment -> new CommentTimeMember(comment.getCommentId(), comment.getCreatedAt()))
                    .toList();
            commentRedisStore.replaceRootTimeComments(noteId, members);
            return members.size();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private int rebuildReplyComments(Long rootId) {
        try {
            List<CommentTimeMember> members = contentCommentMapper
                    .selectReplyPage(rootId, null, null, REPLY_COMMENT_LIMIT)
                    .stream()
                    .map(comment -> new CommentTimeMember(comment.getCommentId(), comment.getCreatedAt()))
                    .toList();
            commentRedisStore.replaceReplyComments(rootId, members);
            return members.size();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private void refreshRootCaches(Long noteId, Long rootId) {
        ContentCommentEntity root = contentCommentMapper.selectByCommentId(rootId);
        if (root == null || LEVEL_ROOT != safeInt(root.getLevel()) || !STATUS_VISIBLE.equals(root.getCommentStatus())) {
            commentRedisStore.removeHotComment(noteId, rootId);
            commentRedisStore.removeRootTimeComment(noteId, rootId);
            commentRedisStore.removeReplyList(rootId);
            return;
        }
        commentRedisStore.updateHotComment(root.getNoteId(), root.getCommentId(), hotScore(root), HOT_COMMENT_LIMIT);
        commentRedisStore.updateRootTimeComment(root.getNoteId(), root.getCommentId(), root.getCreatedAt());
    }

    private long hotScore(ContentCommentEntity comment) {
        return safeLong(comment.getHotScoreSnapshot());
    }

    private long hotLikeDelta(ContentCommentEntity comment, long likeDelta) {
        return LEVEL_ROOT == safeInt(comment.getLevel()) ? likeDelta * HOT_LIKE_WEIGHT : 0L;
    }

    private void consumeCommentCreated(CommentConsumeEventRequest request) {
        Map<String, Object> payload = request.payload();
        Long noteId = id(payload, "noteId");
        Long commentId = id(payload, "commentId");
        Long rootId = id(payload, "rootId");
        ContentCommentEntity comment = contentCommentMapper.selectByCommentId(commentId);
        if (comment == null || !STATUS_VISIBLE.equals(comment.getCommentStatus())) {
            refreshRootCaches(noteId, rootId);
            return;
        }
        if (LEVEL_ROOT == safeInt(comment.getLevel())) {
            refreshRootCaches(comment.getNoteId(), comment.getRootId());
        } else {
            commentRedisStore.updateReplyComment(comment.getRootId(), comment.getCommentId(), comment.getCreatedAt());
            refreshRootCaches(comment.getNoteId(), comment.getRootId());
        }
    }

    private void consumeCommentDeleted(CommentConsumeEventRequest request) {
        Map<String, Object> payload = request.payload();
        Long noteId = id(payload, "noteId");
        Long commentId = id(payload, "commentId");
        Long rootId = id(payload, "rootId");
        int level = intValue(payload.get("level"));
        if (LEVEL_ROOT == level) {
            commentRedisStore.removeHotComment(noteId, rootId);
            commentRedisStore.removeRootTimeComment(noteId, rootId);
            commentRedisStore.removeReplyList(rootId);
            return;
        }
        commentRedisStore.removeReplyComment(rootId, commentId);
        refreshRootCaches(noteId, rootId);
    }

    private void reserveConsumeRecord(CommentConsumeEventRequest request, LocalDateTime now) {
        CommentConsumeRecordEntity entity = new CommentConsumeRecordEntity();
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

    private Map<Long, CommentContentEntity> commentContentMap(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentContentMapper.selectByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(CommentContentEntity::getCommentId, item -> item));
    }

    private PageAuthors pageAuthors(List<ContentCommentEntity> comments) {
        LinkedHashSet<String> userIds = new LinkedHashSet<>();
        for (ContentCommentEntity comment : comments) {
            userIds.add(String.valueOf(comment.getUserId()));
            if (comment.getReplyToUserId() != null) {
                userIds.add(String.valueOf(comment.getReplyToUserId()));
            }
        }
        try {
            Map<String, CommentAuthorResponse> summaries = memberInternalClient.batchSummary(List.copyOf(userIds));
            return new PageAuthors(summaries, summaries.size() < userIds.size());
        } catch (RuntimeException exception) {
            return new PageAuthors(Map.of(), true);
        }
    }

    private Set<Long> viewerLikedCommentIds(String viewerId, List<Long> commentIds) {
        if (viewerId == null || viewerId.isBlank() || commentIds.isEmpty()) {
            return Set.of();
        }
        Long userId = parseId(viewerId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        try {
            return commentLikeMapper.selectActiveByComments(userId, commentIds).stream()
                    .map(CommentLikeEntity::getCommentId)
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            return Set.of();
        }
    }

    private CommentCheckResponse commentCheck(Long noteId, String viewerId) {
        CommentCheckResponse check = noteApplicationService.commentCheck(
                new CommentCheckRequest(String.valueOf(noteId), viewerId)
        );
        if (!check.exists() || !check.commentAllowed()) {
            throw new BusinessException(ApiErrorCode.COMMENT_NOTE_NOT_ALLOWED);
        }
        return check;
    }

    private void ensureNoteVisible(Long noteId, String viewerId) {
        CommentCheckResponse check = noteApplicationService.commentCheck(
                new CommentCheckRequest(String.valueOf(noteId), viewerId)
        );
        if (!check.exists()) {
            throw new BusinessException(ApiErrorCode.NOTE_NOT_FOUND);
        }
    }

    private NoteSnapshot noteSnapshot(Long noteId, String viewerId) {
        try {
            List<NoteSummaryItem> notes = noteApplicationService.batchSummary(
                    new BatchNoteSummaryRequest(List.of(String.valueOf(noteId)), viewerId, true)
            ).notes();
            if (notes.isEmpty()) {
                return new NoteSnapshot(null, null);
            }
            NoteSummaryItem note = notes.get(0);
            return new NoteSnapshot(note.title(), note.coverUrl());
        } catch (RuntimeException exception) {
            return new NoteSnapshot(null, null);
        }
    }

    private ContentCommentEntity requireComment(Long commentId) {
        ContentCommentEntity comment = contentCommentMapper.selectByCommentId(commentId);
        if (comment == null) {
            throw new BusinessException(ApiErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private ContentCommentEntity requireVisibleComment(Long commentId) {
        ContentCommentEntity comment = requireComment(commentId);
        if (!STATUS_VISIBLE.equals(comment.getCommentStatus())) {
            throw new BusinessException(ApiErrorCode.COMMENT_STATUS_INVALID);
        }
        return comment;
    }

    private CreateCommentResponse createResponse(ContentCommentEntity comment, LocalDateTime createdAt) {
        return new CreateCommentResponse(
                String.valueOf(comment.getCommentId()),
                String.valueOf(comment.getNoteId()),
                String.valueOf(comment.getRootId()),
                stringValue(comment.getParentCommentId()),
                comment.getLevel(),
                comment.getCommentStatus(),
                toOffsetString(createdAt)
        );
    }

    private void insertOperationLog(
            Long commentId,
            Long userId,
            String operationType,
            String beforeStatus,
            String afterStatus,
            LocalDateTime now
    ) {
        CommentOperationLogEntity entity = new CommentOperationLogEntity();
        entity.setId(idGenerator.nextId());
        entity.setCommentId(commentId);
        entity.setUserId(userId);
        entity.setOperationType(operationType);
        entity.setBeforeStatus(beforeStatus);
        entity.setAfterStatus(afterStatus);
        entity.setTraceId(TraceIdHolder.currentOrNew());
        entity.setCreatedAt(now);
        operationLogMapper.insert(entity);
    }

    private void insertCommentCreatedOutbox(ContentCommentEntity comment, String contentPreview, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", String.valueOf(comment.getCommentId()));
        payload.put("noteId", String.valueOf(comment.getNoteId()));
        payload.put("noteAuthorId", String.valueOf(comment.getNoteAuthorId()));
        payload.put("userId", String.valueOf(comment.getUserId()));
        payload.put("rootId", String.valueOf(comment.getRootId()));
        payload.put("parentCommentId", stringValue(comment.getParentCommentId()));
        payload.put("replyToUserId", stringValue(comment.getReplyToUserId()));
        payload.put("level", comment.getLevel());
        payload.put("contentPreview", contentPreview);
        payload.put("commentStatus", comment.getCommentStatus());
        payload.put("createdAt", toOffsetString(now));
        insertOutbox("CommentCreated", comment.getCommentId(), String.valueOf(comment.getCommentId()), payload, now);
    }

    private void insertCommentDeletedOutbox(
            ContentCommentEntity comment,
            long affectedCommentCount,
            long affectedReplyCount,
            LocalDateTime now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", String.valueOf(comment.getCommentId()));
        payload.put("noteId", String.valueOf(comment.getNoteId()));
        payload.put("userId", String.valueOf(comment.getUserId()));
        payload.put("rootId", String.valueOf(comment.getRootId()));
        payload.put("level", comment.getLevel());
        payload.put("affectedCommentCount", affectedCommentCount);
        payload.put("affectedReplyCount", affectedReplyCount);
        payload.put("deletedAt", toOffsetString(now));
        insertOutbox("CommentDeleted", comment.getCommentId(), String.valueOf(comment.getCommentId()), payload, now);
    }

    private void insertCommentLikeOutbox(String eventType, ContentCommentEntity comment, Long userId, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", String.valueOf(comment.getCommentId()));
        payload.put("noteId", String.valueOf(comment.getNoteId()));
        payload.put("commentUserId", String.valueOf(comment.getUserId()));
        payload.put("userId", String.valueOf(userId));
        payload.put("rootId", String.valueOf(comment.getRootId()));
        payload.put("occurredActionAt", toOffsetString(now));
        insertOutbox(eventType, comment.getCommentId(), comment.getCommentId() + ":" + userId, payload, now);
    }

    private void insertOutbox(
            String eventType,
            Long aggregateId,
            String bizKey,
            Map<String, Object> payload,
            LocalDateTime now
    ) {
        String eventId = UUID.randomUUID().toString();
        CommentOutboxEventEntity entity = new CommentOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
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
        envelope.put("producer", "bluenote-comment");
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload);
        return envelope;
    }

    private <T> T executeIdempotently(
            Long userId,
            String operation,
            String rawKey,
            Object request,
            Class<T> responseType,
            Supplier<IdempotentResult<T>> supplier
    ) {
        String idempotentKey = idempotentKey(userId, operation, rawKey);
        String requestHash = sha256(canonicalRequest(request));
        CommentIdempotentRequestEntity existing = reserveIdempotency(userId, operation, idempotentKey, requestHash);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BusinessException(ApiErrorCode.COMMENT_IDEMPOTENCY_MISMATCH);
            }
            if ("SUCCESS".equals(existing.getRequestStatus())) {
                return jsonPayloads.parse(existing.getResponsePayload(), responseType);
            }
            throw new BusinessException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
        }
        IdempotentResult<T> result = supplier.get();
        idempotentRequestMapper.markSuccess(
                idempotentKey,
                result.bizId(),
                canonicalRequest(result.response()),
                now()
        );
        return result.response();
    }

    private CommentIdempotentRequestEntity reserveIdempotency(
            Long userId,
            String operation,
            String idempotentKey,
            String requestHash
    ) {
        LocalDateTime now = now();
        CommentIdempotentRequestEntity entity = new CommentIdempotentRequestEntity();
        entity.setIdempotentKey(idempotentKey);
        entity.setUserId(userId);
        entity.setOperation(operation);
        entity.setRequestHash(requestHash);
        entity.setRequestStatus("PROCESSING");
        entity.setExpireAt(now.plusDays(1));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            idempotentRequestMapper.insert(entity);
            return null;
        } catch (DuplicateKeyException exception) {
            return idempotentRequestMapper.selectByKey(idempotentKey);
        }
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank() || normalized.length() > 1000) {
            throw new BusinessException(ApiErrorCode.COMMENT_CONTENT_INVALID);
        }
        return normalized;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_HOT;
        }
        String normalized = sort.trim();
        if (List.of(SORT_HOT, SORT_TIME_DESC, SORT_TIME_ASC).contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(ApiErrorCode.PARAM_INVALID);
    }

    private String resolveIdempotencyKey(String headerKey, String clientRequestId) {
        String key = headerKey == null || headerKey.isBlank() ? clientRequestId : headerKey;
        if (key == null || key.isBlank()) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        return key.trim();
    }

    private PageCursor parseCursor(String cursor, String sort) {
        if (cursor == null || cursor.isBlank()) {
            return new PageCursor(null, null, null);
        }
        String normalized = cursor;
        Long hotScore = null;
        if (normalized.startsWith("HOT_")) {
            int first = normalized.indexOf('_');
            int second = normalized.indexOf('_', first + 1);
            if (second > 0 && second < normalized.length() - 1) {
                try {
                    hotScore = Long.valueOf(normalized.substring(first + 1, second));
                } catch (NumberFormatException exception) {
                    throw new BusinessException(ApiErrorCode.PARAM_INVALID);
                }
                normalized = normalized.substring(second + 1);
            }
        } else if (SORT_HOT.equals(sort)) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        int separator = normalized.lastIndexOf('_');
        if (separator <= 0 || separator == normalized.length() - 1) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        try {
            LocalDateTime sortAt = OffsetDateTime.parse(normalized.substring(0, separator))
                    .atZoneSameInstant(CHINA_ZONE)
                    .toLocalDateTime();
            Long commentId = Long.valueOf(normalized.substring(separator + 1));
            return new PageCursor(hotScore, sortAt, commentId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private String pageCursor(List<ContentCommentEntity> items, boolean hasMore, String sort) {
        if (!hasMore || items.isEmpty()) {
            return null;
        }
        ContentCommentEntity last = items.get(items.size() - 1);
        String base = toOffsetString(last.getCreatedAt()) + "_" + last.getCommentId();
        if (SORT_HOT.equals(sort)) {
            return "HOT_" + hotScore(last) + "_" + base;
        }
        return base;
    }

    private String plainCursor(List<UserCommentEntity> items, boolean hasMore) {
        if (!hasMore || items.isEmpty()) {
            return null;
        }
        UserCommentEntity last = items.get(items.size() - 1);
        return toOffsetString(last.getCreatedAt()) + "_" + last.getCommentId();
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.max(1, Math.min(size, 50));
    }

    private String idempotentKey(Long userId, String operation, String rawKey) {
        return userId + ":" + operation + ":" + sha256(rawKey).substring(0, 32);
    }

    private String canonicalRequest(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize request", exception);
        }
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

    private String contentPreview(String content) {
        if (content == null || content.length() <= 256) {
            return content == null ? "" : content;
        }
        return content.substring(0, 253) + "...";
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

    private Long id(Map<String, Object> payload, String field) {
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
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String toOffsetString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(CHINA_ZONE).toOffsetDateTime().toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record IdempotentResult<T>(Long bizId, T response) {
    }

    private record PageCursor(Long hotScore, LocalDateTime sortAt, Long commentId) {
    }

    private record NoteSnapshot(String title, String coverUrl) {
    }

    private record PageAuthors(Map<String, CommentAuthorResponse> summaries, boolean degraded) {

        private CommentAuthorResponse author(Long userId) {
            String key = String.valueOf(userId);
            CommentAuthorResponse summary = summaries.get(key);
            if (summary != null) {
                return summary;
            }
            return new CommentAuthorResponse(key, "User " + key, null, "UNKNOWN");
        }
    }
}
