package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ImMessageMapper {

    int insert(ImMessageEntity entity);

    ImMessageEntity selectById(@Param("messageId") Long messageId);

    ImMessageEntity selectBySenderClientMsg(
            @Param("senderId") Long senderId,
            @Param("clientMsgId") String clientMsgId
    );

    List<ImMessageEntity> selectByIds(@Param("messageIds") List<Long> messageIds);
}
