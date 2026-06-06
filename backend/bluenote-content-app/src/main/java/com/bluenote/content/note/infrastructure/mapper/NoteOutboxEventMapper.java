package com.bluenote.content.note.infrastructure.mapper;

import com.bluenote.content.note.infrastructure.entity.NoteOutboxEventEntity;

public interface NoteOutboxEventMapper {

    int insert(NoteOutboxEventEntity entity);
}

