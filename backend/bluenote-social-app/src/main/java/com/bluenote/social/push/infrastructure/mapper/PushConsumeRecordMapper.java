package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushConsumeRecordEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface PushConsumeRecordMapper {

    int insert(PushConsumeRecordEntity entity);

    PushConsumeRecordEntity selectByGroupAndEvent(
            @Param("consumerGroup") String consumerGroup,
            @Param("eventId") String eventId
    );

    int markSuccess(
            @Param("id") Long id,
            @Param("consumedAt") LocalDateTime consumedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markSkipped(
            @Param("id") Long id,
            @Param("reason") String reason,
            @Param("consumedAt") LocalDateTime consumedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markFail(
            @Param("id") Long id,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
