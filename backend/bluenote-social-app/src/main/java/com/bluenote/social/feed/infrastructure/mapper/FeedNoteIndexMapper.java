package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedNoteIndexEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FeedNoteIndexMapper {

    int upsert(FeedNoteIndexEntity entity);

    FeedNoteIndexEntity selectByNoteId(@Param("noteId") Long noteId);

    List<FeedNoteIndexEntity> selectByNoteIds(@Param("noteIds") List<Long> noteIds);

    List<FeedNoteIndexEntity> selectRecentVisibleByAuthor(
            @Param("authorId") Long authorId,
            @Param("limit") int limit
    );

    List<FeedNoteIndexEntity> selectVisibleByAuthors(
            @Param("authorIds") List<Long> authorIds,
            @Param("limitPerAuthor") int limitPerAuthor
    );

    int markStatus(
            @Param("noteId") Long noteId,
            @Param("visibility") String visibility,
            @Param("noteStatus") String noteStatus,
            @Param("itemStatus") String itemStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
