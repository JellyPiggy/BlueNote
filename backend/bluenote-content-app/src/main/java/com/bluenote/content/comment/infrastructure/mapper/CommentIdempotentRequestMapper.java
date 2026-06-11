package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.CommentIdempotentRequestEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface CommentIdempotentRequestMapper {

    int insert(CommentIdempotentRequestEntity entity);

    CommentIdempotentRequestEntity selectByKey(@Param("idempotentKey") String idempotentKey);

    int markSuccess(
            @Param("idempotentKey") String idempotentKey,
            @Param("bizId") Long bizId,
            @Param("responsePayload") String responsePayload,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
