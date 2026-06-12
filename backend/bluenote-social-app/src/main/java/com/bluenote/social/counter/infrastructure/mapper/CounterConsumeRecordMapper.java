package com.bluenote.social.counter.infrastructure.mapper;

import com.bluenote.social.counter.infrastructure.entity.CounterConsumeRecordEntity;
import org.apache.ibatis.annotations.Param;

public interface CounterConsumeRecordMapper {

    int insertIgnore(CounterConsumeRecordEntity entity);

    CounterConsumeRecordEntity selectByGroupAndEvent(
            @Param("consumerGroup") String consumerGroup,
            @Param("eventId") String eventId
    );

    int markSuccess(@Param("consumerGroup") String consumerGroup, @Param("eventId") String eventId);

    int markFail(
            @Param("consumerGroup") String consumerGroup,
            @Param("eventId") String eventId,
            @Param("errorMessage") String errorMessage
    );
}
