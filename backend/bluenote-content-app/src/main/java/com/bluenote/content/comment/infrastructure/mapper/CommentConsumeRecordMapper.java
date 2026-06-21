package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.CommentConsumeRecordEntity;
import org.apache.ibatis.annotations.Param;

public interface CommentConsumeRecordMapper {

    int insertIgnore(CommentConsumeRecordEntity entity);

    CommentConsumeRecordEntity selectByGroupAndEvent(
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
