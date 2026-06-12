package com.bluenote.social.notification.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationRedisStore {

    private static final Duration UNREAD_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public NotificationRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public Map<String, Long> getUnread(String userId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(unreadKey(userId));
        Map<String, Long> values = new LinkedHashMap<>();
        raw.forEach((field, value) -> values.put(String.valueOf(field), parseLong(value)));
        return values;
    }

    public void putUnread(String userId, Map<String, Long> values) {
        Map<String, String> stringValues = new LinkedHashMap<>();
        values.forEach((field, value) -> stringValues.put(field, String.valueOf(Math.max(0L, value))));
        redisTemplate.opsForHash().putAll(unreadKey(userId), stringValues);
        redisTemplate.expire(unreadKey(userId), UNREAD_TTL);
    }

    private String unreadKey(String userId) {
        return RedisKeyBuilder.build(env, "notification", "unread", userId);
    }

    private long parseLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
