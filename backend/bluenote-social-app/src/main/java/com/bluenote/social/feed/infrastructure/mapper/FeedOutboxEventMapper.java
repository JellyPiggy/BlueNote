package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedOutboxEventEntity;

public interface FeedOutboxEventMapper {

    int insert(FeedOutboxEventEntity entity);
}
