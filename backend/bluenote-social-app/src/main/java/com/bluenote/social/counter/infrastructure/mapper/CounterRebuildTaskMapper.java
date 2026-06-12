package com.bluenote.social.counter.infrastructure.mapper;

import com.bluenote.social.counter.infrastructure.entity.CounterRebuildTaskEntity;
import org.apache.ibatis.annotations.Param;

public interface CounterRebuildTaskMapper {

    int insert(CounterRebuildTaskEntity entity);

    CounterRebuildTaskEntity selectByTaskId(@Param("taskId") String taskId);

    int markRunning(@Param("taskId") String taskId);

    int markSuccess(
            @Param("taskId") String taskId,
            @Param("progressJson") String progressJson
    );

    int markFailed(
            @Param("taskId") String taskId,
            @Param("progressJson") String progressJson,
            @Param("lastError") String lastError
    );
}
