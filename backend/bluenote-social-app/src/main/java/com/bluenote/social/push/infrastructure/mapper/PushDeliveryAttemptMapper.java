package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushDeliveryAttemptEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PushDeliveryAttemptMapper {

    int insert(PushDeliveryAttemptEntity entity);

    int markAcked(
            @Param("requestId") String requestId,
            @Param("deviceId") String deviceId,
            @Param("ackedAt") LocalDateTime ackedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<PushDeliveryAttemptEntity> selectByRequestId(@Param("requestId") String requestId);
}
