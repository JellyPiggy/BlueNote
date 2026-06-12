package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedRebuildTaskEntity;
import org.apache.ibatis.annotations.Param;

public interface FeedRebuildTaskMapper {

    int insert(FeedRebuildTaskEntity entity);

    FeedRebuildTaskEntity selectByTaskId(@Param("taskId") String taskId);

    int markRunning(@Param("taskId") String taskId);

    int markSuccess(@Param("taskId") String taskId, @Param("progressJson") String progressJson);

    int markFailed(@Param("taskId") String taskId, @Param("progressJson") String progressJson, @Param("lastError") String lastError);
}
