package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankMemberScoreEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RankMemberScoreMapper {

    int insertIgnore(RankMemberScoreEntity entity);

    RankMemberScoreEntity selectForUpdate(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("memberType") String memberType,
            @Param("memberId") Long memberId
    );

    RankMemberScoreEntity selectOne(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("memberType") String memberType,
            @Param("memberId") Long memberId
    );

    int updateScore(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("memberType") String memberType,
            @Param("memberId") Long memberId,
            @Param("score") Long score,
            @Param("rankScore") Double rankScore,
            @Param("memberStatus") String memberStatus,
            @Param("lastScoreAt") LocalDateTime lastScoreAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<RankMemberScoreEntity> selectTop(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("limit") int limit
    );

    List<RankMemberScoreEntity> selectActiveByRank(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("limit") int limit
    );

    int countHigherScore(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("score") Long score
    );
}
