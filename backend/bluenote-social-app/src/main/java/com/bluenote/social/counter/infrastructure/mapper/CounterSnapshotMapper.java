package com.bluenote.social.counter.infrastructure.mapper;

import com.bluenote.social.counter.infrastructure.entity.CounterSnapshotEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CounterSnapshotMapper {

    int insertIgnore(CounterSnapshotEntity entity);

    int upsert(CounterSnapshotEntity entity);

    int increment(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("counterField") String counterField,
            @Param("deltaValue") Long deltaValue,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<CounterSnapshotEntity> selectByTargetAndFields(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("fields") List<String> fields
    );
}
