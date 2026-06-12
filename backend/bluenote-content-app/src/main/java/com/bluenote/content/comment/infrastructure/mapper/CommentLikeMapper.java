package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.CommentLikeEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CommentLikeMapper {

    int insert(CommentLikeEntity entity);

    CommentLikeEntity selectByPair(@Param("commentId") Long commentId, @Param("userId") Long userId);

    int activate(
            @Param("id") Long id,
            @Param("likedAt") LocalDateTime likedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int cancel(
            @Param("id") Long id,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<CommentLikeEntity> selectActiveByComments(
            @Param("userId") Long userId,
            @Param("commentIds") List<Long> commentIds
    );

    long countActiveByComment(@Param("commentId") Long commentId);
}
