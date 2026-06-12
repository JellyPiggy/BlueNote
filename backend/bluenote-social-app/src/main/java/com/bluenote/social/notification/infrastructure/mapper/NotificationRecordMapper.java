package com.bluenote.social.notification.infrastructure.mapper;

import com.bluenote.social.notification.infrastructure.entity.NotificationRecordEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NotificationRecordMapper {

    int insert(NotificationRecordEntity entity);

    NotificationRecordEntity selectById(@Param("notificationId") Long notificationId);

    NotificationRecordEntity selectUnreadAggregate(
            @Param("receiverId") Long receiverId,
            @Param("aggregateUnreadKey") String aggregateUnreadKey
    );

    NotificationRecordEntity selectByReceiverSource(
            @Param("receiverId") Long receiverId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("notificationType") String notificationType
    );

    List<NotificationRecordEntity> selectPage(
            @Param("receiverId") Long receiverId,
            @Param("category") String category,
            @Param("cursorLastEventAt") LocalDateTime cursorLastEventAt,
            @Param("cursorNotificationId") Long cursorNotificationId,
            @Param("size") int size
    );

    List<NotificationRecordEntity> selectByIds(
            @Param("receiverId") Long receiverId,
            @Param("notificationIds") List<Long> notificationIds
    );

    int markRead(
            @Param("notificationId") Long notificationId,
            @Param("receiverId") Long receiverId,
            @Param("readAt") LocalDateTime readAt
    );

    int markReadAll(
            @Param("receiverId") Long receiverId,
            @Param("category") String category,
            @Param("readAt") LocalDateTime readAt
    );

    int markDeleted(
            @Param("notificationId") Long notificationId,
            @Param("receiverId") Long receiverId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markDeletedBatch(
            @Param("receiverId") Long receiverId,
            @Param("notificationIds") List<Long> notificationIds,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateAggregate(NotificationRecordEntity entity);

    long countUnreadByCategory(
            @Param("receiverId") Long receiverId,
            @Param("category") String category
    );
}
