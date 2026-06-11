package com.bluenote.social.relation.infrastructure.mapper;

import com.bluenote.social.relation.infrastructure.entity.RelationFollowerEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RelationFollowerMapper {

    int upsert(RelationFollowerEntity entity);

    List<RelationFollowerEntity> selectFollowersPage(
            @Param("followeeId") Long followeeId,
            @Param("cursorFollowedAt") LocalDateTime cursorFollowedAt,
            @Param("cursorFollowerId") Long cursorFollowerId,
            @Param("size") int size
    );

    long countActiveByFollowee(@Param("followeeId") Long followeeId);
}
