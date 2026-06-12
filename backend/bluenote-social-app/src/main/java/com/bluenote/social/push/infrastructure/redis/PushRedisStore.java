package com.bluenote.social.push.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PushRedisStore {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration PREFERENCE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public PushRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public void markDeviceActive(Long userId, String deviceId, LocalDateTime activeAt) {
        redisTemplate.opsForZSet().add(activeDeviceKey(userId), deviceId, toEpochMillis(activeAt));
    }

    public void removeDevice(Long userId, String deviceId) {
        redisTemplate.opsForZSet().remove(activeDeviceKey(userId), deviceId);
    }

    public void cachePreference(Long userId, Map<String, Object> values) {
        Map<String, String> stringValues = new LinkedHashMap<>();
        values.forEach((key, value) -> stringValues.put(key, value == null ? "" : String.valueOf(value)));
        redisTemplate.opsForHash().putAll(preferenceKey(userId), stringValues);
        redisTemplate.expire(preferenceKey(userId), PREFERENCE_TTL);
    }

    public Map<Object, Object> preference(Long userId) {
        return redisTemplate.opsForHash().entries(preferenceKey(userId));
    }

    public void evictPreference(Long userId) {
        redisTemplate.delete(preferenceKey(userId));
    }

    private String activeDeviceKey(Long userId) {
        return RedisKeyBuilder.build(env, "push", "device:active", userId);
    }

    private String preferenceKey(Long userId) {
        return RedisKeyBuilder.build(env, "push", "preference", userId);
    }

    private double toEpochMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
