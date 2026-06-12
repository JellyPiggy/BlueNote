package com.bluenote.social.im.infrastructure.mapper;

import com.bluenote.social.im.infrastructure.entity.ImConversationMemberEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ImConversationMemberMapper {

    int insertIgnore(ImConversationMemberEntity entity);

    ImConversationMemberEntity selectByConversationAndUser(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId
    );

    List<ImConversationMemberEntity> selectByConversation(@Param("conversationId") Long conversationId);

    List<ImConversationMemberEntity> selectUserPage(
            @Param("userId") Long userId,
            @Param("cursorLastMessageAt") LocalDateTime cursorLastMessageAt,
            @Param("cursorConversationId") Long cursorConversationId,
            @Param("size") int size
    );

    int restoreVisible(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markSenderAfterSend(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("seq") Long seq,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markReceiverAfterSend(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateReceived(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("seq") Long seq,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateRead(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("seq") Long seq,
            @Param("unreadCount") int unreadCount,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateSettings(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("pinned") Integer pinned,
            @Param("mute") Integer mute,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int hideConversation(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("lastVisibleSeq") Long lastVisibleSeq,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    Long sumUnread(@Param("userId") Long userId);
}
