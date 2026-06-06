package com.bluenote.content.file.infrastructure.mapper;

import com.bluenote.content.file.infrastructure.entity.FileBindingEntity;
import org.apache.ibatis.annotations.Param;

public interface FileBindingMapper {

    int insert(FileBindingEntity entity);

    FileBindingEntity selectBinding(
            @Param("fileId") Long fileId,
            @Param("bindType") String bindType,
            @Param("bindId") String bindId
    );

    int markBound(
            @Param("fileId") Long fileId,
            @Param("bindType") String bindType,
            @Param("bindId") String bindId,
            @Param("entity") FileBindingEntity entity
    );
}

