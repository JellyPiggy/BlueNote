package com.bluenote.content.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluenote.content.comment.api.dto.CommentAuthorResponse;
import com.bluenote.content.comment.api.dto.CommentConsumeEventRequest;
import com.bluenote.content.comment.api.dto.CommentCursorPage;
import com.bluenote.content.comment.api.dto.CommentItemResponse;
import com.bluenote.content.comment.infrastructure.entity.CommentConsumeRecordEntity;
import com.bluenote.content.comment.infrastructure.client.MemberInternalClient;
import com.bluenote.content.comment.infrastructure.entity.CommentContentEntity;
import com.bluenote.content.comment.infrastructure.entity.CommentOutboxEventEntity;
import com.bluenote.content.comment.infrastructure.entity.ContentCommentEntity;
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
import com.bluenote.content.note.api.dto.CommentCheckRequest;
import com.bluenote.content.note.api.dto.CommentCheckResponse;
import com.bluenote.content.note.application.NoteApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentApplicationServiceTest {

    private static final Long NOTE_ID = 800001L;
    private static final String VIEWER_ID = "10001";

    @Mock
    private ContentCommentMapper contentCommentMapper;
    @Mock
    private UserCommentMapper userCommentMapper;
    @Mock
    private CommentContentMapper commentContentMapper;
    @Mock
    private CommentLikeMapper commentLikeMapper;
    @Mock
    private CommentIdempotentRequestMapper idempotentRequestMapper;
    @Mock
    private CommentOperationLogMapper operationLogMapper;
    @Mock
    private CommentOutboxEventMapper outboxEventMapper;
    @Mock
    private CommentConsumeRecordMapper consumeRecordMapper;
    @Mock
    private MemberInternalClient memberInternalClient;
    @Mock
    private NoteApplicationService noteApplicationService;
    @Mock
    private CommentRedisStore commentRedisStore;
    @Mock
    private ContentIdGenerator idGenerator;
    @Mock
    private JsonPayloads jsonPayloads;

    private CommentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CommentApplicationService(
                contentCommentMapper,
                userCommentMapper,
                commentContentMapper,
                commentLikeMapper,
                idempotentRequestMapper,
                operationLogMapper,
                outboxEventMapper,
                consumeRecordMapper,
                memberInternalClient,
                noteApplicationService,
                commentRedisStore,
                idGenerator,
                jsonPayloads,
                new ObjectMapper()
        );

        lenient().when(noteApplicationService.commentCheck(any(CommentCheckRequest.class)))
                .thenReturn(new CommentCheckResponse(true, true, "20001", "PUBLISHED", "PUBLIC"));
        lenient().when(memberInternalClient.batchSummary(anyList())).thenReturn(Map.of(
                "10002", new CommentAuthorResponse("10002", "A", null, "NORMAL"),
                "10003", new CommentAuthorResponse("10003", "B", null, "NORMAL"),
                "10004", new CommentAuthorResponse("10004", "C", null, "NORMAL")
        ));
    }

    @Test
    void noteCommentsUsesRedisHotCandidatesWhenCacheIsComplete() {
        ContentCommentEntity lower = rootComment(1001L, 10002L, 1, 1, "2026-06-11T10:01:00");
        ContentCommentEntity hottest = rootComment(1002L, 10003L, 10, 0, "2026-06-11T10:02:00");
        ContentCommentEntity second = rootComment(1003L, 10004L, 0, 3, "2026-06-11T10:03:00");
        when(commentRedisStore.hotComments(NOTE_ID, 23)).thenReturn(List.of(
                new HotCommentMember(hottest.getCommentId(), 40D),
                new HotCommentMember(second.getCommentId(), 18D),
                new HotCommentMember(lower.getCommentId(), 10D)
        ));
        when(contentCommentMapper.selectVisibleRootsByCommentIds(List.of(1002L, 1003L, 1001L)))
                .thenReturn(List.of(lower, second, hottest));
        when(commentContentMapper.selectByCommentIds(List.of(1002L, 1003L)))
                .thenReturn(List.of(content(1002L, "hot"), content(1003L, "second")));
        when(commentLikeMapper.selectActiveByComments(10001L, List.of(1002L, 1003L))).thenReturn(List.of());

        CommentCursorPage<CommentItemResponse> page = service.noteComments(
                String.valueOf(NOTE_ID),
                "HOT",
                null,
                2,
                VIEWER_ID
        );

        assertThat(page.items()).extracting(CommentItemResponse::commentId)
                .containsExactly("1002", "1003");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("HOT_18_2026-06-11T10:03+08:00_1003");
        verify(contentCommentMapper, never()).selectRootPage(eq(NOTE_ID), eq("HOT"), any(), any(), any(), any(Integer.class));
    }

    @Test
    void noteCommentsFallsBackToMysqlHotAndRebuildsRedisWhenHotCacheMisses() {
        ContentCommentEntity hottest = rootComment(1002L, 10003L, 10, 0, "2026-06-11T10:02:00");
        ContentCommentEntity second = rootComment(1003L, 10004L, 0, 3, "2026-06-11T10:03:00");
        when(commentRedisStore.hotComments(NOTE_ID, 23)).thenReturn(List.of());
        when(contentCommentMapper.selectRootPage(NOTE_ID, "HOT", null, null, null, 3))
                .thenReturn(List.of(hottest, second));
        when(contentCommentMapper.selectHotRootCandidates(NOTE_ID, 1000)).thenReturn(List.of(hottest, second));
        when(contentCommentMapper.selectRootPage(NOTE_ID, "TIME_DESC", null, null, null, 1000))
                .thenReturn(List.of(hottest, second));
        when(commentContentMapper.selectByCommentIds(List.of(1002L, 1003L)))
                .thenReturn(List.of(content(1002L, "hot"), content(1003L, "second")));
        when(commentLikeMapper.selectActiveByComments(10001L, List.of(1002L, 1003L))).thenReturn(List.of());

        CommentCursorPage<CommentItemResponse> page = service.noteComments(
                String.valueOf(NOTE_ID),
                "HOT",
                null,
                2,
                VIEWER_ID
        );

        assertThat(page.items()).extracting(CommentItemResponse::commentId)
                .containsExactly("1002", "1003");
        assertThat(page.hasMore()).isFalse();
        verify(commentRedisStore).replaceHotComments(eq(NOTE_ID), eq(List.of(
                new HotCommentMember(1002L, 40D),
                new HotCommentMember(1003L, 18D)
        )), eq(1000));
        verify(commentRedisStore).replaceRootTimeComments(eq(NOTE_ID), eq(List.of(
                new CommentTimeMember(1002L, hottest.getCreatedAt()),
                new CommentTimeMember(1003L, second.getCreatedAt())
        )));
    }

    @Test
    void noteCommentsFillsPartialHotCacheFromMysqlTimePage() {
        ContentCommentEntity hottest = rootComment(1002L, 10003L, 10, 0, "2026-06-11T10:02:00");
        ContentCommentEntity fresh = rootComment(1004L, 10004L, 0, 0, "2026-06-11T10:04:00");
        when(commentRedisStore.hotComments(NOTE_ID, 23)).thenReturn(List.of(
                new HotCommentMember(hottest.getCommentId(), 40D)
        ));
        when(contentCommentMapper.selectVisibleRootsByCommentIds(List.of(1002L)))
                .thenReturn(List.of(hottest));
        when(contentCommentMapper.selectRootPage(NOTE_ID, "TIME_DESC", null, null, null, 5))
                .thenReturn(List.of(fresh, hottest));
        when(contentCommentMapper.selectHotRootCandidates(NOTE_ID, 1000)).thenReturn(List.of(hottest, fresh));
        when(contentCommentMapper.selectRootPage(NOTE_ID, "TIME_DESC", null, null, null, 1000))
                .thenReturn(List.of(fresh, hottest));
        when(commentContentMapper.selectByCommentIds(List.of(1002L, 1004L)))
                .thenReturn(List.of(content(1002L, "hot"), content(1004L, "fresh")));
        when(commentLikeMapper.selectActiveByComments(10001L, List.of(1002L, 1004L))).thenReturn(List.of());

        CommentCursorPage<CommentItemResponse> page = service.noteComments(
                String.valueOf(NOTE_ID),
                "HOT",
                null,
                2,
                VIEWER_ID
        );

        assertThat(page.items()).extracting(CommentItemResponse::commentId)
                .containsExactly("1002", "1004");
        verify(contentCommentMapper).selectRootPage(NOTE_ID, "TIME_DESC", null, null, null, 5);
    }

    @Test
    void noteCommentsUsesTimeCursorForHotMysqlFillers() {
        LocalDateTime cursorTime = LocalDateTime.parse("2026-06-11T10:03:00");
        when(commentRedisStore.hotComments(NOTE_ID, 23)).thenReturn(List.of());
        when(contentCommentMapper.selectRootPage(NOTE_ID, "HOT", 18L, cursorTime, 1003L, 3))
                .thenReturn(List.of());
        when(contentCommentMapper.selectHotRootCandidates(NOTE_ID, 1000)).thenReturn(List.of());
        when(contentCommentMapper.selectRootPage(NOTE_ID, "TIME_DESC", null, null, null, 1000)).thenReturn(List.of());

        CommentCursorPage<CommentItemResponse> page = service.noteComments(
                String.valueOf(NOTE_ID),
                "HOT",
                "HOT_18_2026-06-11T10:03:00+08:00_1003",
                2,
                VIEWER_ID
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verify(contentCommentMapper).selectRootPage(NOTE_ID, "HOT", 18L, cursorTime, 1003L, 3);
    }

    @Test
    void likingReplyDoesNotAddHotScoreToReplyItself() {
        ContentCommentEntity reply = replyComment(2002L, 10002L, 1001L);
        when(contentCommentMapper.selectByCommentId(2002L)).thenReturn(reply);
        when(commentLikeMapper.selectByPair(2002L, 10001L)).thenReturn(null);

        service.like(VIEWER_ID, "2002");

        verify(commentLikeMapper).insert(any());
        verify(contentCommentMapper).incrementLikeSnapshot(eq(2002L), eq(1L), eq(0L), any(LocalDateTime.class));
        verify(commentRedisStore, never()).updateHotComment(any(), any(), any(Double.class), any(Integer.class));
    }

    @Test
    void deletingReplyPublishesAffectedReplyCount() {
        ContentCommentEntity reply = replyComment(2002L, 10002L, 1001L);
        when(contentCommentMapper.selectByCommentId(2002L)).thenReturn(reply);
        when(jsonPayloads.stringify(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
            return String.valueOf(payload.get("affectedReplyCount"));
        });

        service.deleteComment("10002", "2002");

        verify(contentCommentMapper).incrementReplySnapshot(eq(1001L), eq(-1L), eq(-6L), any(LocalDateTime.class));
        ArgumentCaptor<CommentOutboxEventEntity> captor = ArgumentCaptor.forClass(CommentOutboxEventEntity.class);
        verify(outboxEventMapper).insert(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo("1");
    }

    @Test
    void consumeRootCreatedEventRefreshesHotAndTimeCaches() {
        ContentCommentEntity root = rootComment(1001L, 10003L, 2, 1, "2026-06-11T10:01:00");
        when(idGenerator.nextId()).thenReturn(9001L);
        when(contentCommentMapper.selectByCommentId(1001L)).thenReturn(root);

        service.consumeEvent(commentEvent("evt-root-created", "CommentCreated", Map.of(
                "commentId", "1001",
                "noteId", String.valueOf(NOTE_ID),
                "rootId", "1001",
                "level", 1
        )));

        verify(consumeRecordMapper).insertIgnore(any(CommentConsumeRecordEntity.class));
        verify(commentRedisStore).updateHotComment(NOTE_ID, 1001L, 14D, 1000);
        verify(commentRedisStore).updateRootTimeComment(NOTE_ID, 1001L, root.getCreatedAt());
        verify(consumeRecordMapper).markSuccess("bluenote-comment-hot-consumer", "evt-root-created");
    }

    @Test
    void consumeReplyCreatedEventAddsReplyAndRefreshesRootHot() {
        ContentCommentEntity reply = replyComment(2002L, 10002L, 1001L);
        ContentCommentEntity root = rootComment(1001L, 10003L, 2, 2, "2026-06-11T10:01:00");
        when(idGenerator.nextId()).thenReturn(9002L);
        when(contentCommentMapper.selectByCommentId(2002L)).thenReturn(reply);
        when(contentCommentMapper.selectByCommentId(1001L)).thenReturn(root);

        service.consumeEvent(commentEvent("evt-reply-created", "CommentCreated", Map.of(
                "commentId", "2002",
                "noteId", String.valueOf(NOTE_ID),
                "rootId", "1001",
                "level", 2
        )));

        verify(commentRedisStore).updateReplyComment(1001L, 2002L, reply.getCreatedAt());
        verify(commentRedisStore).updateHotComment(NOTE_ID, 1001L, 20D, 1000);
        verify(commentRedisStore).updateRootTimeComment(NOTE_ID, 1001L, root.getCreatedAt());
    }

    @Test
    void consumeRootDeleteEventRemovesHotTimeAndReplies() {
        when(idGenerator.nextId()).thenReturn(9003L);

        service.consumeEvent(commentEvent("evt-root-deleted", "CommentDeleted", Map.of(
                "commentId", "1001",
                "noteId", String.valueOf(NOTE_ID),
                "rootId", "1001",
                "level", 1
        )));

        verify(commentRedisStore).removeHotComment(NOTE_ID, 1001L);
        verify(commentRedisStore).removeRootTimeComment(NOTE_ID, 1001L);
        verify(commentRedisStore).removeReplyList(1001L);
    }

    @Test
    void timeDescCommentsCanUseRedisTimeList() {
        ContentCommentEntity newest = rootComment(1003L, 10004L, 0, 0, "2026-06-11T10:03:00");
        ContentCommentEntity older = rootComment(1002L, 10003L, 0, 0, "2026-06-11T10:02:00");
        ContentCommentEntity oldest = rootComment(1001L, 10002L, 0, 0, "2026-06-11T10:01:00");
        when(commentRedisStore.rootTimeComments(NOTE_ID, true, 23)).thenReturn(List.of(1003L, 1002L, 1001L));
        when(contentCommentMapper.selectVisibleRootsByCommentIds(List.of(1003L, 1002L, 1001L)))
                .thenReturn(List.of(older, newest, oldest));
        when(commentContentMapper.selectByCommentIds(List.of(1003L, 1002L)))
                .thenReturn(List.of(content(1003L, "newest"), content(1002L, "older")));
        when(commentLikeMapper.selectActiveByComments(10001L, List.of(1003L, 1002L))).thenReturn(List.of());

        CommentCursorPage<CommentItemResponse> page = service.noteComments(
                String.valueOf(NOTE_ID),
                "TIME_DESC",
                null,
                2,
                VIEWER_ID
        );

        assertThat(page.items()).extracting(CommentItemResponse::commentId)
                .containsExactly("1003", "1002");
        verify(contentCommentMapper, never()).selectRootPage(eq(NOTE_ID), eq("TIME_DESC"), any(), any(), any(), any(Integer.class));
    }

    private ContentCommentEntity rootComment(Long commentId, Long userId, long likeCount, long replyCount, String createdAt) {
        ContentCommentEntity entity = new ContentCommentEntity();
        entity.setCommentId(commentId);
        entity.setNoteId(NOTE_ID);
        entity.setNoteAuthorId(20001L);
        entity.setUserId(userId);
        entity.setRootId(commentId);
        entity.setLevel(1);
        entity.setCommentStatus("VISIBLE");
        entity.setLikeCountSnapshot(likeCount);
        entity.setReplyCountSnapshot(replyCount);
        entity.setHotScoreSnapshot(likeCount * 4 + replyCount * 6);
        entity.setCreatedAt(LocalDateTime.parse(createdAt));
        entity.setUpdatedAt(LocalDateTime.parse(createdAt));
        return entity;
    }

    private ContentCommentEntity replyComment(Long commentId, Long userId, Long rootId) {
        ContentCommentEntity entity = new ContentCommentEntity();
        entity.setCommentId(commentId);
        entity.setNoteId(NOTE_ID);
        entity.setNoteAuthorId(20001L);
        entity.setUserId(userId);
        entity.setRootId(rootId);
        entity.setParentCommentId(rootId);
        entity.setLevel(2);
        entity.setCommentStatus("VISIBLE");
        entity.setLikeCountSnapshot(0L);
        entity.setReplyCountSnapshot(0L);
        entity.setHotScoreSnapshot(0L);
        entity.setCreatedAt(LocalDateTime.parse("2026-06-11T10:04:00"));
        entity.setUpdatedAt(LocalDateTime.parse("2026-06-11T10:04:00"));
        return entity;
    }

    private CommentContentEntity content(Long commentId, String value) {
        CommentContentEntity entity = new CommentContentEntity();
        entity.setCommentId(commentId);
        entity.setContent(value);
        entity.setContentPreview(value);
        entity.setAuditStatus("SKIPPED");
        return entity;
    }

    private CommentConsumeEventRequest commentEvent(String eventId, String eventType, Map<String, Object> payload) {
        return new CommentConsumeEventRequest(
                "comment-event",
                "bluenote-comment-hot-consumer",
                eventId,
                eventType,
                1,
                "2026-06-11T10:01:00+08:00",
                "trace-id",
                "bluenote-comment",
                String.valueOf(payload.get("commentId")),
                payload
        );
    }
}
