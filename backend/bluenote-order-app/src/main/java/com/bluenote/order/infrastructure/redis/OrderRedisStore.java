package com.bluenote.order.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class OrderRedisStore {

    private static final DefaultRedisScript<Long> PRE_DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -4
            end
            if redis.call('EXISTS', KEYS[3]) == 0 then
                return -2
            end
            redis.call('DEL', KEYS[3])
            if redis.call('EXISTS', KEYS[5]) == 1 then
                return 0
            end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                return -1
            end
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then
                redis.call('SET', KEYS[5], '1', 'EX', ARGV[3])
                return 0
            end
            stock = redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            redis.call('HSET', KEYS[4], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[4], ARGV[3])
            if stock <= 0 then
                redis.call('SET', KEYS[5], '1', 'EX', ARGV[3])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RECOVER_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('INCR', KEYS[1])
            end
            redis.call('SREM', KEYS[2], ARGV[1])
            redis.call('HDEL', KEYS[3], ARGV[1])
            redis.call('DEL', KEYS[4])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String env;
    private final long redisRetainMinutes;

    public OrderRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env,
            @Value("${bluenote.order.redis-retain-minutes:60}") long redisRetainMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
        this.redisRetainMinutes = redisRetainMinutes;
    }

    public void preheat(Long activityId, int stock, Duration ttl) {
        Duration safeTtl = ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(redisRetainMinutes) : ttl;
        redisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(Math.max(0, stock)), safeTtl);
        redisTemplate.delete(soldOutKey(activityId));
    }

    public void rebuild(Long activityId, int stock, Set<Long> userIds, Duration ttl) {
        Duration safeTtl = ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(redisRetainMinutes) : ttl;
        String usersKey = usersKey(activityId);
        String requestKey = requestKey(activityId);
        redisTemplate.opsForValue().set(rebuildingKey(activityId), "1", Duration.ofMinutes(5));
        try {
            redisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(Math.max(0, stock)), safeTtl);
            redisTemplate.delete(usersKey);
            if (userIds != null && !userIds.isEmpty()) {
                String[] members = userIds.stream().map(String::valueOf).toArray(String[]::new);
                redisTemplate.opsForSet().add(usersKey, members);
                redisTemplate.expire(usersKey, safeTtl);
            }
            redisTemplate.delete(requestKey);
            redisTemplate.delete(soldOutKey(activityId));
        } finally {
            redisTemplate.delete(rebuildingKey(activityId));
        }
    }

    public RedisActivitySnapshot snapshot(Long activityId) {
        String stockValue = redisTemplate.opsForValue().get(stockKey(activityId));
        Boolean stockExists = redisTemplate.hasKey(stockKey(activityId));
        Long participantCount = redisTemplate.opsForSet().size(usersKey(activityId));
        Boolean soldOut = redisTemplate.hasKey(soldOutKey(activityId));
        return new RedisActivitySnapshot(
                Boolean.TRUE.equals(stockExists),
                parseInt(stockValue),
                participantCount == null ? 0 : participantCount.intValue(),
                Boolean.TRUE.equals(soldOut),
                rebuilding(activityId)
        );
    }

    public String issueToken(Long activityId, Long userId, Duration ttl) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(tokenKey(activityId, userId, token), "1", ttl);
        return token;
    }

    public long preDeduct(Long activityId, Long userId, Long requestId, String token, Duration ttl) {
        Long result = redisTemplate.execute(
                PRE_DEDUCT_SCRIPT,
                List.of(
                        stockKey(activityId),
                        usersKey(activityId),
                        tokenKey(activityId, userId, token),
                        requestKey(activityId),
                        soldOutKey(activityId)
                ),
                String.valueOf(userId),
                String.valueOf(requestId),
                String.valueOf(Math.max(60, ttl.toSeconds()))
        );
        return result == null ? -4L : result;
    }

    public void recoverReservation(Long activityId, Long userId) {
        redisTemplate.execute(
                RECOVER_SCRIPT,
                List.of(stockKey(activityId), usersKey(activityId), requestKey(activityId), soldOutKey(activityId)),
                String.valueOf(userId)
        );
    }

    public boolean rebuilding(Long activityId) {
        Boolean exists = redisTemplate.hasKey(rebuildingKey(activityId));
        return Boolean.TRUE.equals(exists);
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stockKey(Long activityId) {
        return RedisKeyBuilder.build(env, "order", "seckill:stock", activityId);
    }

    private String usersKey(Long activityId) {
        return RedisKeyBuilder.build(env, "order", "seckill:users", activityId);
    }

    private String tokenKey(Long activityId, Long userId, String token) {
        return RedisKeyBuilder.build(env, "order", "seckill:token", activityId, userId, token);
    }

    private String requestKey(Long activityId) {
        return RedisKeyBuilder.build(env, "order", "seckill:request", activityId);
    }

    private String soldOutKey(Long activityId) {
        return RedisKeyBuilder.build(env, "order", "seckill:soldout", activityId);
    }

    private String rebuildingKey(Long activityId) {
        return RedisKeyBuilder.build(env, "order", "activity:rebuilding", activityId);
    }

    public record RedisActivitySnapshot(
            Boolean stockKeyExists,
            Integer stock,
            Integer participantCount,
            Boolean soldOut,
            Boolean rebuilding
    ) {
    }
}
