package com.bluenote.member.auth.infrastructure.mapper;

import com.bluenote.member.auth.infrastructure.entity.AuthLoginAuditEntity;

public interface AuthLoginAuditMapper {

    int insert(AuthLoginAuditEntity entity);
}
