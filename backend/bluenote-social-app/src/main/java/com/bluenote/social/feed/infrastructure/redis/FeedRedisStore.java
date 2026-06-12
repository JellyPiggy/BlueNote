package com.bluenote.social.feed.infrastructure.redis;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class FeedRedisStore {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PAD_WIDTH = 20;

    private final StringRedisTemplate redisTemplate;
    private final String env;

    public FeedRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${bluenote.env:local}") String env
    ) {
        this.redisTemplate = redisTemplate;
        this.env = env;
    }

    public void addInbox(Long userId, Long noteId, LocalDateTime publishedAt, int inboxLimit) {
        addZSet(inboxKey(userId), noteId, publishedAt, inboxLimit);
    }

    public void addAuthorOutbox(Long authorId, Long noteId, LocalDateTime publishedAt, int authorOutboxLimit) {
        addZSet(authorOutboxKey(authorId), noteId, publishedAt, authorOutboxLimit);
    }

    public List<Long> inboxNoteIds(Long userId, int limit) {
        return reverseRange(inboxKey(userId), limit);
    }

    public void removeInbox(Long userId, Long noteId) {
        redisTemplate.opsForZSet().remove(inboxKey(userId), padded(noteId));
    }

    public void removeAuthorOutbox(Long authorId, Long noteId) {
        redisTemplate.opsForZSet().remove(authorOutboxKey(authorId), padded(noteId));
    }

    public void markActive(Long userId, LocalDateTime activeAt) {
        redisTemplate.opsForZSet().add(activeUserKey(), String.valueOf(userId), toEpochMillis(activeAt));
    }

    public void replaceInbox(Long userId, List<FeedRedisItem> items, int inboxLimit) {
        String key = inboxKey(userId);
        redisTemplate.delete(key);
        for (FeedRedisItem item : items) {
            addZSet(key, item.noteId(), item.publishedAt(), inboxLimit);
        }
    }

    private void addZSet(String key, Long noteId, LocalDateTime publishedAt, int limit) {
        redisTemplate.opsForZSet().add(key, padded(noteId), toEpochMillis(publishedAt));
        if (limit > 0) {
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size > limit) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - limit - 1);
            }
        }
    }

    private List<Long> reverseRange(String key, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, Math.max(0, limit - 1));
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            if (value != null) {
                noteIds.add(Long.valueOf(value));
            }
        }
        return Collections.unmodifiableList(noteIds);
    }

    private String inboxKey(Long userId) {
        return "bluenote:" + env + ":feed:inbox:" + userId;
    }

    private String authorOutboxKey(Long authorId) {
        return "bluenote:" + env + ":feed:author:outbox:" + authorId;
    }

    private String activeUserKey() {
        return "bluenote:" + env + ":feed:active:user";
    }

    private String padded(Long noteId) {
        return String.format("%0" + PAD_WIDTH + "d", noteId);
    }

    private double toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(CHINA_ZONE).toInstant().toEpochMilli();
    }

    public record FeedRedisItem(Long noteId, LocalDateTime publishedAt) {
    }
}
