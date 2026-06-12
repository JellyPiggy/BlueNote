package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankConsumeRecordEntity;
import org.apache.ibatis.annotations.Param;

public interface RankConsumeRecordMapper {

    int insertIgnore(RankConsumeRecordEntity entity);

    RankConsumeRecordEntity selectByGroupAndEvent(
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
