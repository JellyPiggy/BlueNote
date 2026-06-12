package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushOutboxEventEntity;

public interface PushOutboxEventMapper {

    int insert(PushOutboxEventEntity entity);
}
