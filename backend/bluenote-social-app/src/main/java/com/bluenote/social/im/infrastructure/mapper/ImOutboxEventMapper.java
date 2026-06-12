package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImOutboxEventEntity;

public interface ImOutboxEventMapper {

    int insert(ImOutboxEventEntity entity);
}
