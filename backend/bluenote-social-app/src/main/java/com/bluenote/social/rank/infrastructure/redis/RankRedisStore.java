package com.bluenote.social.rank.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class RankRedisStore {

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public RankRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public void updateMember(String rankCode, String periodId, Long memberId, long score, double rankScore, int limit) {
        String key = exactKey(rankCode, periodId);
        if (score <= 0) {
            redisTemplate.opsForZSet().remove(key, String.valueOf(memberId));
        } else {
            redisTemplate.opsForZSet().add(key, String.valueOf(memberId), rankScore);
        }
        trim(key, limit);
        redisTemplate.opsForZSet().add(dirtyKey(), rankCode + ":" + periodId, System.currentTimeMillis());
    }

    public List<RankRedisMember> top(String rankCode, String periodId, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(exactKey(rankCode, periodId), 0, Math.max(0, limit - 1));
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<RankRedisMember> result = new ArrayList<>();
        int rankNo = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(new RankRedisMember(Long.valueOf(value), tuple.getScore() == null ? 0D : tuple.getScore(), rankNo++));
        }
        return result;
    }

    public Long reverseRank(String rankCode, String periodId, Long memberId) {
        return redisTemplate.opsForZSet().reverseRank(exactKey(rankCode, periodId), String.valueOf(memberId));
    }

    public void replaceExact(String rankCode, String periodId, List<RankRedisMember> members, int limit) {
        String key = exactKey(rankCode, periodId);
        redisTemplate.delete(key);
        for (RankRedisMember member : members) {
            redisTemplate.opsForZSet().add(key, String.valueOf(member.memberId()), member.rankScore());
        }
        trim(key, limit);
        redisTemplate.opsForZSet().add(dirtyKey(), rankCode + ":" + periodId, System.currentTimeMillis());
    }

    private void trim(String key, int limit) {
        if (limit <= 0) {
            return;
        }
        Long size = redisTemplate.opsForZSet().zCard(key);
        if (size != null && size > limit) {
            redisTemplate.opsForZSet().removeRange(key, 0, size - limit - 1);
        }
    }

    private String exactKey(String rankCode, String periodId) {
        return RedisKeyBuilder.build(env, "rank", rankCode, periodId, "exact");
    }

    private String dirtyKey() {
        return RedisKeyBuilder.build(env, "rank", "dirty");
    }

    public record RankRedisMember(Long memberId, Double rankScore, Integer rankNo) {
    }
}
