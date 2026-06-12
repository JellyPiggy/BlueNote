package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankPeriodEntity;
import org.apache.ibatis.annotations.Param;

public interface RankPeriodMapper {

    int insertIgnore(RankPeriodEntity entity);

    RankPeriodEntity selectByRankAndPeriod(@Param("rankCode") String rankCode, @Param("periodId") String periodId);
}
