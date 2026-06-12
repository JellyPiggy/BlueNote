package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankRebuildTaskEntity;
import org.apache.ibatis.annotations.Param;

public interface RankRebuildTaskMapper {

    int insert(RankRebuildTaskEntity entity);

    RankRebuildTaskEntity selectByTaskId(@Param("taskId") String taskId);

    int markRunning(@Param("taskId") String taskId);

    int markSuccess(@Param("taskId") String taskId, @Param("progressJson") String progressJson);

    int markFailed(
            @Param("taskId") String taskId,
            @Param("progressJson") String progressJson,
            @Param("errorMessage") String errorMessage
    );
}
