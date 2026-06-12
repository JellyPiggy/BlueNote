package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedFanoutTaskEntity;
import org.apache.ibatis.annotations.Param;

public interface FeedFanoutTaskMapper {

    int insertIgnore(FeedFanoutTaskEntity entity);

    FeedFanoutTaskEntity selectByTaskId(@Param("taskId") String taskId);

    int markRunning(@Param("taskId") String taskId);

    int markSuccess(
            @Param("taskId") String taskId,
            @Param("successCount") int successCount,
            @Param("failedCount") int failedCount
    );

    int markFailed(@Param("taskId") String taskId, @Param("lastError") String lastError);
}
