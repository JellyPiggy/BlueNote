package com.bluenote.social.counter.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CounterRedisStore {

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public CounterRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public Map<String, Long> getCounts(String targetType, String targetId, List<String> fields) {
        List<Object> hashFields = new ArrayList<>(fields);
        List<Object> values = redisTemplate.opsForHash().multiGet(counterKey(targetType, targetId), hashFields);
        Map<String, Long> counts = new LinkedHashMap<>();
        if (values == null) {
            return counts;
        }
        for (int index = 0; index < fields.size(); index++) {
            Object value = values.get(index);
            if (value != null) {
                counts.put(fields.get(index), parseLong(value));
            }
        }
        return counts;
    }

    public void putCounts(String targetType, String targetId, Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        counts.forEach((field, count) -> values.put(field, String.valueOf(Math.max(0L, count))));
        redisTemplate.opsForHash().putAll(counterKey(targetType, targetId), values);
    }

    public long increment(String targetType, String targetId, String field, long deltaValue) {
        Long value = redisTemplate.opsForHash().increment(counterKey(targetType, targetId), field, deltaValue);
        long current = value == null ? 0L : value;
        if (current < 0) {
            redisTemplate.opsForHash().put(counterKey(targetType, targetId), field, "0");
            current = 0L;
        }
        markDirty(targetType, targetId);
        return current;
    }

    public void markDirty(String targetType, String targetId) {
        redisTemplate.opsForZSet().add(dirtyKey(), targetType + ":" + targetId, Instant.now().toEpochMilli());
    }

    private String counterKey(String targetType, String targetId) {
        return RedisKeyBuilder.build(env, "counter", targetType, targetId);
    }

    private String dirtyKey() {
        return RedisKeyBuilder.build(env, "counter", "dirty");
    }

    private long parseLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
