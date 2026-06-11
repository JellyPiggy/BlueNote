package com.bluenote.social.relation.infrastructure.mapper;

import com.bluenote.social.relation.infrastructure.entity.RelationOutboxEventEntity;

public interface RelationOutboxEventMapper {

    int insert(RelationOutboxEventEntity entity);
}
