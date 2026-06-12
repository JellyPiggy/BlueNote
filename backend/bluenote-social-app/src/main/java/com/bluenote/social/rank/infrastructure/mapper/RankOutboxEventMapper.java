package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankOutboxEventEntity;

public interface RankOutboxEventMapper {

    int insert(RankOutboxEventEntity entity);
}
