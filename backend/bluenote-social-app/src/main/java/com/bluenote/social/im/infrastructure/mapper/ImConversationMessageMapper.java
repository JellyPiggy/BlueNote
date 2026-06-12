package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImConversationMessageEntity;
import com.bluenote.social.im.infrastructure.entity.ImMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ImConversationMessageMapper {

    int insert(ImConversationMessageEntity entity);

    List<ImMessageEntity> selectAfter(
            @Param("conversationId") Long conversationId,
            @Param("afterSeq") Long afterSeq,
            @Param("lastVisibleSeq") Long lastVisibleSeq,
            @Param("limit") int limit
    );

    List<ImMessageEntity> selectBefore(
            @Param("conversationId") Long conversationId,
            @Param("beforeSeq") Long beforeSeq,
            @Param("lastVisibleSeq") Long lastVisibleSeq,
            @Param("limit") int limit
    );

    List<ImMessageEntity> selectLatest(
            @Param("conversationId") Long conversationId,
            @Param("lastVisibleSeq") Long lastVisibleSeq,
            @Param("limit") int limit
    );
}
