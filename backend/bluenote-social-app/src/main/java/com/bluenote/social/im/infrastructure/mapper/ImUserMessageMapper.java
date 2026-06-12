package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImUserMessageEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface ImUserMessageMapper {

    int insert(ImUserMessageEntity entity);

    int markReceived(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("conversationSeq") Long conversationSeq,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markRead(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("conversationSeq") Long conversationSeq,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int countUnreadAfter(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("conversationSeq") Long conversationSeq
    );
}
