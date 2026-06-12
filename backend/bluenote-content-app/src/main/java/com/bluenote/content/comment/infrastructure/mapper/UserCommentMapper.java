package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.UserCommentEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserCommentMapper {

    int insert(UserCommentEntity entity);

    int markDeleted(@Param("commentId") Long commentId, @Param("updatedAt") LocalDateTime updatedAt);

    int markDeletedByRoot(@Param("rootId") Long rootId, @Param("updatedAt") LocalDateTime updatedAt);

    List<UserCommentEntity> selectByUserPage(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorCommentId") Long cursorCommentId,
            @Param("size") int size
    );
}
