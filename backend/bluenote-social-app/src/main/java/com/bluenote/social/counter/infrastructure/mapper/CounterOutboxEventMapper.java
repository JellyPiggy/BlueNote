package com.bluenote.social.counter.infrastructure.mapper;

import com.bluenote.social.counter.infrastructure.entity.CounterOutboxEventEntity;

public interface CounterOutboxEventMapper {

    int insert(CounterOutboxEventEntity entity);
}
