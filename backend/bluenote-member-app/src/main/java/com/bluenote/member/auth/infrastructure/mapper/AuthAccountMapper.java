package com.bluenote.member.auth.infrastructure.mapper;

import com.bluenote.member.auth.infrastructure.entity.AuthAccountEntity;
import org.apache.ibatis.annotations.Param;

public interface AuthAccountMapper {

    int insert(AuthAccountEntity entity);

    AuthAccountEntity selectByUserId(@Param("userId") Long userId);

    AuthAccountEntity selectByUsername(@Param("username") String username);
}
