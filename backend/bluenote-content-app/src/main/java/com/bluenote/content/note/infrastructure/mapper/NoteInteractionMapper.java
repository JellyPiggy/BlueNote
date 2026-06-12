package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteCollectionEntity;
import com.bluenote.content.note.infrastructure.entity.NoteLikeEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface NoteInteractionMapper {

    int insertLike(NoteLikeEntity entity);

    NoteLikeEntity selectLikeByPair(@Param("noteId") Long noteId, @Param("userId") Long userId);

    int activateLike(
            @Param("id") Long id,
            @Param("likedAt") LocalDateTime likedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int cancelLike(
            @Param("id") Long id,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int insertCollection(NoteCollectionEntity entity);

    NoteCollectionEntity selectCollectionByPair(@Param("noteId") Long noteId, @Param("userId") Long userId);

    int activateCollection(
            @Param("id") Long id,
            @Param("collectedAt") LocalDateTime collectedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int cancelCollection(
            @Param("id") Long id,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    long countLikes(@Param("noteId") Long noteId);

    long countCollections(@Param("noteId") Long noteId);

    long countActiveLikesByAuthor(@Param("authorId") Long authorId);

    boolean likedByViewer(@Param("noteId") Long noteId, @Param("userId") Long userId);

    boolean collectedByViewer(@Param("noteId") Long noteId, @Param("userId") Long userId);
}
