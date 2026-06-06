package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteIdempotentRequestEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface NoteIdempotentRequestMapper {

    int insert(NoteIdempotentRequestEntity entity);

    NoteIdempotentRequestEntity selectByKey(@Param("idempotentKey") String idempotentKey);

    int markSuccess(
            @Param("idempotentKey") String idempotentKey,
            @Param("bizId") Long bizId,
            @Param("responsePayload") String responsePayload,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}

