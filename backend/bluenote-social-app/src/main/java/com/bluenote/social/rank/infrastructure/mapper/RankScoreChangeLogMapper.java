package com.bluenote.social.rank.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

public interface RankScoreChangeLogMapper {

    int insertIgnore(
            @Param("changeId") Long changeId,
            @Param("sourceEventId") String sourceEventId,
            @Param("sourceEventType") String sourceEventType,
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("memberType") String memberType,
            @Param("memberId") Long memberId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId,
            @Param("fieldName") String fieldName,
            @Param("deltaValue") Long deltaValue,
            @Param("scoreDelta") Long scoreDelta,
            @Param("scoreAfter") Long scoreAfter,
            @Param("occurredAt") java.time.LocalDateTime occurredAt,
            @Param("createdAt") java.time.LocalDateTime createdAt
    );
}
