package com.bluenote.content.comment.infrastructure.redis;

import com.bluenote.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class CommentRedisStore {

    private static final Duration COMMENT_LIST_TTL = Duration.ofMinutes(10);
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public CommentRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public List<HotCommentMember> hotComments(Long noteId, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(hotKey(noteId), 0, Math.max(0, limit - 1));
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<HotCommentMember> members = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            members.add(new HotCommentMember(Long.valueOf(value), tuple.getScore() == null ? 0D : tuple.getScore()));
        }
        return members;
    }

    public void replaceHotComments(Long noteId, List<HotCommentMember> members, int limit) {
        String key = hotKey(noteId);
        redisTemplate.delete(key);
        for (HotCommentMember member : members) {
            redisTemplate.opsForZSet().add(key, String.valueOf(member.commentId()), member.hotScore());
        }
        trim(key, limit);
        redisTemplate.expire(key, COMMENT_LIST_TTL);
    }

    public void updateHotComment(Long noteId, Long commentId, double hotScore, int limit) {
        String key = hotKey(noteId);
        redisTemplate.opsForZSet().add(key, String.valueOf(commentId), hotScore);
        trim(key, limit);
        redisTemplate.expire(key, COMMENT_LIST_TTL);
    }

    public void removeHotComment(Long noteId, Long commentId) {
        redisTemplate.opsForZSet().remove(hotKey(noteId), String.valueOf(commentId));
    }

    public List<Long> rootTimeComments(Long noteId, boolean descending, int limit) {
        return rangeIds(timeKey(noteId), descending, limit);
    }

    public void replaceRootTimeComments(Long noteId, List<CommentTimeMember> members) {
        replaceTimeMembers(timeKey(noteId), members);
    }

    public void updateRootTimeComment(Long noteId, Long commentId, LocalDateTime createdAt) {
        updateTimeMember(timeKey(noteId), commentId, createdAt);
    }

    public void removeRootTimeComment(Long noteId, Long commentId) {
        redisTemplate.opsForZSet().remove(timeKey(noteId), String.valueOf(commentId));
    }

    public List<Long> replyComments(Long rootCommentId, int limit) {
        return rangeIds(replyKey(rootCommentId), false, limit);
    }

    public void replaceReplyComments(Long rootCommentId, List<CommentTimeMember> members) {
        replaceTimeMembers(replyKey(rootCommentId), members);
    }

    public void updateReplyComment(Long rootCommentId, Long commentId, LocalDateTime createdAt) {
        updateTimeMember(replyKey(rootCommentId), commentId, createdAt);
    }

    public void removeReplyComment(Long rootCommentId, Long commentId) {
        redisTemplate.opsForZSet().remove(replyKey(rootCommentId), String.valueOf(commentId));
    }

    public void removeReplyList(Long rootCommentId) {
        redisTemplate.delete(replyKey(rootCommentId));
    }

    private List<Long> rangeIds(String key, boolean descending, int limit) {
        Set<String> values = descending
                ? redisTemplate.opsForZSet().reverseRange(key, 0, Math.max(0, limit - 1))
                : redisTemplate.opsForZSet().range(key, 0, Math.max(0, limit - 1));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Long> commentIds = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            commentIds.add(Long.valueOf(value));
        }
        return commentIds;
    }

    private void replaceTimeMembers(String key, List<CommentTimeMember> members) {
        redisTemplate.delete(key);
        for (CommentTimeMember member : members) {
            redisTemplate.opsForZSet().add(key, String.valueOf(member.commentId()), toEpochMillis(member.createdAt()));
        }
        redisTemplate.expire(key, COMMENT_LIST_TTL);
    }

    private void updateTimeMember(String key, Long commentId, LocalDateTime createdAt) {
        redisTemplate.opsForZSet().add(key, String.valueOf(commentId), toEpochMillis(createdAt));
        redisTemplate.expire(key, COMMENT_LIST_TTL);
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

    private String hotKey(Long noteId) {
        return RedisKeyBuilder.build(env, "comment", "hot", noteId);
    }

    private String timeKey(Long noteId) {
        return RedisKeyBuilder.build(env, "comment", "time", noteId, "level1");
    }

    private String replyKey(Long rootCommentId) {
        return RedisKeyBuilder.build(env, "comment", "replies", rootCommentId);
    }

    private double toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(CHINA_ZONE).toInstant().toEpochMilli();
    }

    public record HotCommentMember(Long commentId, Double hotScore) {
    }

    public record CommentTimeMember(Long commentId, LocalDateTime createdAt) {
    }
}
