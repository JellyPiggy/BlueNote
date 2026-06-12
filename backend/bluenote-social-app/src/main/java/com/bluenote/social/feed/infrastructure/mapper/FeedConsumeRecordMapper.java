package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedConsumeRecordEntity;
import org.apache.ibatis.annotations.Param;

public interface FeedConsumeRecordMapper {

    int insertIgnore(FeedConsumeRecordEntity entity);

    FeedConsumeRecordEntity selectByGroupAndEvent(
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
