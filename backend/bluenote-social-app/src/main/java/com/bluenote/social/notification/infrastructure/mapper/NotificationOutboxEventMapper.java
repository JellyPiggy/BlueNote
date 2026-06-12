package com.bluenote.social.notification.infrastructure.mapper;

import com.bluenote.social.notification.infrastructure.entity.NotificationOutboxEventEntity;

public interface NotificationOutboxEventMapper {

    int insert(NotificationOutboxEventEntity entity);
}
