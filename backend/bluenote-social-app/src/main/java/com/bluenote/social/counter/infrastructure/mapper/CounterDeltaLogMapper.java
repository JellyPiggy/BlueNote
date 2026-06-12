package com.bluenote.social.counter.infrastructure.mapper;

import com.bluenote.social.counter.infrastructure.entity.CounterDeltaLogEntity;
import org.apache.ibatis.annotations.Param;

public interface CounterDeltaLogMapper {

    int insertIgnore(CounterDeltaLogEntity entity);

    CounterDeltaLogEntity selectByDeltaId(@Param("deltaId") String deltaId);

    CounterDeltaLogEntity selectByDeltaIdForUpdate(@Param("deltaId") String deltaId);

    int markApplied(@Param("deltaId") String deltaId);

    int markFailed(@Param("deltaId") String deltaId);
}
