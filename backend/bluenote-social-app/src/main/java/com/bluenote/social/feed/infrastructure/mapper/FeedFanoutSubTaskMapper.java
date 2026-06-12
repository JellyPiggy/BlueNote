package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedFanoutSubTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FeedFanoutSubTaskMapper {

    int insertIgnore(FeedFanoutSubTaskEntity entity);

    FeedFanoutSubTaskEntity selectBySubTaskId(@Param("subTaskId") String subTaskId);

    List<FeedFanoutSubTaskEntity> selectRetryableMessages(@Param("now") LocalDateTime now, @Param("size") int size);

    List<FeedFanoutSubTaskEntity> selectByTaskId(@Param("taskId") String taskId);

    int markMessageSent(@Param("subTaskId") String subTaskId);

    int markMessageFailed(@Param("subTaskId") String subTaskId, @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("lastError") String lastError);

    int markRunning(@Param("subTaskId") String subTaskId);

    int markProgress(@Param("subTaskId") String subTaskId, @Param("progressUserId") Long progressUserId);

    int markSuccess(@Param("subTaskId") String subTaskId);

    int markFailed(@Param("subTaskId") String subTaskId, @Param("lastError") String lastError);
}
