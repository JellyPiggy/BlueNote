package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.ContentCommentEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ContentCommentMapper {

    int insert(ContentCommentEntity entity);

    ContentCommentEntity selectByCommentId(@Param("commentId") Long commentId);

    List<ContentCommentEntity> selectByCommentIds(@Param("commentIds") List<Long> commentIds);

    List<ContentCommentEntity> selectVisibleRootsByCommentIds(@Param("commentIds") List<Long> commentIds);

    List<ContentCommentEntity> selectRootPage(
            @Param("noteId") Long noteId,
            @Param("sort") String sort,
            @Param("cursorHotScore") Long cursorHotScore,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorCommentId") Long cursorCommentId,
            @Param("size") int size
    );

    List<ContentCommentEntity> selectReplyPage(
            @Param("rootId") Long rootId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorCommentId") Long cursorCommentId,
            @Param("size") int size
    );

    int markDeleted(@Param("commentId") Long commentId, @Param("updatedAt") LocalDateTime updatedAt);

    int markDeletedByRoot(@Param("rootId") Long rootId, @Param("updatedAt") LocalDateTime updatedAt);

    int incrementReplySnapshot(
            @Param("rootId") Long rootId,
            @Param("delta") long delta,
            @Param("hotDelta") long hotDelta,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int incrementLikeSnapshot(
            @Param("commentId") Long commentId,
            @Param("delta") long delta,
            @Param("hotDelta") long hotDelta,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    long countVisibleByNote(@Param("noteId") Long noteId);

    long countVisibleByRoot(@Param("rootId") Long rootId);

    long countVisibleReplies(@Param("rootId") Long rootId);

    List<ContentCommentEntity> selectHotRootCandidates(@Param("noteId") Long noteId, @Param("size") int size);
}
