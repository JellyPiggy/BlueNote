package com.bluenote.social.notification.infrastructure.mapper;

import com.bluenote.social.notification.infrastructure.entity.NotificationAggregateActorEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NotificationAggregateActorMapper {

    int insertIgnore(NotificationAggregateActorEntity entity);

    int countActors(@Param("notificationId") Long notificationId);

    List<NotificationAggregateActorEntity> selectLatestActors(
            @Param("notificationId") Long notificationId,
            @Param("size") int size
    );
}
