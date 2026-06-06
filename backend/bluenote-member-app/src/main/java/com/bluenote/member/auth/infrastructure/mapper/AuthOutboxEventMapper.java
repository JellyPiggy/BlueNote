package com.bluenote.member.auth.infrastructure.mapper;

import com.bluenote.member.auth.infrastructure.entity.AuthOutboxEventEntity;

public interface AuthOutboxEventMapper {

    int insert(AuthOutboxEventEntity entity);
}
