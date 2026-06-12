package com.bluenote.social.feed.infrastructure.mapper;

import com.bluenote.social.feed.infrastructure.entity.FeedInboxItemEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FeedInboxItemMapper {

    int upsert(FeedInboxItemEntity entity);

    List<FeedInboxItemEntity> selectUserPage(
            @Param("userId") Long userId,
            @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
            @Param("cursorNoteId") Long cursorNoteId,
            @Param("size") int size
    );

    List<FeedInboxItemEntity> selectUserItemsByNoteIds(
            @Param("userId") Long userId,
            @Param("noteIds") List<Long> noteIds
    );

    List<FeedInboxItemEntity> selectUserSnapshot(@Param("userId") Long userId, @Param("size") int size);

    int hideByUserAndAuthor(
            @Param("userId") Long userId,
            @Param("authorId") Long authorId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markByNote(
            @Param("noteId") Long noteId,
            @Param("itemStatus") String itemStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
