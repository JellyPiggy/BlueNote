package com.bluenote.social.feed.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterItem;
import com.bluenote.social.counter.api.dto.CounterTarget;
import com.bluenote.social.counter.application.CounterApplicationService;
import com.bluenote.social.feed.api.dto.FeedCardResponse;
import com.bluenote.social.feed.api.dto.FeedCountsResponse;
import com.bluenote.social.feed.api.dto.FollowingFeedResponse;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient.AuthorRecentNotes;
import com.bluenote.social.feed.infrastructure.client.ContentNoteClient.NoteSummary;
import com.bluenote.social.relation.api.dto.InternalFollowingPageItem;
import com.bluenote.social.relation.api.dto.RelationUserSummary;
import com.bluenote.social.relation.application.RelationApplicationService;
import com.bluenote.social.relation.infrastructure.client.MemberInternalClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FeedApplicationService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int FOLLOWING_SCAN_SIZE = 500;
    private static final int AUTHOR_RECENT_LIMIT = 50;
    private static final int AUTHOR_RECENT_BATCH_SIZE = 50;
    private static final String SOURCE_TYPE_PULL = "PULL";

    private final RelationApplicationService relationApplicationService;
    private final ContentNoteClient contentNoteClient;
    private final CounterApplicationService counterApplicationService;
    private final MemberInternalClient memberInternalClient;

    public FeedApplicationService(
            RelationApplicationService relationApplicationService,
            ContentNoteClient contentNoteClient,
            CounterApplicationService counterApplicationService,
            MemberInternalClient memberInternalClient
    ) {
        this.relationApplicationService = relationApplicationService;
        this.contentNoteClient = contentNoteClient;
        this.counterApplicationService = counterApplicationService;
        this.memberInternalClient = memberInternalClient;
    }

    public FollowingFeedResponse followingFeed(String userId, String cursor, Integer size) {
        parseUserId(userId);
        FeedCursor feedCursor = parseCursor(cursor);
        int pageSize = normalizeSize(size);

        List<String> authorIds = followingAuthorIds(userId);
        if (authorIds.isEmpty()) {
            return new FollowingFeedResponse(List.of(), null, false, false);
        }

        boolean degraded = false;
        List<NoteSummary> notes;
        try {
            notes = recentNotes(authorIds, feedCursor);
        } catch (BusinessException exception) {
            return new FollowingFeedResponse(List.of(), null, false, true);
        }

        List<NoteSummary> visibleNotes = notes.stream()
                .filter(note -> "PUBLISHED".equals(note.noteStatus()) && "PUBLIC".equals(note.visibility()))
                .filter(note -> beforeCursor(note, feedCursor))
                .sorted(noteComparator())
                .limit(pageSize + 1L)
                .toList();

        boolean hasMore = visibleNotes.size() > pageSize;
        List<NoteSummary> pageNotes = hasMore ? visibleNotes.subList(0, pageSize) : visibleNotes;
        if (pageNotes.isEmpty()) {
            return new FollowingFeedResponse(List.of(), null, false, degraded);
        }

        PageDependencies dependencies = pageDependencies(pageNotes);
        degraded = degraded || dependencies.degraded();
        List<FeedCardResponse> items = pageNotes.stream()
                .map(note -> toFeedCard(userId, note, dependencies))
                .toList();
        return new FollowingFeedResponse(items, nextCursor(pageNotes, hasMore), hasMore, degraded);
    }

    private List<String> followingAuthorIds(String userId) {
        List<String> authorIds = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            var page = relationApplicationService.internalFollowingPage(userId, cursor, FOLLOWING_SCAN_SIZE);
            authorIds.addAll(page.items().stream()
                    .map(InternalFollowingPageItem::followeeId)
                    .toList());
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
        return authorIds.stream().distinct().toList();
    }

    private List<NoteSummary> recentNotes(List<String> authorIds, FeedCursor feedCursor) {
        String publishedAfter = feedCursor.sortAt() == null
                ? OffsetDateTime.now(CHINA_ZONE).minusDays(90).toString()
                : null;
        List<NoteSummary> notes = new ArrayList<>();
        for (int index = 0; index < authorIds.size(); index += AUTHOR_RECENT_BATCH_SIZE) {
            int end = Math.min(index + AUTHOR_RECENT_BATCH_SIZE, authorIds.size());
            notes.addAll(contentNoteClient.authorRecentNotes(
                            authorIds.subList(index, end),
                            AUTHOR_RECENT_LIMIT,
                            publishedAfter
                    ).stream()
                    .map(AuthorRecentNotes::notes)
                    .flatMap(List::stream)
                    .toList());
        }
        return notes;
    }

    private PageDependencies pageDependencies(List<NoteSummary> notes) {
        boolean degraded = false;
        List<String> authorIds = notes.stream()
                .map(NoteSummary::authorId)
                .distinct()
                .toList();
        Map<String, RelationUserSummary> authors;
        try {
            authors = memberInternalClient.batchSummary(authorIds);
            degraded = authors.size() < authorIds.size();
        } catch (RuntimeException exception) {
            degraded = true;
            authors = Map.of();
        }

        Map<String, CounterItem> counters;
        try {
            counters = counterApplicationService.batch(new CounterBatchRequest(notes.stream()
                            .map(note -> new CounterTarget(
                                    "NOTE",
                                    note.noteId(),
                                    List.of("like_count", "collect_count", "comment_count")
                            ))
                            .toList()))
                    .items()
                    .stream()
                    .collect(Collectors.toMap(CounterItem::targetId, Function.identity()));
            degraded = degraded || counters.values().stream().anyMatch(CounterItem::degraded);
        } catch (RuntimeException exception) {
            degraded = true;
            counters = Map.of();
        }

        return new PageDependencies(authors, counters, degraded);
    }

    private FeedCardResponse toFeedCard(
            String userId,
            NoteSummary note,
            PageDependencies dependencies
    ) {
        CounterItem counter = dependencies.counters().get(note.noteId());
        Map<String, Long> counts = counter == null ? Map.of() : counter.counts();
        boolean itemDegraded = dependencies.degraded()
                || counter == null
                || counter.degraded()
                || !dependencies.authors().containsKey(note.authorId());
        return new FeedCardResponse(
                userId + "_" + note.noteId(),
                note.noteId(),
                dependencies.authors().getOrDefault(note.authorId(), fallbackAuthor(note.authorId())),
                valueOrEmpty(note.title()),
                valueOrEmpty(note.contentPreview()),
                note.coverUrl(),
                "IMAGE_TEXT",
                new FeedCountsResponse(
                        counts.getOrDefault("like_count", 0L),
                        counts.getOrDefault("collect_count", 0L),
                        counts.getOrDefault("comment_count", 0L)
                ),
                null,
                note.publishedAt(),
                SOURCE_TYPE_PULL,
                itemDegraded
        );
    }

    private RelationUserSummary fallbackAuthor(String authorId) {
        return new RelationUserSummary(authorId, null, null, null, null, "UNKNOWN", null);
    }

    private boolean beforeCursor(NoteSummary note, FeedCursor cursor) {
        if (cursor.sortAt() == null || cursor.noteId() == null) {
            return true;
        }
        OffsetDateTime publishedAt = parsePublishedAt(note.publishedAt());
        int compared = publishedAt.toInstant().compareTo(cursor.sortAt());
        return compared < 0 || (compared == 0 && parseNoteId(note.noteId()) < cursor.noteId());
    }

    private Comparator<NoteSummary> noteComparator() {
        return Comparator
                .comparing((NoteSummary note) -> parsePublishedAt(note.publishedAt()).toInstant())
                .reversed()
                .thenComparing((NoteSummary note) -> parseNoteId(note.noteId()), Comparator.reverseOrder());
    }

    private String nextCursor(List<NoteSummary> items, boolean hasMore) {
        if (!hasMore || items.isEmpty()) {
            return null;
        }
        NoteSummary last = items.get(items.size() - 1);
        return parsePublishedAt(last.publishedAt()).toInstant().toEpochMilli() + "_" + last.noteId();
    }

    private FeedCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new FeedCursor(null, null);
        }
        int separator = cursor.lastIndexOf('_');
        if (separator <= 0 || separator == cursor.length() - 1) {
            throw new BusinessException(ApiErrorCode.FEED_CURSOR_INVALID);
        }
        try {
            long publishedAtMillis = Long.parseLong(cursor.substring(0, separator));
            long noteId = Long.parseLong(cursor.substring(separator + 1));
            return new FeedCursor(Instant.ofEpochMilli(publishedAtMillis), noteId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.FEED_CURSOR_INVALID);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ApiErrorCode.FEED_SIZE_EXCEEDED);
        }
        return Math.max(1, size);
    }

    private long parseUserId(String userId) {
        try {
            if (userId == null || userId.isBlank()) {
                throw new NumberFormatException("blank");
            }
            return Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private long parseNoteId(String noteId) {
        try {
            return Long.parseLong(noteId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
        }
    }

    private OffsetDateTime parsePublishedAt(String publishedAt) {
        try {
            if (publishedAt == null || publishedAt.isBlank()) {
                throw new IllegalArgumentException("blank");
            }
            return OffsetDateTime.parse(publishedAt);
        } catch (RuntimeException exception) {
            throw new BusinessException(ApiErrorCode.FEED_INTERNAL_DEPENDENCY_FAILED);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record FeedCursor(Instant sortAt, Long noteId) {
    }

    private record PageDependencies(
            Map<String, RelationUserSummary> authors,
            Map<String, CounterItem> counters,
            boolean degraded
    ) {
    }
}
