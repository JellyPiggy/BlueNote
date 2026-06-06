package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteTopicEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NoteTopicMapper {

    int insert(NoteTopicEntity entity);

    List<NoteTopicEntity> selectByNoteAndVersion(
            @Param("noteId") Long noteId,
            @Param("versionNo") Integer versionNo
    );
}

