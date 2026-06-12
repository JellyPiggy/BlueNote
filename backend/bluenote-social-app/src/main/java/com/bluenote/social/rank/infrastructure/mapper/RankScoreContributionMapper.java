package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankScoreContributionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RankScoreContributionMapper {

    int insertIgnore(RankScoreContributionEntity entity);

    RankScoreContributionEntity selectForUpdate(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("memberType") String memberType,
            @Param("memberId") Long memberId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId
    );

    int updateScore(RankScoreContributionEntity entity);

    List<RankScoreContributionEntity> selectBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId
    );
}
