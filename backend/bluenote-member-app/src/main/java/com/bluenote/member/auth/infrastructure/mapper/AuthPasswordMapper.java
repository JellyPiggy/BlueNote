package com.bluenote.member.auth.infrastructure.mapper;

import com.bluenote.member.auth.infrastructure.entity.AuthPasswordEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface AuthPasswordMapper {

    int insert(AuthPasswordEntity entity);

    AuthPasswordEntity selectByUserId(@Param("userId") Long userId);

    int updatePassword(
            @Param("userId") Long userId,
            @Param("passwordHash") String passwordHash,
            @Param("passwordVersion") Integer passwordVersion,
            @Param("now") LocalDateTime now
    );
}
