package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankNoteIndexEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RankNoteIndexMapper {

    int upsert(RankNoteIndexEntity entity);

    RankNoteIndexEntity selectByNoteId(@Param("noteId") Long noteId);

    int markStatus(
            @Param("noteId") Long noteId,
            @Param("visibility") String visibility,
            @Param("noteStatus") String noteStatus,
            @Param("eligibleStatus") String eligibleStatus,
            @Param("lastEventId") String lastEventId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<RankNoteIndexEntity> selectEligibleByPublishedYear(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("limit") int limit
    );
}
