package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushDeliveryAttemptEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PushDeliveryAttemptMapper {

    int insert(PushDeliveryAttemptEntity entity);

    List<PushDeliveryAttemptEntity> selectByRequestId(@Param("requestId") String requestId);
}
