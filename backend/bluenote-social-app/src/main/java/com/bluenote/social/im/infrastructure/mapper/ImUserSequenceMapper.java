package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImUserSequenceEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface ImUserSequenceMapper {

    int insertIgnore(ImUserSequenceEntity entity);

    ImUserSequenceEntity selectForUpdate(@Param("userId") Long userId);

    int updateCurrentSeq(
            @Param("userId") Long userId,
            @Param("currentSeq") Long currentSeq,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
