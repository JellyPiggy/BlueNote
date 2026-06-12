package com.bluenote.social.notification.infrastructure.mapper;

import com.bluenote.social.notification.infrastructure.entity.NotificationUnreadCounterEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NotificationUnreadCounterMapper {

    int upsertDelta(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("delta") long delta,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int upsertValue(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("unreadCount") long unreadCount,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<NotificationUnreadCounterEntity> selectByUser(@Param("userId") Long userId);
}
