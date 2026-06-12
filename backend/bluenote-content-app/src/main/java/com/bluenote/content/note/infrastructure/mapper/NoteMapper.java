package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NoteMapper {

    int insert(NoteEntity entity);

    NoteEntity selectByNoteId(@Param("noteId") Long noteId);

    List<NoteEntity> selectByNoteIds(@Param("noteIds") List<Long> noteIds);

    int publishDraft(
            @Param("noteId") Long noteId,
            @Param("authorId") Long authorId,
            @Param("noteStatus") String noteStatus,
            @Param("visibility") String visibility,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markDeleted(
            @Param("noteId") Long noteId,
            @Param("authorId") Long authorId,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<NoteEntity> selectAuthorPublishedPage(
            @Param("authorId") Long authorId,
            @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
            @Param("cursorNoteId") Long cursorNoteId,
            @Param("size") int size
    );

    List<NoteEntity> selectMyNotesPage(
            @Param("authorId") Long authorId,
            @Param("status") String status,
            @Param("cursorSortAt") LocalDateTime cursorSortAt,
            @Param("cursorNoteId") Long cursorNoteId,
            @Param("size") int size
    );

    List<NoteEntity> selectRecentPublicByAuthors(
            @Param("authorIds") List<Long> authorIds,
            @Param("publishedAfter") LocalDateTime publishedAfter,
            @Param("size") int size
    );

    long countPublicPublishedByAuthor(@Param("authorId") Long authorId);
}
