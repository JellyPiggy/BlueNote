package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImConversationEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ImConversationMapper {

    int insert(ImConversationEntity entity);

    ImConversationEntity selectById(@Param("conversationId") Long conversationId);

    ImConversationEntity selectByIdForUpdate(@Param("conversationId") Long conversationId);

    ImConversationEntity selectBySingleKey(@Param("singleKey") String singleKey);

    List<ImConversationEntity> selectByIds(@Param("conversationIds") List<Long> conversationIds);

    int updateLastMessage(
            @Param("conversationId") Long conversationId,
            @Param("currentSeq") Long currentSeq,
            @Param("lastMessageId") Long lastMessageId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
