package com.bluenote.social.relation.infrastructure.mapper;

import com.bluenote.social.relation.infrastructure.entity.RelationFollowingEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RelationFollowingMapper {

    int insert(RelationFollowingEntity entity);

    RelationFollowingEntity selectByPair(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    int activate(
            @Param("id") Long id,
            @Param("followedAt") LocalDateTime followedAt,
            @Param("relationVersion") Long relationVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int cancel(
            @Param("id") Long id,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("relationVersion") Long relationVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<RelationFollowingEntity> selectFollowingPage(
            @Param("followerId") Long followerId,
            @Param("cursorFollowedAt") LocalDateTime cursorFollowedAt,
            @Param("cursorFolloweeId") Long cursorFolloweeId,
            @Param("size") int size
    );

    List<RelationFollowingEntity> selectActiveByTargets(
            @Param("followerId") Long followerId,
            @Param("targetUserIds") List<Long> targetUserIds
    );

    long countActiveByFollower(@Param("followerId") Long followerId);
}
