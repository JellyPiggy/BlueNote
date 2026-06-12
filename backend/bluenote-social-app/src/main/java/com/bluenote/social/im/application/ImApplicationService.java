package com.bluenote.social.im.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.common.JsonPayloads;
import com.bluenote.social.common.SocialIdGenerator;
import com.bluenote.social.im.api.dto.ImBatchSummaryItem;
import com.bluenote.social.im.api.dto.ImBatchSummaryRequest;
import com.bluenote.social.im.api.dto.ImBatchSummaryResponse;
import com.bluenote.social.im.api.dto.ImConversationDeleteResponse;
import com.bluenote.social.im.api.dto.ImConversationItem;
import com.bluenote.social.im.api.dto.ImConversationListResponse;
import com.bluenote.social.im.api.dto.ImConversationSettingsRequest;
import com.bluenote.social.im.api.dto.ImMessageItem;
import com.bluenote.social.im.api.dto.ImMessageListResponse;
import com.bluenote.social.im.api.dto.ImPushPolicyResponse;
import com.bluenote.social.im.api.dto.ImReadRequest;
import com.bluenote.social.im.api.dto.ImReadResponse;
import com.bluenote.social.im.api.dto.ImRebuildUnreadResponse;
import com.bluenote.social.im.api.dto.ImReceivedRequest;
import com.bluenote.social.im.api.dto.ImReceivedResponse;
import com.bluenote.social.im.api.dto.ImSendMessageRequest;
import com.bluenote.social.im.api.dto.ImSendMessageResponse;
import com.bluenote.social.im.api.dto.ImSingleConversationRequest;
import com.bluenote.social.im.api.dto.ImUnreadCountResponse;
import com.bluenote.social.im.api.dto.ImUserSummary;
import com.bluenote.social.im.infrastructure.client.ImMemberClient;
import com.bluenote.social.im.infrastructure.client.ImMemberClient.UserSummary;
import com.bluenote.social.im.infrastructure.entity.ImConversationEntity;
import com.bluenote.social.im.infrastructure.entity.ImConversationMemberEntity;
import com.bluenote.social.im.infrastructure.entity.ImConversationMessageEntity;
import com.bluenote.social.im.infrastructure.entity.ImMessageEntity;
import com.bluenote.social.im.infrastructure.entity.ImOutboxEventEntity;
import com.bluenote.social.im.infrastructure.entity.ImUserMessageEntity;
import com.bluenote.social.im.infrastructure.entity.ImUserSequenceEntity;
import com.bluenote.social.im.infrastructure.mapper.ImConversationMapper;
import com.bluenote.social.im.infrastructure.mapper.ImConversationMemberMapper;
import com.bluenote.social.im.infrastructure.mapper.ImConversationMessageMapper;
import com.bluenote.social.im.infrastructure.mapper.ImMessageMapper;
import com.bluenote.social.im.infrastructure.mapper.ImOutboxEventMapper;
import com.bluenote.social.im.infrastructure.mapper.ImUserMessageMapper;
import com.bluenote.social.im.infrastructure.mapper.ImUserSequenceMapper;
import com.bluenote.social.im.infrastructure.redis.ImRedisStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImApplicationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_CONVERSATION_SIZE = 20;
    private static final int MAX_CONVERSATION_SIZE = 50;
    private static final int DEFAULT_MESSAGE_LIMIT = 30;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int TEXT_MAX_LENGTH = 1000;
    private static final String CONVERSATION_SINGLE = "SINGLE";
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String MESSAGE_TEXT = "TEXT";
    private static final int UNREAD = 0;
    private static final int READ = 1;
    private static final int NOT_RECEIVED = 0;
    private static final int RECEIVED = 1;

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final ImConversationMessageMapper conversationMessageMapper;
    private final ImUserSequenceMapper userSequenceMapper;
    private final ImUserMessageMapper userMessageMapper;
    private final ImOutboxEventMapper outboxEventMapper;
    private final ImMemberClient memberClient;
    private final ImRedisStore redisStore;
    private final SocialIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final ObjectMapper objectMapper;

    public ImApplicationService(
            ImConversationMapper conversationMapper,
            ImConversationMemberMapper memberMapper,
            ImMessageMapper messageMapper,
            ImConversationMessageMapper conversationMessageMapper,
            ImUserSequenceMapper userSequenceMapper,
            ImUserMessageMapper userMessageMapper,
            ImOutboxEventMapper outboxEventMapper,
            ImMemberClient memberClient,
            ImRedisStore redisStore,
            SocialIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            ObjectMapper objectMapper
    ) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.userSequenceMapper = userSequenceMapper;
        this.userMessageMapper = userMessageMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.memberClient = memberClient;
        this.redisStore = redisStore;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImConversationItem singleConversation(String userId, ImSingleConversationRequest request) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long targetUserId = parseId(request == null ? null : request.targetUserId(), ApiErrorCode.IM_TARGET_INVALID);
        validateTarget(currentUserId, targetUserId);
        ConversationContext context = ensureSingleConversation(currentUserId, targetUserId, now());
        Map<String, UserSummary> users = memberClient.batchSummary(List.of(String.valueOf(targetUserId)));
        return toConversationItem(context.member(), context.conversation(), null, users, currentUserId);
    }

    @Transactional(readOnly = true)
    public ImConversationListResponse conversations(String userId, String cursor, Integer pageSize) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        int size = normalizeConversationSize(pageSize);
        ConversationCursor parsedCursor = parseConversationCursor(cursor);
        List<ImConversationMemberEntity> rows = memberMapper.selectUserPage(
                currentUserId,
                parsedCursor == null ? null : parsedCursor.lastMessageAt(),
                parsedCursor == null ? null : parsedCursor.conversationId(),
                size + 1
        );
        boolean hasMore = rows.size() > size;
        List<ImConversationMemberEntity> page = hasMore ? rows.subList(0, size) : rows;
        Map<Long, ImMessageEntity> messages = lastMessages(page);
        Map<String, UserSummary> users = loadPeerUsers(page);
        List<ImConversationItem> items = page.stream()
                .map(member -> toConversationItem(
                        member,
                        conversationFromMember(member),
                        messages.get(member.getLastMessageId()),
                        users,
                        currentUserId
                ))
                .toList();
        return new ImConversationListResponse(items, nextConversationCursor(page, hasMore), hasMore);
    }

    @Transactional
    public ImSendMessageResponse sendMessage(String userId, ImSendMessageRequest request) {
        Long senderId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        String clientMsgId = normalizeClientMsgId(request == null ? null : request.clientMsgId());
        ImMessageEntity existing = messageMapper.selectBySenderClientMsg(senderId, clientMsgId);
        if (existing != null) {
            return responseForExisting(senderId, existing);
        }

        LocalDateTime now = now();
        MessageContent content = normalizeContent(request);
        ConversationContext context = conversationForSend(senderId, request, now);
        ImConversationEntity locked = conversationMapper.selectByIdForUpdate(context.conversation().getConversationId());
        if (locked == null) {
            throw new BusinessException(ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        }
        Long receiverId = context.peerUserId();
        Long nextConversationSeq = safeLong(locked.getCurrentSeq()) + 1;
        Long senderUserSeq = nextUserSeq(senderId, now);
        Long receiverUserSeq = nextUserSeq(receiverId, now);
        Long messageId = idGenerator.nextId();

        ImMessageEntity message = new ImMessageEntity();
        message.setMessageId(messageId);
        message.setConversationId(locked.getConversationId());
        message.setConversationSeq(nextConversationSeq);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setClientMsgId(clientMsgId);
        message.setMessageType(MESSAGE_TEXT);
        message.setContentJson(json(content.content()));
        message.setSummary(content.summary());
        message.setMessageStatus(STATUS_NORMAL);
        message.setSentAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);

        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            ImMessageEntity duplicate = messageMapper.selectBySenderClientMsg(senderId, clientMsgId);
            if (duplicate != null) {
                return responseForExisting(senderId, duplicate);
            }
            throw exception;
        }

        ImConversationMessageEntity conversationMessage = new ImConversationMessageEntity();
        conversationMessage.setId(idGenerator.nextId());
        conversationMessage.setConversationId(locked.getConversationId());
        conversationMessage.setConversationSeq(nextConversationSeq);
        conversationMessage.setMessageId(messageId);
        conversationMessage.setSenderId(senderId);
        conversationMessage.setSentAt(now);
        conversationMessage.setCreatedAt(now);
        conversationMessageMapper.insert(conversationMessage);

        insertUserMessage(senderId, senderUserSeq, message, READ, RECEIVED, now);
        insertUserMessage(receiverId, receiverUserSeq, message, UNREAD, NOT_RECEIVED, now);

        conversationMapper.updateLastMessage(locked.getConversationId(), nextConversationSeq, messageId, now, now);
        memberMapper.markSenderAfterSend(locked.getConversationId(), senderId, nextConversationSeq, now);
        memberMapper.markReceiverAfterSend(locked.getConversationId(), receiverId, now);

        insertMessageSentOutbox(message, locked.getConversationType(), now);
        ImConversationMemberEntity receiverMember = memberMapper.selectByConversationAndUser(locked.getConversationId(), receiverId);
        if (receiverMember == null || !enabled(receiverMember.getMute())) {
            insertPushOutbox(message, now);
        }

        redisStore.touchConversation(senderId, locked.getConversationId(), now);
        redisStore.touchConversation(receiverId, locked.getConversationId(), now);
        redisStore.evictTotalUnread(receiverId);

        ImConversationEntity refreshed = conversationMapper.selectById(locked.getConversationId());
        ImConversationMemberEntity senderMember = memberMapper.selectByConversationAndUser(refreshed.getConversationId(), senderId);
        Map<String, UserSummary> users = memberClient.batchSummary(List.of(String.valueOf(receiverId)));
        return new ImSendMessageResponse(
                toMessageItem(message, senderId),
                toConversationItem(senderMember, refreshed, message, users, senderId)
        );
    }

    @Transactional(readOnly = true)
    public ImMessageListResponse messages(String userId, String conversationId, Long afterSeq, Long beforeSeq, Integer limit) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedConversationId = parseId(conversationId, ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        ImConversationMemberEntity member = requireMember(parsedConversationId, currentUserId);
        int pageLimit = normalizeMessageLimit(limit);
        List<ImMessageEntity> rows;
        if (afterSeq != null && afterSeq >= 0) {
            rows = conversationMessageMapper.selectAfter(parsedConversationId, afterSeq, safeLong(member.getLastVisibleSeq()), pageLimit + 1);
        } else if (beforeSeq != null && beforeSeq > 0) {
            rows = conversationMessageMapper.selectBefore(parsedConversationId, beforeSeq, safeLong(member.getLastVisibleSeq()), pageLimit + 1);
        } else {
            rows = conversationMessageMapper.selectLatest(parsedConversationId, safeLong(member.getLastVisibleSeq()), pageLimit + 1);
        }
        boolean hasMore = rows.size() > pageLimit;
        List<ImMessageEntity> page = hasMore ? rows.subList(0, pageLimit) : rows;
        List<ImMessageItem> items = page.stream()
                .map(message -> toMessageItem(message, currentUserId))
                .toList();
        Long nextAfterSeq = page.isEmpty() ? afterSeq : page.get(page.size() - 1).getConversationSeq();
        Long nextBeforeSeq = page.isEmpty() ? beforeSeq : page.get(0).getConversationSeq();
        return new ImMessageListResponse(items, nextAfterSeq, nextBeforeSeq, hasMore);
    }

    @Transactional
    public ImReceivedResponse markReceived(String userId, String conversationId, ImReceivedRequest request) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedConversationId = parseId(conversationId, ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        ImConversationMemberEntity member = requireMember(parsedConversationId, currentUserId);
        Long seq = normalizeSeq(request == null ? null : request.receivedSeq(), member.getCurrentSeq());
        LocalDateTime now = now();
        boolean advanced = seq > safeLong(member.getLastReceivedSeq());
        memberMapper.updateReceived(parsedConversationId, currentUserId, seq, now);
        userMessageMapper.markReceived(currentUserId, parsedConversationId, seq, now);
        if (advanced) {
            insertAckOutbox(parsedConversationId, currentUserId, seq, now);
        }
        return new ImReceivedResponse(conversationId, Math.max(seq, safeLong(member.getLastReceivedSeq())));
    }

    @Transactional
    public ImReadResponse markRead(String userId, String conversationId, ImReadRequest request) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedConversationId = parseId(conversationId, ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        ImConversationMemberEntity member = requireMember(parsedConversationId, currentUserId);
        Long seq = normalizeSeq(request == null ? null : request.readSeq(), member.getCurrentSeq());
        LocalDateTime now = now();
        boolean advanced = seq > safeLong(member.getLastReadSeq());
        userMessageMapper.markRead(currentUserId, parsedConversationId, seq, now);
        int unreadCount = userMessageMapper.countUnreadAfter(currentUserId, parsedConversationId, seq);
        memberMapper.updateRead(parsedConversationId, currentUserId, seq, unreadCount, now);
        if (advanced) {
            insertReadOutbox(parsedConversationId, currentUserId, seq, now);
        }
        redisStore.evictTotalUnread(currentUserId);
        long totalUnread = totalUnreadFromDb(currentUserId);
        redisStore.putTotalUnread(currentUserId, totalUnread);
        return new ImReadResponse(conversationId, Math.max(seq, safeLong(member.getLastReadSeq())), unreadCount, totalUnread);
    }

    @Transactional(readOnly = true)
    public ImUnreadCountResponse unreadCount(String userId) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long cached = redisStore.totalUnread(currentUserId);
        if (cached != null) {
            return new ImUnreadCountResponse(cached);
        }
        long totalUnread = totalUnreadFromDb(currentUserId);
        redisStore.putTotalUnread(currentUserId, totalUnread);
        return new ImUnreadCountResponse(totalUnread);
    }

    @Transactional
    public ImConversationItem updateSettings(String userId, String conversationId, ImConversationSettingsRequest request) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedConversationId = parseId(conversationId, ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        requireMember(parsedConversationId, currentUserId);
        Integer pinned = request == null || request.pinned() == null ? null : flag(request.pinned());
        Integer mute = request == null || request.mute() == null ? null : flag(request.mute());
        memberMapper.updateSettings(parsedConversationId, currentUserId, pinned, mute, now());
        ImConversationEntity conversation = conversationMapper.selectById(parsedConversationId);
        ImConversationMemberEntity member = memberMapper.selectByConversationAndUser(parsedConversationId, currentUserId);
        ImMessageEntity lastMessage = conversation.getLastMessageId() == null ? null : messageMapper.selectById(conversation.getLastMessageId());
        Map<String, UserSummary> users = loadPeerUsers(List.of(member));
        return toConversationItem(member, conversation, lastMessage, users, currentUserId);
    }

    @Transactional
    public ImConversationDeleteResponse deleteConversation(String userId, String conversationId) {
        Long currentUserId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        Long parsedConversationId = parseId(conversationId, ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        ImConversationMemberEntity member = requireMember(parsedConversationId, currentUserId);
        memberMapper.hideConversation(parsedConversationId, currentUserId, safeLong(member.getCurrentSeq()), now());
        redisStore.removeConversation(currentUserId, parsedConversationId);
        redisStore.evictTotalUnread(currentUserId);
        return new ImConversationDeleteResponse(conversationId, true);
    }

    @Transactional(readOnly = true)
    public ImBatchSummaryResponse batchSummary(ImBatchSummaryRequest request) {
        if (request == null || request.conversationIds() == null || request.conversationIds().isEmpty()) {
            return new ImBatchSummaryResponse(List.of());
        }
        if (request.conversationIds().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
        List<Long> ids = request.conversationIds().stream()
                .map(id -> parseId(id, ApiErrorCode.PARAM_INVALID))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ImBatchSummaryResponse(List.of());
        }
        Map<Long, ImConversationEntity> conversations = conversationMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(ImConversationEntity::getConversationId, Function.identity()));
        List<Long> messageIds = conversations.values().stream()
                .map(ImConversationEntity::getLastMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ImMessageEntity> messages = messageIds.isEmpty()
                ? Map.of()
                : messageMapper.selectByIds(messageIds).stream().collect(Collectors.toMap(ImMessageEntity::getMessageId, Function.identity()));
        List<ImBatchSummaryItem> items = new ArrayList<>();
        for (Long id : ids) {
            ImConversationEntity conversation = conversations.get(id);
            if (conversation == null) {
                items.add(new ImBatchSummaryItem(String.valueOf(id), null, null, null, "NOT_FOUND"));
            } else {
                items.add(new ImBatchSummaryItem(
                        String.valueOf(id),
                        conversation.getConversationType(),
                        safeLong(conversation.getCurrentSeq()),
                        conversation.getLastMessageId() == null ? null : toMessageItem(messages.get(conversation.getLastMessageId()), null),
                        "FOUND"
                ));
            }
        }
        return new ImBatchSummaryResponse(items);
    }

    @Transactional(readOnly = true)
    public ImPushPolicyResponse pushPolicy(String conversationId, String userId) {
        Long parsedConversationId = parseId(conversationId, ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        Long parsedUserId = parseId(userId, ApiErrorCode.PARAM_INVALID);
        ImConversationMemberEntity member = requireMember(parsedConversationId, parsedUserId);
        boolean mute = enabled(member.getMute());
        return new ImPushPolicyResponse(conversationId, userId, mute, !mute);
    }

    @Transactional
    public ImRebuildUnreadResponse rebuildUnread(String userId) {
        Long parsedUserId = parseId(userId, ApiErrorCode.PARAM_INVALID);
        LocalDateTime now = now();
        long totalUnread = totalUnreadFromDb(parsedUserId);
        redisStore.putTotalUnread(parsedUserId, totalUnread);
        return new ImRebuildUnreadResponse(userId, totalUnread, toOffsetString(now));
    }

    private ImSendMessageResponse responseForExisting(Long senderId, ImMessageEntity existing) {
        ImConversationEntity conversation = conversationMapper.selectById(existing.getConversationId());
        ImConversationMemberEntity member = memberMapper.selectByConversationAndUser(existing.getConversationId(), senderId);
        Map<String, UserSummary> users = loadPeerUsers(List.of(member));
        return new ImSendMessageResponse(
                toMessageItem(existing, senderId),
                toConversationItem(member, conversation, existing, users, senderId)
        );
    }

    private ConversationContext conversationForSend(Long senderId, ImSendMessageRequest request, LocalDateTime now) {
        Long conversationId = parseNullableId(request == null ? null : request.conversationId(), ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        if (conversationId != null) {
            ImConversationMemberEntity senderMember = requireMember(conversationId, senderId);
            Long peerUserId = senderMember.getPeerUserId();
            if (peerUserId == null) {
                List<ImConversationMemberEntity> members = memberMapper.selectByConversation(conversationId);
                peerUserId = members.stream()
                        .map(ImConversationMemberEntity::getUserId)
                        .filter(id -> !id.equals(senderId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ApiErrorCode.IM_TARGET_INVALID));
            }
            validateTarget(senderId, peerUserId);
            return new ConversationContext(conversationMapper.selectById(conversationId), senderMember, peerUserId);
        }
        Long targetUserId = parseId(request == null ? null : request.targetUserId(), ApiErrorCode.IM_TARGET_INVALID);
        validateTarget(senderId, targetUserId);
        return ensureSingleConversation(senderId, targetUserId, now);
    }

    private ConversationContext ensureSingleConversation(Long currentUserId, Long targetUserId, LocalDateTime now) {
        String singleKey = singleKey(currentUserId, targetUserId);
        ImConversationEntity conversation = conversationMapper.selectBySingleKey(singleKey);
        if (conversation == null) {
            conversation = new ImConversationEntity();
            conversation.setConversationId(idGenerator.nextId());
            conversation.setConversationType(CONVERSATION_SINGLE);
            conversation.setSingleKey(singleKey);
            conversation.setCurrentSeq(0L);
            conversation.setLastMessageId(null);
            conversation.setLastMessageAt(null);
            conversation.setConversationStatus(STATUS_NORMAL);
            conversation.setCreatedAt(now);
            conversation.setUpdatedAt(now);
            try {
                conversationMapper.insert(conversation);
            } catch (DuplicateKeyException exception) {
                conversation = conversationMapper.selectBySingleKey(singleKey);
            }
        }
        if (conversation == null) {
            throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
        }
        ensureMember(conversation.getConversationId(), currentUserId, targetUserId, now);
        ensureMember(conversation.getConversationId(), targetUserId, currentUserId, now);
        memberMapper.restoreVisible(conversation.getConversationId(), currentUserId, now);
        ImConversationMemberEntity member = memberMapper.selectByConversationAndUser(conversation.getConversationId(), currentUserId);
        return new ConversationContext(conversation, member, targetUserId);
    }

    private void ensureMember(Long conversationId, Long userId, Long peerUserId, LocalDateTime now) {
        ImConversationMemberEntity entity = new ImConversationMemberEntity();
        entity.setId(idGenerator.nextId());
        entity.setConversationId(conversationId);
        entity.setUserId(userId);
        entity.setPeerUserId(peerUserId);
        entity.setMemberRole(ROLE_MEMBER);
        entity.setMemberStatus(STATUS_NORMAL);
        entity.setLastReadSeq(0L);
        entity.setLastReceivedSeq(0L);
        entity.setUnreadCount(0);
        entity.setPinned(0);
        entity.setMute(0);
        entity.setHidden(0);
        entity.setLastVisibleSeq(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        memberMapper.insertIgnore(entity);
    }

    private void validateTarget(Long currentUserId, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(currentUserId)) {
            throw new BusinessException(ApiErrorCode.IM_TARGET_INVALID);
        }
        if (!memberClient.sendAllowed(String.valueOf(currentUserId)) || !memberClient.sendAllowed(String.valueOf(targetUserId))) {
            throw new BusinessException(ApiErrorCode.IM_SEND_FORBIDDEN);
        }
    }

    private Long nextUserSeq(Long userId, LocalDateTime now) {
        ImUserSequenceEntity seed = new ImUserSequenceEntity();
        seed.setUserId(userId);
        seed.setCurrentSeq(0L);
        seed.setCreatedAt(now);
        seed.setUpdatedAt(now);
        userSequenceMapper.insertIgnore(seed);
        ImUserSequenceEntity locked = userSequenceMapper.selectForUpdate(userId);
        Long nextSeq = safeLong(locked == null ? null : locked.getCurrentSeq()) + 1;
        userSequenceMapper.updateCurrentSeq(userId, nextSeq, now);
        return nextSeq;
    }

    private void insertUserMessage(Long userId, Long userSeq, ImMessageEntity message, int readStatus, int receivedStatus, LocalDateTime now) {
        ImUserMessageEntity entity = new ImUserMessageEntity();
        entity.setId(idGenerator.nextId());
        entity.setUserId(userId);
        entity.setUserSeq(userSeq);
        entity.setConversationId(message.getConversationId());
        entity.setConversationSeq(message.getConversationSeq());
        entity.setMessageId(message.getMessageId());
        entity.setSenderId(message.getSenderId());
        entity.setReadStatus(readStatus);
        entity.setReceivedStatus(receivedStatus);
        entity.setSentAt(message.getSentAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMessageMapper.insert(entity);
    }

    private ImConversationMemberEntity requireMember(Long conversationId, Long userId) {
        ImConversationMemberEntity member = memberMapper.selectByConversationAndUser(conversationId, userId);
        if (member == null || !STATUS_NORMAL.equals(member.getMemberStatus())) {
            throw new BusinessException(ApiErrorCode.IM_CONVERSATION_FORBIDDEN);
        }
        return member;
    }

    private Map<Long, ImMessageEntity> lastMessages(List<ImConversationMemberEntity> members) {
        List<Long> messageIds = members.stream()
                .map(ImConversationMemberEntity::getLastMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageMapper.selectByIds(messageIds).stream()
                .collect(Collectors.toMap(ImMessageEntity::getMessageId, Function.identity()));
    }

    private Map<String, UserSummary> loadPeerUsers(List<ImConversationMemberEntity> members) {
        Set<String> userIds = members.stream()
                .map(ImConversationMemberEntity::getPeerUserId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return memberClient.batchSummary(new ArrayList<>(userIds));
    }

    private ImConversationItem toConversationItem(
            ImConversationMemberEntity member,
            ImConversationEntity conversation,
            ImMessageEntity lastMessage,
            Map<String, UserSummary> users,
            Long currentUserId
    ) {
        if (member == null || conversation == null) {
            throw new BusinessException(ApiErrorCode.IM_CONVERSATION_NOT_FOUND);
        }
        return new ImConversationItem(
                String.valueOf(conversation.getConversationId()),
                conversation.getConversationType(),
                toUserSummary(users.get(String.valueOf(member.getPeerUserId())), member.getPeerUserId()),
                lastMessage == null ? null : toMessageItem(lastMessage, currentUserId),
                safeLong(conversation.getCurrentSeq()),
                safeLong(member.getLastReadSeq()),
                safeLong(member.getLastReceivedSeq()),
                member.getUnreadCount() == null ? 0 : member.getUnreadCount(),
                enabled(member.getPinned()),
                enabled(member.getMute()),
                enabled(member.getHidden()),
                toOffsetString(conversation.getLastMessageAt() == null ? conversation.getUpdatedAt() : conversation.getLastMessageAt())
        );
    }

    private ImConversationEntity conversationFromMember(ImConversationMemberEntity member) {
        ImConversationEntity entity = new ImConversationEntity();
        entity.setConversationId(member.getConversationId());
        entity.setConversationType(CONVERSATION_SINGLE);
        entity.setCurrentSeq(safeLong(member.getCurrentSeq()));
        entity.setLastMessageId(member.getLastMessageId());
        entity.setLastMessageAt(member.getLastMessageAt());
        entity.setConversationStatus(STATUS_NORMAL);
        entity.setCreatedAt(member.getCreatedAt());
        entity.setUpdatedAt(member.getUpdatedAt());
        return entity;
    }

    private ImMessageItem toMessageItem(ImMessageEntity message, Long currentUserId) {
        if (message == null) {
            return null;
        }
        return new ImMessageItem(
                String.valueOf(message.getMessageId()),
                String.valueOf(message.getConversationId()),
                message.getConversationSeq(),
                String.valueOf(message.getSenderId()),
                String.valueOf(message.getReceiverId()),
                message.getMessageType(),
                parseJsonMap(message.getContentJson()),
                message.getSummary(),
                message.getMessageStatus(),
                currentUserId != null && currentUserId.equals(message.getSenderId()),
                toOffsetString(message.getSentAt())
        );
    }

    private ImUserSummary toUserSummary(UserSummary summary, Long fallbackUserId) {
        if (fallbackUserId == null) {
            return null;
        }
        return new ImUserSummary(
                String.valueOf(fallbackUserId),
                summary == null || isBlank(summary.nickname()) ? "用户" + fallbackUserId : summary.nickname(),
                summary == null ? null : summary.avatarUrl()
        );
    }

    private MessageContent normalizeContent(ImSendMessageRequest request) {
        String type = request == null || isBlank(request.messageType()) ? MESSAGE_TEXT : request.messageType().trim().toUpperCase();
        if (!MESSAGE_TEXT.equals(type)) {
            throw new BusinessException(ApiErrorCode.IM_MESSAGE_TYPE_UNSUPPORTED);
        }
        Object rawText = request.content() == null ? null : request.content().get("text");
        String text = rawText == null ? "" : String.valueOf(rawText).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ApiErrorCode.IM_MESSAGE_INVALID);
        }
        if (text.length() > TEXT_MAX_LENGTH) {
            throw new BusinessException(ApiErrorCode.IM_MESSAGE_SIZE_EXCEEDED);
        }
        String summary = text.length() <= 80 ? text : text.substring(0, 80);
        return new MessageContent(Map.of("text", text), summary);
    }

    private String normalizeClientMsgId(String clientMsgId) {
        if (isBlank(clientMsgId) || clientMsgId.trim().length() > 128) {
            throw new BusinessException(ApiErrorCode.IM_CLIENT_MSG_ID_INVALID);
        }
        return clientMsgId.trim();
    }

    private int normalizeConversationSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_CONVERSATION_SIZE;
        }
        if (pageSize > MAX_CONVERSATION_SIZE) {
            throw new BusinessException(ApiErrorCode.IM_CURSOR_INVALID);
        }
        return pageSize;
    }

    private int normalizeMessageLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        if (limit > MAX_MESSAGE_LIMIT) {
            throw new BusinessException(ApiErrorCode.IM_CURSOR_INVALID);
        }
        return limit;
    }

    private ConversationCursor parseConversationCursor(String cursor) {
        if (isBlank(cursor)) {
            return null;
        }
        String[] parts = cursor.split("_", 2);
        if (parts.length != 2) {
            throw new BusinessException(ApiErrorCode.IM_CURSOR_INVALID);
        }
        try {
            return new ConversationCursor(parseCursorTime(parts[0]), Long.valueOf(parts[1]));
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.IM_CURSOR_INVALID);
        }
    }

    private String nextConversationCursor(List<ImConversationMemberEntity> page, boolean hasMore) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }
        ImConversationMemberEntity last = page.get(page.size() - 1);
        LocalDateTime time = last.getLastMessageAt() == null ? last.getUpdatedAt() : last.getLastMessageAt();
        return toOffsetString(time) + "_" + last.getConversationId();
    }

    private LocalDateTime parseCursorTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            return LocalDateTime.parse(value);
        }
    }

    private Long normalizeSeq(Long incoming, Long fallback) {
        long maxSeq = safeLong(fallback);
        long value = incoming == null || incoming <= 0 ? maxSeq : incoming;
        return Math.max(0L, Math.min(value, maxSeq));
    }

    private long totalUnreadFromDb(Long userId) {
        Long value = memberMapper.sumUnread(userId);
        return value == null ? 0L : Math.max(0L, value);
    }

    private void insertMessageSentOutbox(ImMessageEntity message, String conversationType, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", String.valueOf(message.getMessageId()));
        payload.put("conversationId", String.valueOf(message.getConversationId()));
        payload.put("conversationType", conversationType);
        payload.put("conversationSeq", message.getConversationSeq());
        payload.put("senderId", String.valueOf(message.getSenderId()));
        payload.put("receiverId", String.valueOf(message.getReceiverId()));
        payload.put("messageType", message.getMessageType());
        payload.put("summary", message.getSummary());
        payload.put("sentAt", toOffsetString(message.getSentAt()));
        insertOutbox("ImMessageSent", message.getConversationId() + ":" + message.getConversationSeq(), payload, now);
    }

    private void insertAckOutbox(Long conversationId, Long userId, Long seq, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", String.valueOf(conversationId));
        payload.put("userId", String.valueOf(userId));
        payload.put("receivedSeq", seq);
        payload.put("receivedAt", toOffsetString(now));
        insertOutbox("ImMessageAcked", conversationId + ":" + userId, payload, now);
    }

    private void insertReadOutbox(Long conversationId, Long userId, Long seq, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", String.valueOf(conversationId));
        payload.put("userId", String.valueOf(userId));
        payload.put("readSeq", seq);
        payload.put("readAt", toOffsetString(now));
        insertOutbox("ImMessageRead", conversationId + ":" + userId, payload, now);
    }

    private void insertPushOutbox(ImMessageEntity message, LocalDateTime now) {
        Map<String, UserSummary> users = memberClient.batchSummary(List.of(String.valueOf(message.getSenderId())));
        UserSummary sender = users.get(String.valueOf(message.getSenderId()));
        String title = sender == null || isBlank(sender.nickname()) ? "BlueNote 私信" : sender.nickname();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", "push_req_im_" + message.getMessageId());
        payload.put("sourceService", "bluenote-im");
        payload.put("sourceBizType", "IM_MESSAGE");
        payload.put("sourceBizId", String.valueOf(message.getMessageId()));
        payload.put("scene", "IM_MESSAGE");
        payload.put("targetUserId", String.valueOf(message.getReceiverId()));
        payload.put("targetDevicePolicy", "ALL_ACTIVE_DEVICES");
        payload.put("deliveryStrategy", "ONLINE_THEN_OFFLINE");
        payload.put("priority", 8);
        payload.put("title", title);
        payload.put("body", message.getSummary());
        payload.put("data", Map.of(
                "conversationId", String.valueOf(message.getConversationId()),
                "messageId", String.valueOf(message.getMessageId()),
                "conversationSeq", message.getConversationSeq(),
                "senderId", String.valueOf(message.getSenderId()),
                "messageType", message.getMessageType()
        ));
        payload.put("expireAt", toOffsetString(now.plusMinutes(10)));
        insertOutbox("PushSendRequested", "push_req_im_" + message.getMessageId(), payload, now);
    }

    private void insertOutbox(String eventType, String bizKey, Map<String, Object> payload, LocalDateTime now) {
        String eventId = "evt_" + eventType + "_" + bizKey + "_" + UUID.randomUUID();
        ImOutboxEventEntity entity = new ImOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(bizKey);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, bizKey, payload, now)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setNextRetryAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(String eventId, String eventType, String bizKey, Map<String, Object> payload, LocalDateTime now) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", toOffsetString(now));
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-im");
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload);
        return envelope;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize IM JSON", exception);
        }
    }

    private String singleKey(Long userA, Long userB) {
        long min = Math.min(userA, userB);
        long max = Math.max(userA, userB);
        return min + ":" + max;
    }

    private Long parseId(String value, ApiErrorCode errorCode) {
        if (isBlank(value)) {
            throw new BusinessException(errorCode);
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private Long parseNullableId(String value, ApiErrorCode errorCode) {
        if (isBlank(value)) {
            return null;
        }
        return parseId(value, errorCode);
    }

    private boolean enabled(Integer value) {
        return value != null && value == 1;
    }

    private int flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toOffsetString(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.ofHours(8)).toString();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    private record ConversationContext(
            ImConversationEntity conversation,
            ImConversationMemberEntity member,
            Long peerUserId
    ) {
    }

    private record ConversationCursor(LocalDateTime lastMessageAt, Long conversationId) {
    }

    private record MessageContent(Map<String, Object> content, String summary) {
    }
}
