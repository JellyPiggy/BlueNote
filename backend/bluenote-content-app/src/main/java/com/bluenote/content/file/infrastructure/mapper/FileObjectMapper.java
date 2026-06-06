package com.bluenote.content.file.infrastructure.mapper;

import com.bluenote.content.file.infrastructure.entity.FileObjectEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FileObjectMapper {

    int insert(FileObjectEntity entity);

    FileObjectEntity selectByFileId(@Param("fileId") Long fileId);

    List<FileObjectEntity> selectByFileIds(@Param("fileIds") List<Long> fileIds);

    int markUploaded(
            @Param("fileId") Long fileId,
            @Param("etag") String etag,
            @Param("fileSize") Long fileSize,
            @Param("uploadedAt") LocalDateTime uploadedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markBound(
            @Param("fileId") Long fileId,
            @Param("boundAt") LocalDateTime boundAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}

