package com.bluenote.content.file.infrastructure.mapper;

import com.bluenote.content.file.infrastructure.entity.FileUploadSessionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface FileUploadSessionMapper {

    int insert(FileUploadSessionEntity entity);

    FileUploadSessionEntity selectLatestByFileId(@Param("fileId") Long fileId);

    int markUploaded(
            @Param("uploadId") Long uploadId,
            @Param("confirmedAt") LocalDateTime confirmedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markExpired(
            @Param("uploadId") Long uploadId,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}

