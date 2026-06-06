package com.bluenote.member.auth.infrastructure.mapper;

import com.bluenote.member.auth.infrastructure.entity.AuthSessionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface AuthSessionMapper {

    int insert(AuthSessionEntity entity);

    AuthSessionEntity selectByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);

    int revokeActiveByUserDevice(
            @Param("userId") Long userId,
            @Param("deviceId") String deviceId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );

    int revokeByRefreshTokenHash(
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );

    int updateStatus(
            @Param("sessionId") Long sessionId,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );

    int rotateRefreshToken(
            @Param("sessionId") Long sessionId,
            @Param("oldRefreshTokenHash") String oldRefreshTokenHash,
            @Param("newRefreshTokenHash") String newRefreshTokenHash,
            @Param("refreshTokenExpiresAt") LocalDateTime refreshTokenExpiresAt,
            @Param("now") LocalDateTime now
    );
}
