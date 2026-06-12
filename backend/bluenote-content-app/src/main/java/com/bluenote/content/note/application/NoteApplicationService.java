package com.bluenote.content.note.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.core.CursorPage;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.common.ContentIdGenerator;
import com.bluenote.content.common.JsonPayloads;
import com.bluenote.content.file.api.dto.InternalBatchBindFileRequest;
import com.bluenote.content.file.api.dto.InternalBatchValidateFileRequest;
import com.bluenote.content.file.application.FileApplicationService;
import com.bluenote.content.note.api.dto.AuthorRecentNotesItem;
import com.bluenote.content.note.api.dto.AuthorRecentNotesRequest;
import com.bluenote.content.note.api.dto.AuthorRecentNotesResponse;
import com.bluenote.content.note.api.dto.BatchNoteSummaryRequest;
import com.bluenote.content.note.api.dto.BatchNoteSummaryResponse;
import com.bluenote.content.note.api.dto.CommentCheckRequest;
import com.bluenote.content.note.api.dto.CommentCheckResponse;
import com.bluenote.content.note.api.dto.DeleteNoteResponse;
import com.bluenote.content.note.api.dto.DraftNoteResponse;
import com.bluenote.content.note.api.dto.NoteCollectResponse;
import com.bluenote.content.note.api.dto.NoteAuthorResponse;
import com.bluenote.content.note.api.dto.NoteCardResponse;
import com.bluenote.content.note.api.dto.NoteCountsResponse;
import com.bluenote.content.note.api.dto.NoteCounterSourceItem;
import com.bluenote.content.note.api.dto.NoteCounterSourceRequest;
import com.bluenote.content.note.api.dto.NoteCounterSourceResponse;
import com.bluenote.content.note.api.dto.NoteDetailResponse;
import com.bluenote.content.note.api.dto.NoteLikeResponse;
import com.bluenote.content.note.api.dto.NoteMediaInput;
import com.bluenote.content.note.api.dto.NoteMediaResponse;
import com.bluenote.content.note.api.dto.NoteSummaryItem;
import com.bluenote.content.note.api.dto.PublishNoteRequest;
import com.bluenote.content.note.api.dto.PublishNoteResponse;
import com.bluenote.content.note.api.dto.UpsertNoteRequest;
import com.bluenote.content.note.api.dto.ViewerActionResponse;
import com.bluenote.content.note.infrastructure.client.MemberInternalClient;
import com.bluenote.content.note.infrastructure.entity.NoteCollectionEntity;
import com.bluenote.content.note.infrastructure.entity.NoteEntity;
import com.bluenote.content.note.infrastructure.entity.NoteIdempotentRequestEntity;
import com.bluenote.content.note.infrastructure.entity.NoteLikeEntity;
import com.bluenote.content.note.infrastructure.entity.NoteMediaEntity;
import com.bluenote.content.note.infrastructure.entity.NoteOutboxEventEntity;
import com.bluenote.content.note.infrastructure.entity.NoteTopicEntity;
import com.bluenote.content.note.infrastructure.entity.NoteVersionEntity;
import com.bluenote.content.note.infrastructure.mapper.NoteIdempotentRequestMapper;
import com.bluenote.content.note.infrastructure.mapper.NoteInteractionMapper;
import com.bluenote.content.note.infrastructure.mapper.NoteMapper;
import com.bluenote.content.note.infrastructure.mapper.NoteMediaMapper;
import com.bluenote.content.note.infrastructure.mapper.NoteOutboxEventMapper;
import com.bluenote.content.note.infrastructure.mapper.NoteTopicMapper;
import com.bluenote.content.note.infrastructure.mapper.NoteVersionMapper;
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
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteApplicationService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String OP_SAVE_DRAFT = "NOTE_SAVE_DRAFT";
    private static final String OP_PUBLISH_NOTE = "NOTE_PUBLISH";
    private static final String OP_PUBLISH_DRAFT = "NOTE_PUBLISH_DRAFT";
    private static final String SCENE_NOTE_INTERACTION = "NOTE_INTERACTION";
    private static final String INTERACTION_ACTIVE = "ACTIVE";
    private static final String INTERACTION_CANCELED = "CANCELED";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_PRIVATE = "PRIVATE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final int FIRST_VERSION = 1;
    private static final int MAX_MEDIA_COUNT = 9;
    private static final int MAX_TOPIC_COUNT = 10;

    private final NoteMapper noteMapper;
    private final NoteVersionMapper noteVersionMapper;
    private final NoteMediaMapper noteMediaMapper;
    private final NoteTopicMapper noteTopicMapper;
    private final NoteIdempotentRequestMapper idempotentRequestMapper;
    private final NoteOutboxEventMapper outboxEventMapper;
    private final NoteInteractionMapper noteInteractionMapper;
    private final FileApplicationService fileApplicationService;
    private final MemberInternalClient memberInternalClient;
    private final ContentIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;

    public NoteApplicationService(
            NoteMapper noteMapper,
            NoteVersionMapper noteVersionMapper,
            NoteMediaMapper noteMediaMapper,
            NoteTopicMapper noteTopicMapper,
            NoteIdempotentRequestMapper idempotentRequestMapper,
            NoteOutboxEventMapper outboxEventMapper,
            NoteInteractionMapper noteInteractionMapper,
            FileApplicationService fileApplicationService,
            MemberInternalClient memberInternalClient,
            ContentIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper
    ) {
        this.noteMapper = noteMapper;
        this.noteVersionMapper = noteVersionMapper;
        this.noteMediaMapper = noteMediaMapper;
        this.noteTopicMapper = noteTopicMapper;
        this.idempotentRequestMapper = idempotentRequestMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.noteInteractionMapper = noteInteractionMapper;
        this.fileApplicationService = fileApplicationService;
        this.memberInternalClient = memberInternalClient;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DraftNoteResponse saveDraft(String userId, String idempotencyKey, UpsertNoteRequest request) {
        Long authorId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        return executeIdempotently(
                authorId,
                OP_SAVE_DRAFT,
                resolveIdempotencyKey(idempotencyKey, request.clientRequestId()),
                request,
                DraftNoteResponse.class,
                () -> {
                    memberInternalClient.ensureUserAllowed(userId);
                    NoteWriteModel model = prepareWriteModel(authorId, request, false);
                    LocalDateTime now = now();
                    NoteEntity note = insertNote(authorId, model, STATUS_DRAFT, model.visibility(), now, null);
                    insertVersionAndChildren(authorId, note.getNoteId(), FIRST_VERSION, "DRAFT", model, now);
                    bindMediaIfPresent(authorId, note.getNoteId(), model.mediaFiles());
                    DraftNoteResponse response = new DraftNoteResponse(
                            String.valueOf(note.getNoteId()),
                            STATUS_DRAFT,
                            FIRST_VERSION,
                            toOffsetString(now)
                    );
                    return new IdempotentResult<>(note.getNoteId(), response);
                }
        );
    }

    @Transactional
    public PublishNoteResponse publishNewNote(String userId, String idempotencyKey, UpsertNoteRequest request) {
        Long authorId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        return executeIdempotently(
                authorId,
                OP_PUBLISH_NOTE,
                resolveIdempotencyKey(idempotencyKey, request.clientRequestId()),
                request,
                PublishNoteResponse.class,
                () -> {
                    memberInternalClient.ensureUserAllowed(userId);
                    NoteWriteModel model = prepareWriteModel(authorId, request, true);
                    LocalDateTime now = now();
                    String noteStatus = statusForVisibility(model.visibility());
                    NoteEntity note = insertNote(authorId, model, noteStatus, model.visibility(), now, now);
                    insertVersionAndChildren(authorId, note.getNoteId(), FIRST_VERSION, "ACTIVE", model, now);
                    bindMediaIfPresent(authorId, note.getNoteId(), model.mediaFiles());
                    insertNoteOutbox("NotePublished", note, model, now);
                    PublishNoteResponse response = new PublishNoteResponse(
                            String.valueOf(note.getNoteId()),
                            noteStatus,
                            model.visibility(),
                            FIRST_VERSION,
                            toOffsetString(now)
                    );
                    return new IdempotentResult<>(note.getNoteId(), response);
                }
        );
    }

    @Transactional
    public PublishNoteResponse publishDraft(String userId, String noteId, String idempotencyKey, PublishNoteRequest request) {
        Long authorId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        return executeIdempotently(
                authorId,
                OP_PUBLISH_DRAFT,
                resolveIdempotencyKey(idempotencyKey, request.clientRequestId()),
                request,
                PublishNoteResponse.class,
                () -> {
                    memberInternalClient.ensureUserAllowed(userId);
                    NoteEntity note = requireNote(parsedNoteId);
                    requireAuthor(note, authorId);
                    if (!STATUS_DRAFT.equals(note.getNoteStatus())) {
                        throw new BusinessException(ApiErrorCode.NOTE_STATUS_INVALID);
                    }
                    List<NoteMediaEntity> media = noteMediaMapper.selectByNoteAndVersion(note.getNoteId(), note.getLatestVersion());
                    if (media.isEmpty()) {
                        throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID);
                    }
                    String visibility = normalizeVisibility(request.visibility(), VISIBILITY_PUBLIC);
                    String noteStatus = statusForVisibility(visibility);
                    LocalDateTime now = now();
                    int updatedRows = noteMapper.publishDraft(note.getNoteId(), authorId, noteStatus, visibility, now, now);
                    if (updatedRows == 0) {
                        throw new BusinessException(ApiErrorCode.NOTE_STATUS_INVALID);
                    }
                    noteVersionMapper.markActive(note.getNoteId(), note.getLatestVersion());
                    NoteVersionEntity version = noteVersionMapper.selectByNoteAndVersion(note.getNoteId(), note.getLatestVersion());
                    NoteWriteModel model = modelFromVersionAndMedia(visibility, version, media);
                    NoteEntity published = requireNote(note.getNoteId());
                    insertNoteOutbox("NotePublished", published, model, now);
                    PublishNoteResponse response = new PublishNoteResponse(
                            String.valueOf(note.getNoteId()),
                            noteStatus,
                            visibility,
                            published.getCurrentVersion(),
                            toOffsetString(now)
                    );
                    return new IdempotentResult<>(note.getNoteId(), response);
                }
        );
    }

    @Transactional
    public DeleteNoteResponse deleteNote(String userId, String noteId) {
        Long authorId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        NoteEntity note = requireNote(parsedNoteId);
        requireAuthor(note, authorId);
        LocalDateTime now = now();
        noteMapper.markDeleted(parsedNoteId, authorId, now, now);
        insertDeleteOutbox(note, now);
        return new DeleteNoteResponse(noteId, STATUS_DELETED, toOffsetString(now));
    }

    @Transactional(readOnly = true)
    public NoteDetailResponse detail(String noteId, String viewerId) {
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        Long parsedViewerId = parseOptionalId(viewerId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        NoteEntity note = requireNote(parsedNoteId);
        if (!canView(note, parsedViewerId)) {
            throw new BusinessException(ApiErrorCode.NOTE_NOT_FOUND);
        }
        NoteVersionEntity version = requireVersion(note.getNoteId(), note.getCurrentVersion());
        List<NoteMediaResponse> media = noteMediaResponses(note.getNoteId(), note.getCurrentVersion());
        List<String> topics = topicNames(note.getNoteId(), note.getCurrentVersion());
        NoteAuthorResponse author = memberInternalClient.authorSummary(String.valueOf(note.getAuthorId()));

        boolean degraded = false;
        NoteCountsResponse counts;
        try {
            counts = new NoteCountsResponse(
                    noteInteractionMapper.countLikes(note.getNoteId()),
                    noteInteractionMapper.countCollections(note.getNoteId()),
                    0L
            );
        } catch (RuntimeException exception) {
            degraded = true;
            counts = new NoteCountsResponse(0L, 0L, 0L);
        }

        ViewerActionResponse viewerAction;
        try {
            viewerAction = parsedViewerId == null
                    ? new ViewerActionResponse(false, false)
                    : new ViewerActionResponse(
                            noteInteractionMapper.likedByViewer(note.getNoteId(), parsedViewerId),
                            noteInteractionMapper.collectedByViewer(note.getNoteId(), parsedViewerId)
                    );
        } catch (RuntimeException exception) {
            degraded = true;
            viewerAction = new ViewerActionResponse(false, false);
        }

        return new NoteDetailResponse(
                String.valueOf(note.getNoteId()),
                author,
                version.getTitle(),
                version.getContent(),
                note.getVisibility(),
                note.getNoteStatus(),
                note.getCurrentVersion(),
                note.getCommentEnabled() == 1,
                media,
                topics,
                counts,
                viewerAction,
                toOffsetString(note.getPublishedAt()),
                degraded
        );
    }

    @Transactional
    public NoteLikeResponse likeNote(String userId, String noteId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        memberInternalClient.ensureUserAllowed(userId, SCENE_NOTE_INTERACTION);
        NoteEntity note = requireVisibleNoteForInteraction(parsedNoteId, parsedUserId);
        NoteLikeEntity existing = noteInteractionMapper.selectLikeByPair(parsedNoteId, parsedUserId);
        if (existing != null && INTERACTION_ACTIVE.equals(existing.getLikeStatus())) {
            return new NoteLikeResponse(noteId, true);
        }
        LocalDateTime now = now();
        boolean changed = false;
        if (existing == null) {
            NoteLikeEntity entity = new NoteLikeEntity();
            entity.setId(idGenerator.nextId());
            entity.setNoteId(parsedNoteId);
            entity.setAuthorId(note.getAuthorId());
            entity.setUserId(parsedUserId);
            entity.setLikeStatus(INTERACTION_ACTIVE);
            entity.setLikedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            try {
                noteInteractionMapper.insertLike(entity);
                changed = true;
            } catch (DuplicateKeyException exception) {
                existing = noteInteractionMapper.selectLikeByPair(parsedNoteId, parsedUserId);
            }
        }
        if (!changed && existing != null && INTERACTION_CANCELED.equals(existing.getLikeStatus())) {
            changed = noteInteractionMapper.activateLike(existing.getId(), now, now) > 0;
        }
        if (changed) {
            insertNoteInteractionOutbox("NoteLiked", note, parsedUserId, now);
        }
        return new NoteLikeResponse(noteId, true);
    }

    @Transactional
    public NoteLikeResponse unlikeNote(String userId, String noteId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        memberInternalClient.ensureUserAllowed(userId, SCENE_NOTE_INTERACTION);
        NoteEntity note = requireVisibleNoteForInteraction(parsedNoteId, parsedUserId);
        NoteLikeEntity existing = noteInteractionMapper.selectLikeByPair(parsedNoteId, parsedUserId);
        if (existing == null || INTERACTION_CANCELED.equals(existing.getLikeStatus())) {
            return new NoteLikeResponse(noteId, false);
        }
        LocalDateTime now = now();
        boolean changed = noteInteractionMapper.cancelLike(existing.getId(), now, now) > 0;
        if (changed) {
            insertNoteInteractionOutbox("NoteUnliked", note, parsedUserId, now);
        }
        return new NoteLikeResponse(noteId, false);
    }

    @Transactional
    public NoteCollectResponse collectNote(String userId, String noteId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        memberInternalClient.ensureUserAllowed(userId, SCENE_NOTE_INTERACTION);
        NoteEntity note = requireVisibleNoteForInteraction(parsedNoteId, parsedUserId);
        NoteCollectionEntity existing = noteInteractionMapper.selectCollectionByPair(parsedNoteId, parsedUserId);
        if (existing != null && INTERACTION_ACTIVE.equals(existing.getCollectionStatus())) {
            return new NoteCollectResponse(noteId, true);
        }
        LocalDateTime now = now();
        boolean changed = false;
        if (existing == null) {
            NoteCollectionEntity entity = new NoteCollectionEntity();
            entity.setId(idGenerator.nextId());
            entity.setNoteId(parsedNoteId);
            entity.setAuthorId(note.getAuthorId());
            entity.setUserId(parsedUserId);
            entity.setCollectionStatus(INTERACTION_ACTIVE);
            entity.setCollectedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            try {
                noteInteractionMapper.insertCollection(entity);
                changed = true;
            } catch (DuplicateKeyException exception) {
                existing = noteInteractionMapper.selectCollectionByPair(parsedNoteId, parsedUserId);
            }
        }
        if (!changed && existing != null && INTERACTION_CANCELED.equals(existing.getCollectionStatus())) {
            changed = noteInteractionMapper.activateCollection(existing.getId(), now, now) > 0;
        }
        if (changed) {
            insertNoteInteractionOutbox("NoteCollected", note, parsedUserId, now);
        }
        return new NoteCollectResponse(noteId, true);
    }

    @Transactional
    public NoteCollectResponse uncollectNote(String userId, String noteId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedNoteId = parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND);
        memberInternalClient.ensureUserAllowed(userId, SCENE_NOTE_INTERACTION);
        NoteEntity note = requireVisibleNoteForInteraction(parsedNoteId, parsedUserId);
        NoteCollectionEntity existing = noteInteractionMapper.selectCollectionByPair(parsedNoteId, parsedUserId);
        if (existing == null || INTERACTION_CANCELED.equals(existing.getCollectionStatus())) {
            return new NoteCollectResponse(noteId, false);
        }
        LocalDateTime now = now();
        boolean changed = noteInteractionMapper.cancelCollection(existing.getId(), now, now) > 0;
        if (changed) {
            insertNoteInteractionOutbox("NoteUncollected", note, parsedUserId, now);
        }
        return new NoteCollectResponse(noteId, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<NoteCardResponse> authorNotes(String authorId, String cursor, Integer size) {
        Long parsedAuthorId = parseId(authorId, ApiErrorCode.USER_NOT_FOUND);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<NoteEntity> notes = noteMapper.selectAuthorPublishedPage(
                parsedAuthorId,
                pageCursor.sortAt(),
                pageCursor.noteId(),
                pageSize + 1
        );
        return toCardPage(notes, pageSize);
    }

    @Transactional(readOnly = true)
    public CursorPage<NoteCardResponse> myNotes(String userId, String status, String cursor, Integer size) {
        Long authorId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String normalizedStatus = normalizeOptionalNoteStatus(status);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<NoteEntity> notes = noteMapper.selectMyNotesPage(
                authorId,
                normalizedStatus,
                pageCursor.sortAt(),
                pageCursor.noteId(),
                pageSize + 1
        );
        return toCardPage(notes, pageSize);
    }

    @Transactional(readOnly = true)
    public CursorPage<NoteCardResponse> myCollections(String userId, String cursor, Integer size) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<NoteEntity> notes = noteMapper.selectMyCollectedNotesPage(
                parsedUserId,
                pageCursor.sortAt(),
                pageCursor.noteId(),
                pageSize + 1
        );
        return toInteractionCardPage(notes, pageSize);
    }

    @Transactional(readOnly = true)
    public CursorPage<NoteCardResponse> myLikes(String userId, String cursor, Integer size) {
        Long parsedUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        PageCursor pageCursor = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<NoteEntity> notes = noteMapper.selectMyLikedNotesPage(
                parsedUserId,
                pageCursor.sortAt(),
                pageCursor.noteId(),
                pageSize + 1
        );
        return toInteractionCardPage(notes, pageSize);
    }

    @Transactional(readOnly = true)
    public BatchNoteSummaryResponse batchSummary(BatchNoteSummaryRequest request) {
        List<Long> noteIds = request.noteIds().stream()
                .map(noteId -> parseId(noteId, ApiErrorCode.NOTE_NOT_FOUND))
                .toList();
        Map<Long, NoteEntity> notes = noteMapper.selectByNoteIds(noteIds).stream()
                .collect(Collectors.toMap(NoteEntity::getNoteId, note -> note));
        Long viewerId = parseOptionalId(request.viewerId(), ApiErrorCode.ACCESS_TOKEN_INVALID);
        boolean includeInvisible = Boolean.TRUE.equals(request.includeInvisible());
        List<NoteSummaryItem> summaries = new ArrayList<>();
        for (String rawNoteId : request.noteIds()) {
            Long parsedNoteId = parseId(rawNoteId, ApiErrorCode.NOTE_NOT_FOUND);
            NoteEntity note = notes.get(parsedNoteId);
            if (note == null || (!includeInvisible && !canView(note, viewerId))) {
                continue;
            }
            summaries.add(toSummaryItem(note));
        }
        return new BatchNoteSummaryResponse(summaries);
    }

    @Transactional(readOnly = true)
    public AuthorRecentNotesResponse authorRecentNotes(AuthorRecentNotesRequest request) {
        int limitPerAuthor = normalizeLimitPerAuthor(request.limitPerAuthor());
        LocalDateTime publishedAfter = parseOptionalOffsetDateTime(request.publishedAfter(), ApiErrorCode.PARAM_INVALID);
        List<Long> authorIds = request.authorIds().stream()
                .map(authorId -> parseId(authorId, ApiErrorCode.USER_NOT_FOUND))
                .distinct()
                .toList();
        if (authorIds.isEmpty()) {
            return new AuthorRecentNotesResponse(List.of());
        }

        List<NoteEntity> notes = noteMapper.selectRecentPublicByAuthors(
                authorIds,
                publishedAfter,
                limitPerAuthor
        );
        Map<Long, List<NoteEntity>> notesByAuthor = notes.stream()
                .collect(Collectors.groupingBy(
                        NoteEntity::getAuthorId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<AuthorRecentNotesItem> authors = authorIds.stream()
                .map(authorId -> new AuthorRecentNotesItem(
                        String.valueOf(authorId),
                        notesByAuthor.getOrDefault(authorId, List.of()).stream()
                                .limit(limitPerAuthor)
                                .map(this::toSummaryItem)
                                .toList()
                ))
                .toList();
        return new AuthorRecentNotesResponse(authors);
    }

    @Transactional(readOnly = true)
    public CommentCheckResponse commentCheck(CommentCheckRequest request) {
        Long noteId = parseId(request.noteId(), ApiErrorCode.NOTE_NOT_FOUND);
        Long viewerId = parseOptionalId(request.viewerId(), ApiErrorCode.ACCESS_TOKEN_INVALID);
        NoteEntity note = noteMapper.selectByNoteId(noteId);
        if (note == null || !canView(note, viewerId)) {
            return new CommentCheckResponse(false, false, null, null, null);
        }
        return new CommentCheckResponse(
                true,
                note.getCommentEnabled() == 1 && STATUS_PUBLISHED.equals(note.getNoteStatus()),
                String.valueOf(note.getAuthorId()),
                note.getNoteStatus(),
                note.getVisibility()
        );
    }

    @Transactional(readOnly = true)
    public NoteCounterSourceResponse counterSource(NoteCounterSourceRequest request) {
        List<NoteCounterSourceItem> items = request.targets().stream()
                .map(target -> {
                    Long targetId = parseId(target.targetId(), ApiErrorCode.PARAM_INVALID);
                    Map<String, Long> counts = new LinkedHashMap<>();
                    if ("NOTE".equals(target.targetType())) {
                        for (String field : target.fields()) {
                            if ("like_count".equals(field)) {
                                counts.put(field, noteInteractionMapper.countLikes(targetId));
                            } else if ("collect_count".equals(field)) {
                                counts.put(field, noteInteractionMapper.countCollections(targetId));
                            }
                        }
                    } else if ("USER".equals(target.targetType())) {
                        for (String field : target.fields()) {
                            if ("note_count".equals(field)) {
                                counts.put(field, noteMapper.countPublicPublishedByAuthor(targetId));
                            } else if ("liked_count".equals(field)) {
                                counts.put(field, noteInteractionMapper.countActiveLikesByAuthor(targetId));
                            }
                        }
                    } else {
                        throw new BusinessException(ApiErrorCode.PARAM_INVALID);
                    }
                    return new NoteCounterSourceItem(target.targetType(), target.targetId(), counts);
                })
                .toList();
        return new NoteCounterSourceResponse(items);
    }

    private NoteEntity insertNote(
            Long authorId,
            NoteWriteModel model,
            String noteStatus,
            String visibility,
            LocalDateTime now,
            LocalDateTime publishedAt
    ) {
        NoteEntity note = new NoteEntity();
        note.setNoteId(idGenerator.nextId());
        note.setAuthorId(authorId);
        note.setNoteType("IMAGE_TEXT");
        note.setNoteStatus(noteStatus);
        note.setVisibility(visibility);
        note.setCurrentVersion(FIRST_VERSION);
        note.setLatestVersion(FIRST_VERSION);
        note.setCoverFileId(model.coverFileId());
        note.setCommentEnabled(model.commentEnabled() ? 1 : 0);
        note.setPublishedAt(publishedAt);
        note.setLastEditedAt(now);
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        note.setDeleted(0);
        noteMapper.insert(note);
        return note;
    }

    private void insertVersionAndChildren(
            Long authorId,
            Long noteId,
            int versionNo,
            String versionStatus,
            NoteWriteModel model,
            LocalDateTime now
    ) {
        NoteVersionEntity version = new NoteVersionEntity();
        version.setId(idGenerator.nextId());
        version.setNoteId(noteId);
        version.setVersionNo(versionNo);
        version.setTitle(model.title());
        version.setContent(model.content());
        version.setContentPreview(contentPreview(model.content()));
        version.setVersionStatus(versionStatus);
        version.setAuditStatus("SKIPPED");
        version.setCreatedBy(authorId);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        noteVersionMapper.insert(version);

        for (NoteMediaInput mediaInput : model.mediaFiles()) {
            NoteMediaEntity media = new NoteMediaEntity();
            media.setId(idGenerator.nextId());
            media.setNoteId(noteId);
            media.setVersionNo(versionNo);
            media.setFileId(parseId(mediaInput.fileId(), ApiErrorCode.NOTE_MEDIA_INVALID));
            media.setMediaType(mediaInput.mediaType());
            media.setSortOrder(mediaInput.sortOrder());
            media.setCover(Boolean.TRUE.equals(mediaInput.cover()) ? 1 : 0);
            media.setCreatedAt(now);
            noteMediaMapper.insert(media);
        }

        int sortOrder = 1;
        for (String topic : model.topics()) {
            NoteTopicEntity topicEntity = new NoteTopicEntity();
            topicEntity.setId(idGenerator.nextId());
            topicEntity.setNoteId(noteId);
            topicEntity.setVersionNo(versionNo);
            topicEntity.setTopicName(topic);
            topicEntity.setSortOrder(sortOrder++);
            topicEntity.setCreatedAt(now);
            noteTopicMapper.insert(topicEntity);
        }
    }

    private void bindMediaIfPresent(Long authorId, Long noteId, List<NoteMediaInput> mediaFiles) {
        if (mediaFiles.isEmpty()) {
            return;
        }
        try {
            fileApplicationService.batchBindFiles(new InternalBatchBindFileRequest(
                    String.valueOf(authorId),
                    "NOTE_MEDIA",
                    String.valueOf(noteId),
                    mediaFiles.stream().map(NoteMediaInput::fileId).toList()
            ));
        } catch (BusinessException exception) {
            throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID, noteMediaErrorData(exception));
        }
    }

    private NoteWriteModel prepareWriteModel(Long authorId, UpsertNoteRequest request, boolean requireMedia) {
        String title = normalizeTitle(request.title());
        String content = normalizeContent(request.content(), requireMedia);
        String visibility = normalizeVisibility(request.visibility(), requireMedia ? VISIBILITY_PUBLIC : VISIBILITY_PRIVATE);
        boolean commentEnabled = request.commentEnabled() == null || request.commentEnabled();
        List<NoteMediaInput> mediaFiles = normalizeMediaFiles(request.mediaFiles(), requireMedia);
        List<String> topics = normalizeTopics(request.topics());
        validateMediaFiles(authorId, mediaFiles);
        Long coverFileId = mediaFiles.stream()
                .filter(media -> Boolean.TRUE.equals(media.cover()))
                .findFirst()
                .map(media -> parseId(media.fileId(), ApiErrorCode.NOTE_MEDIA_INVALID))
                .orElse(null);
        return new NoteWriteModel(title, content, visibility, commentEnabled, mediaFiles, topics, coverFileId);
    }

    private NoteWriteModel modelFromVersionAndMedia(
            String visibility,
            NoteVersionEntity version,
            List<NoteMediaEntity> media
    ) {
        List<NoteMediaInput> mediaInputs = media.stream()
                .map(item -> new NoteMediaInput(
                        String.valueOf(item.getFileId()),
                        item.getMediaType(),
                        item.getSortOrder(),
                        item.getCover() == 1
                ))
                .toList();
        Long coverFileId = media.stream()
                .filter(item -> item.getCover() == 1)
                .findFirst()
                .map(NoteMediaEntity::getFileId)
                .orElse(null);
        return new NoteWriteModel(
                version.getTitle(),
                version.getContent(),
                visibility,
                true,
                mediaInputs,
                List.of(),
                coverFileId
        );
    }

    private List<NoteMediaInput> normalizeMediaFiles(List<NoteMediaInput> mediaFiles, boolean requireMedia) {
        List<NoteMediaInput> normalized = mediaFiles == null ? List.of() : mediaFiles.stream()
                .sorted(Comparator.comparing(NoteMediaInput::sortOrder))
                .toList();
        if (requireMedia && normalized.isEmpty()) {
            throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID);
        }
        if (normalized.size() > MAX_MEDIA_COUNT) {
            throw new BusinessException(ApiErrorCode.NOTE_MEDIA_COUNT_EXCEEDED);
        }
        if (normalized.isEmpty()) {
            return normalized;
        }
        int coverCount = 0;
        LinkedHashSet<String> fileIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> sortOrders = new LinkedHashSet<>();
        for (NoteMediaInput media : normalized) {
            if (!"IMAGE".equals(media.mediaType())) {
                throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID);
            }
            parseId(media.fileId(), ApiErrorCode.NOTE_MEDIA_INVALID);
            if (!fileIds.add(media.fileId()) || !sortOrders.add(media.sortOrder())) {
                throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID);
            }
            if (Boolean.TRUE.equals(media.cover())) {
                coverCount++;
            }
        }
        if (coverCount != 1) {
            throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID);
        }
        return normalized;
    }

    private void validateMediaFiles(Long authorId, List<NoteMediaInput> mediaFiles) {
        if (mediaFiles.isEmpty()) {
            return;
        }
        try {
            fileApplicationService.batchValidateFiles(new InternalBatchValidateFileRequest(
                    String.valueOf(authorId),
                    "NOTE_IMAGE",
                    mediaFiles.stream().map(NoteMediaInput::fileId).toList(),
                    MAX_MEDIA_COUNT
            ));
        } catch (BusinessException exception) {
            throw new BusinessException(ApiErrorCode.NOTE_MEDIA_INVALID, noteMediaErrorData(exception));
        }
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 128) {
            throw new BusinessException(ApiErrorCode.NOTE_TITLE_INVALID);
        }
        return title.trim();
    }

    private String normalizeContent(String content, boolean requireContent) {
        String normalized = content == null ? "" : content.trim();
        if ((requireContent && normalized.isBlank()) || normalized.length() > 5000) {
            throw new BusinessException(ApiErrorCode.NOTE_CONTENT_INVALID);
        }
        return normalized;
    }

    private String normalizeVisibility(String visibility, String defaultVisibility) {
        String normalized = visibility == null || visibility.isBlank() ? defaultVisibility : visibility;
        if (!VISIBILITY_PUBLIC.equals(normalized) && !VISIBILITY_PRIVATE.equals(normalized)) {
            throw new BusinessException(ApiErrorCode.NOTE_VISIBILITY_INVALID);
        }
        return normalized;
    }

    private List<String> normalizeTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        return topics.stream()
                .filter(topic -> topic != null && !topic.isBlank())
                .map(String::trim)
                .filter(topic -> topic.length() <= 64)
                .distinct()
                .limit(MAX_TOPIC_COUNT)
                .toList();
    }

    private String statusForVisibility(String visibility) {
        return VISIBILITY_PRIVATE.equals(visibility) ? STATUS_PRIVATE : STATUS_PUBLISHED;
    }

    private CursorPage<NoteCardResponse> toCardPage(List<NoteEntity> notes, int pageSize) {
        return toCardPage(notes, pageSize, false);
    }

    private CursorPage<NoteCardResponse> toInteractionCardPage(List<NoteEntity> notes, int pageSize) {
        return toCardPage(notes, pageSize, true);
    }

    private CursorPage<NoteCardResponse> toCardPage(List<NoteEntity> notes, int pageSize, boolean useInteractionSort) {
        boolean hasMore = notes.size() > pageSize;
        List<NoteEntity> pageItems = hasMore ? notes.subList(0, pageSize) : notes;
        List<NoteCardResponse> cards = pageItems.stream()
                .map(this::toCard)
                .toList();
        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            NoteEntity last = pageItems.get(pageItems.size() - 1);
            LocalDateTime cursorSortAt = useInteractionSort && last.getInteractionAt() != null
                    ? last.getInteractionAt()
                    : sortAt(last);
            nextCursor = toOffsetString(cursorSortAt) + "_" + last.getNoteId();
        }
        return new CursorPage<>(cards, nextCursor, hasMore);
    }

    private NoteCardResponse toCard(NoteEntity note) {
        NoteVersionEntity version = requireVersion(note.getNoteId(), note.getCurrentVersion());
        return new NoteCardResponse(
                String.valueOf(note.getNoteId()),
                version.getTitle(),
                fileAccessUrl(note.getCoverFileId()),
                String.valueOf(note.getAuthorId()),
                safeCountLikes(note.getNoteId()),
                safeCountCollections(note.getNoteId()),
                toOffsetString(note.getPublishedAt())
        );
    }

    private NoteSummaryItem toSummaryItem(NoteEntity note) {
        NoteVersionEntity version = requireVersion(note.getNoteId(), note.getCurrentVersion());
        return new NoteSummaryItem(
                String.valueOf(note.getNoteId()),
                String.valueOf(note.getAuthorId()),
                version.getTitle(),
                version.getContentPreview(),
                stringValue(note.getCoverFileId()),
                fileAccessUrl(note.getCoverFileId()),
                note.getNoteStatus(),
                note.getVisibility(),
                toOffsetString(note.getPublishedAt())
        );
    }

    private List<NoteMediaResponse> noteMediaResponses(Long noteId, Integer versionNo) {
        return noteMediaMapper.selectByNoteAndVersion(noteId, versionNo).stream()
                .map(media -> new NoteMediaResponse(
                        String.valueOf(media.getFileId()),
                        media.getMediaType(),
                        media.getSortOrder(),
                        media.getCover() == 1,
                        fileAccessUrl(media.getFileId())
                ))
                .toList();
    }

    private List<String> topicNames(Long noteId, Integer versionNo) {
        return noteTopicMapper.selectByNoteAndVersion(noteId, versionNo).stream()
                .map(NoteTopicEntity::getTopicName)
                .toList();
    }

    private String fileAccessUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        try {
            return fileApplicationService.publicAccessUrl(String.valueOf(fileId));
        } catch (BusinessException exception) {
            return null;
        }
    }

    private long safeCountLikes(Long noteId) {
        try {
            return noteInteractionMapper.countLikes(noteId);
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private long safeCountCollections(Long noteId) {
        try {
            return noteInteractionMapper.countCollections(noteId);
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private boolean canView(NoteEntity note, Long viewerId) {
        if (STATUS_DELETED.equals(note.getNoteStatus()) || note.getDeleted() == 1) {
            return false;
        }
        if (viewerId != null && note.getAuthorId().equals(viewerId)) {
            return true;
        }
        return STATUS_PUBLISHED.equals(note.getNoteStatus()) && VISIBILITY_PUBLIC.equals(note.getVisibility());
    }

    private NoteEntity requireVisibleNoteForInteraction(Long noteId, Long userId) {
        NoteEntity note = requireNote(noteId);
        if (!canView(note, userId)) {
            throw new BusinessException(ApiErrorCode.NOTE_NOT_FOUND);
        }
        return note;
    }

    private NoteEntity requireNote(Long noteId) {
        NoteEntity note = noteMapper.selectByNoteId(noteId);
        if (note == null) {
            throw new BusinessException(ApiErrorCode.NOTE_NOT_FOUND);
        }
        return note;
    }

    private NoteVersionEntity requireVersion(Long noteId, Integer versionNo) {
        NoteVersionEntity version = noteVersionMapper.selectByNoteAndVersion(noteId, versionNo);
        if (version == null) {
            throw new BusinessException(ApiErrorCode.NOTE_NOT_FOUND);
        }
        return version;
    }

    private void requireAuthor(NoteEntity note, Long authorId) {
        if (!note.getAuthorId().equals(authorId)) {
            throw new BusinessException(ApiErrorCode.NOTE_AUTHOR_FORBIDDEN);
        }
    }

    private void insertNoteOutbox(String eventType, NoteEntity note, NoteWriteModel model, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("noteId", String.valueOf(note.getNoteId()));
        payload.put("authorId", String.valueOf(note.getAuthorId()));
        payload.put("title", model.title());
        payload.put("contentPreview", contentPreview(model.content()));
        payload.put("coverFileId", stringValue(note.getCoverFileId()));
        payload.put("visibility", note.getVisibility());
        payload.put("noteStatus", note.getNoteStatus());
        payload.put("currentVersion", note.getCurrentVersion());
        payload.put("publishedAt", toOffsetString(note.getPublishedAt()));
        insertOutbox(eventType, note.getNoteId(), payload, now);
    }

    private void insertDeleteOutbox(NoteEntity note, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("noteId", String.valueOf(note.getNoteId()));
        payload.put("authorId", String.valueOf(note.getAuthorId()));
        payload.put("visibility", note.getVisibility());
        payload.put("noteStatus", note.getNoteStatus());
        payload.put("deletedAt", toOffsetString(now));
        insertOutbox("NoteDeleted", note.getNoteId(), payload, now);
    }

    private void insertNoteInteractionOutbox(String eventType, NoteEntity note, Long userId, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("noteId", String.valueOf(note.getNoteId()));
        payload.put("authorId", String.valueOf(note.getAuthorId()));
        payload.put("userId", String.valueOf(userId));
        payload.put("occurredAt", toOffsetString(now));
        insertOutbox(eventType, note.getNoteId(), payload, now);
    }

    private void insertOutbox(String eventType, Long noteId, Map<String, Object> payload, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        NoteOutboxEventEntity entity = new NoteOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(noteId);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, noteId, payload)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            Long aggregateId,
            Map<String, Object> payload
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", OffsetDateTime.now(CHINA_ZONE).toString());
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-note");
        envelope.put("bizKey", String.valueOf(aggregateId));
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
        NoteIdempotentRequestEntity existing = reserveIdempotency(userId, operation, idempotentKey, requestHash);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BusinessException(ApiErrorCode.NOTE_IDEMPOTENCY_MISMATCH);
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

    private NoteIdempotentRequestEntity reserveIdempotency(
            Long userId,
            String operation,
            String idempotentKey,
            String requestHash
    ) {
        LocalDateTime now = now();
        NoteIdempotentRequestEntity entity = new NoteIdempotentRequestEntity();
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

    private Map<String, Object> noteMediaErrorData(BusinessException exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", ApiErrorCode.NOTE_MEDIA_INVALID.reason());
        data.put("fileReason", exception.errorCode().reason());
        data.put("fileError", exception.errorData());
        return data;
    }

    private String resolveIdempotencyKey(String headerKey, String clientRequestId) {
        String key = headerKey == null || headerKey.isBlank() ? clientRequestId : headerKey;
        if (key == null || key.isBlank()) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        return key.trim();
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

    private PageCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new PageCursor(null, null);
        }
        int separator = cursor.lastIndexOf('_');
        if (separator <= 0 || separator == cursor.length() - 1) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        try {
            LocalDateTime sortAt = OffsetDateTime.parse(cursor.substring(0, separator))
                    .atZoneSameInstant(CHINA_ZONE)
                    .toLocalDateTime();
            Long noteId = Long.valueOf(cursor.substring(separator + 1));
            return new PageCursor(sortAt, noteId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.max(1, Math.min(size, 50));
    }

    private int normalizeLimitPerAuthor(Integer limitPerAuthor) {
        if (limitPerAuthor == null) {
            return 20;
        }
        return Math.max(1, Math.min(limitPerAuthor, 50));
    }

    private String normalizeOptionalNoteStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if (!List.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_PRIVATE, "AUDIT_REJECTED", "OFFLINE").contains(status)) {
            throw new BusinessException(ApiErrorCode.NOTE_STATUS_INVALID);
        }
        return status;
    }

    private String contentPreview(String content) {
        if (content == null || content.length() <= 256) {
            return content == null ? "" : content;
        }
        return content.substring(0, 253) + "...";
    }

    private LocalDateTime sortAt(NoteEntity note) {
        return note.getPublishedAt() == null ? note.getUpdatedAt() : note.getPublishedAt();
    }

    private LocalDateTime parseOptionalOffsetDateTime(String value, ApiErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
        } catch (RuntimeException exception) {
            throw new BusinessException(errorCode);
        }
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

    private Long parseOptionalId(String value, ApiErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseId(value, errorCode);
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

    private record NoteWriteModel(
            String title,
            String content,
            String visibility,
            boolean commentEnabled,
            List<NoteMediaInput> mediaFiles,
            List<String> topics,
            Long coverFileId
    ) {
    }

    private record IdempotentResult<T>(Long bizId, T response) {
    }

    private record PageCursor(LocalDateTime sortAt, Long noteId) {
    }
}
