package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteVersionEntity;
import org.apache.ibatis.annotations.Param;

public interface NoteVersionMapper {

    int insert(NoteVersionEntity entity);

    NoteVersionEntity selectByNoteAndVersion(
            @Param("noteId") Long noteId,
            @Param("versionNo") Integer versionNo
    );

    int markActive(
            @Param("noteId") Long noteId,
            @Param("versionNo") Integer versionNo
    );
}

