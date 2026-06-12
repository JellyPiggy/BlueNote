package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushDeviceEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PushDeviceMapper {

    int upsert(PushDeviceEntity entity);

    PushDeviceEntity selectByDeviceId(@Param("deviceId") String deviceId);

    List<PushDeviceEntity> selectByUser(@Param("userId") Long userId);

    List<PushDeviceEntity> selectActiveByUser(@Param("userId") Long userId);

    int unbind(
            @Param("deviceId") String deviceId,
            @Param("userId") Long userId,
            @Param("unboundAt") LocalDateTime unboundAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
