package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushDeliveryRequestEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface PushDeliveryRequestMapper {

    int insertIgnore(PushDeliveryRequestEntity entity);

    PushDeliveryRequestEntity selectByRequestId(@Param("requestId") String requestId);

    PushDeliveryRequestEntity selectBySourceBiz(
            @Param("sourceService") String sourceService,
            @Param("sourceBizType") String sourceBizType,
            @Param("sourceBizId") String sourceBizId,
            @Param("scene") String scene
    );

    PushDeliveryRequestEntity selectByRequestIdForUpdate(@Param("requestId") String requestId);

    int updateStatus(
            @Param("requestId") String requestId,
            @Param("requestStatus") String requestStatus,
            @Param("filteredReason") String filteredReason,
            @Param("deliveredDeviceCount") int deliveredDeviceCount,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
