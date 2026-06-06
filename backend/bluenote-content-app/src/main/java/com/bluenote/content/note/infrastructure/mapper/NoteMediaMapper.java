package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteMediaEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NoteMediaMapper {

    int insert(NoteMediaEntity entity);

    List<NoteMediaEntity> selectByNoteAndVersion(
            @Param("noteId") Long noteId,
            @Param("versionNo") Integer versionNo
    );
}

