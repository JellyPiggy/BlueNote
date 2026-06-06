package com.bluenote.content.note.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

public interface NoteInteractionMapper {

    long countLikes(@Param("noteId") Long noteId);

    long countCollections(@Param("noteId") Long noteId);

    boolean likedByViewer(@Param("noteId") Long noteId, @Param("userId") Long userId);

    boolean collectedByViewer(@Param("noteId") Long noteId, @Param("userId") Long userId);
}

