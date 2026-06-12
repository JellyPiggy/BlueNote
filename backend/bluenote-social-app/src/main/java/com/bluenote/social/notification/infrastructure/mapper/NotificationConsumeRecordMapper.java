package com.bluenote.social.notification.infrastructure.mapper;

import com.bluenote.social.notification.infrastructure.entity.NotificationConsumeRecordEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface NotificationConsumeRecordMapper {

    int insert(NotificationConsumeRecordEntity entity);

    NotificationConsumeRecordEntity selectByGroupAndEvent(
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
