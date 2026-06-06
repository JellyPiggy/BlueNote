package com.bluenote.member.user.infrastructure.mapper;

import com.bluenote.member.user.infrastructure.entity.UserOutboxEventEntity;

public interface UserOutboxEventMapper {

    int insert(UserOutboxEventEntity entity);
}
