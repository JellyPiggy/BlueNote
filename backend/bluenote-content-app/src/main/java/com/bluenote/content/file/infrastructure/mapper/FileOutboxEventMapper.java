package com.bluenote.content.file.infrastructure.mapper;

import com.bluenote.content.file.infrastructure.entity.FileOutboxEventEntity;

public interface FileOutboxEventMapper {

    int insert(FileOutboxEventEntity entity);
}

