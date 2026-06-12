package com.bluenote.social.im.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ImRedisStore {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration UNREAD_TTL = Duration.ofMinutes(30);
    private static final Duration CONVERSATION_LIST_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public ImRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public Long totalUnread(Long userId) {
        String value = redisTemplate.opsForValue().get(unreadKey(userId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void putTotalUnread(Long userId, long totalUnread) {
        redisTemplate.opsForValue().set(unreadKey(userId), String.valueOf(Math.max(0L, totalUnread)), UNREAD_TTL);
    }

    public void evictTotalUnread(Long userId) {
        redisTemplate.delete(unreadKey(userId));
    }

    public void touchConversation(Long userId, Long conversationId, LocalDateTime lastMessageAt) {
        redisTemplate.opsForZSet().add(conversationListKey(userId), String.valueOf(conversationId), toEpochMillis(lastMessageAt));
        redisTemplate.expire(conversationListKey(userId), CONVERSATION_LIST_TTL);
    }

    public void removeConversation(Long userId, Long conversationId) {
        redisTemplate.opsForZSet().remove(conversationListKey(userId), String.valueOf(conversationId));
    }

    public Set<String> recentConversationIds(Long userId, int limit) {
        Set<String> values = redisTemplate.opsForZSet().reverseRange(conversationListKey(userId), 0, Math.max(0, limit - 1));
        return values == null ? Set.of() : values;
    }

    private String unreadKey(Long userId) {
        return RedisKeyBuilder.build(env, "im", "unread", userId);
    }

    private String conversationListKey(Long userId) {
        return RedisKeyBuilder.build(env, "im", "conversation:list", userId);
    }

    private double toEpochMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
