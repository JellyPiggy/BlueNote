package com.bluenote.social.push.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PushRedisStore {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration PREFERENCE_TTL = Duration.ofMinutes(30);
    private static final Duration ONLINE_TTL = Duration.ofMinutes(2);

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
        redisTemplate.opsForSet().remove(onlineUserKey(userId), deviceId);
        redisTemplate.delete(onlineDeviceKey(deviceId));
    }

    public void markDeviceOnline(Long userId, String deviceId, String connectionId, String nodeId, LocalDateTime now) {
        redisTemplate.opsForSet().add(onlineUserKey(userId), deviceId);
        redisTemplate.expire(onlineUserKey(userId), ONLINE_TTL);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("userId", String.valueOf(userId));
        values.put("deviceId", deviceId);
        values.put("connectionId", connectionId);
        values.put("nodeId", nodeId);
        values.put("lastSeenAt", String.valueOf(toEpochMillis(now)));
        redisTemplate.opsForHash().putAll(onlineDeviceKey(deviceId), values);
        redisTemplate.expire(onlineDeviceKey(deviceId), ONLINE_TTL);
    }

    public void touchDeviceOnline(Long userId, String deviceId, LocalDateTime now) {
        redisTemplate.expire(onlineUserKey(userId), ONLINE_TTL);
        redisTemplate.opsForHash().put(onlineDeviceKey(deviceId), "lastSeenAt", String.valueOf(toEpochMillis(now)));
        redisTemplate.expire(onlineDeviceKey(deviceId), ONLINE_TTL);
    }

    public void removeDeviceOnline(Long userId, String deviceId) {
        redisTemplate.opsForSet().remove(onlineUserKey(userId), deviceId);
        redisTemplate.delete(onlineDeviceKey(deviceId));
    }

    public Set<String> onlineDeviceIds(Long userId) {
        Set<String> values = redisTemplate.opsForSet().members(onlineUserKey(userId));
        return values == null ? Set.of() : values;
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

    private String onlineUserKey(Long userId) {
        return RedisKeyBuilder.build(env, "push", "online:user", userId);
    }

    private String onlineDeviceKey(String deviceId) {
        return RedisKeyBuilder.build(env, "push", "online:device", deviceId);
    }

    private double toEpochMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
