package com.bluenote.social.counter.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.social.counter.api.dto.CounterBatchRequest;
import com.bluenote.social.counter.api.dto.CounterBatchResponse;
import com.bluenote.social.counter.api.dto.CounterItem;
import com.bluenote.social.counter.api.dto.CounterTarget;
import com.bluenote.social.counter.infrastructure.client.ContentCounterSourceClient;
import com.bluenote.social.relation.application.RelationApplicationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CounterApplicationService {

    private static final int MAX_BATCH_SIZE = 100;
    private static final String TARGET_NOTE = "NOTE";
    private static final String TARGET_USER = "USER";
    private static final String TARGET_COMMENT = "COMMENT";

    private final RelationApplicationService relationApplicationService;
    private final ContentCounterSourceClient contentCounterSourceClient;

    public CounterApplicationService(
            RelationApplicationService relationApplicationService,
            ContentCounterSourceClient contentCounterSourceClient
    ) {
        this.relationApplicationService = relationApplicationService;
        this.contentCounterSourceClient = contentCounterSourceClient;
    }

    public CounterBatchResponse batch(CounterBatchRequest request) {
        if (request.targets().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ApiErrorCode.COUNTER_BATCH_SIZE_EXCEEDED);
        }
        return new CounterBatchResponse(request.targets().stream().map(this::counterItem).toList());
    }

    private CounterItem counterItem(CounterTarget target) {
        validateTarget(target);
        Map<String, Long> counts = zeroCounts(target.fields());
        boolean degraded = false;

        List<String> relationFields = fieldsForSource(target, Source.RELATION);
        if (!relationFields.isEmpty()) {
            try {
                counts.putAll(relationCounts(target.targetId(), relationFields));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        List<String> noteFields = fieldsForSource(target, Source.NOTE);
        if (!noteFields.isEmpty()) {
            try {
                counts.putAll(contentCounterSourceClient.noteCounts(target.targetType(), target.targetId(), noteFields));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        List<String> commentFields = fieldsForSource(target, Source.COMMENT);
        if (!commentFields.isEmpty()) {
            try {
                counts.putAll(contentCounterSourceClient.commentCounts(target.targetType(), target.targetId(), commentFields));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        return new CounterItem(target.targetType(), target.targetId(), counts, degraded);
    }

    private void validateTarget(CounterTarget target) {
        parseTargetId(target.targetId());
        if (!List.of(TARGET_NOTE, TARGET_USER, TARGET_COMMENT).contains(target.targetType())) {
            throw new BusinessException(ApiErrorCode.COUNTER_TARGET_TYPE_UNSUPPORTED);
        }
        for (String field : target.fields()) {
            if (!fieldSupported(target.targetType(), field)) {
                throw new BusinessException(ApiErrorCode.COUNTER_FIELD_NOT_SUPPORTED);
            }
        }
    }

    private Long parseTargetId(String targetId) {
        try {
            if (targetId == null || targetId.isBlank()) {
                throw new NumberFormatException("blank");
            }
            return Long.valueOf(targetId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.COUNTER_TARGET_ID_INVALID);
        }
    }

    private boolean fieldSupported(String targetType, String field) {
        return switch (targetType) {
            case TARGET_NOTE -> List.of("like_count", "collect_count", "comment_count").contains(field);
            case TARGET_USER -> List.of("following_count", "follower_count", "note_count", "liked_count").contains(field);
            case TARGET_COMMENT -> List.of("like_count", "reply_count").contains(field);
            default -> false;
        };
    }

    private List<String> fieldsForSource(CounterTarget target, Source source) {
        return target.fields().stream()
                .distinct()
                .filter(field -> belongsToSource(target.targetType(), field, source))
                .toList();
    }

    private boolean belongsToSource(String targetType, String field, Source source) {
        return switch (source) {
            case RELATION -> TARGET_USER.equals(targetType)
                    && List.of("following_count", "follower_count").contains(field);
            case NOTE -> (TARGET_NOTE.equals(targetType) && List.of("like_count", "collect_count").contains(field))
                    || (TARGET_USER.equals(targetType) && List.of("note_count", "liked_count").contains(field));
            case COMMENT -> (TARGET_NOTE.equals(targetType) && "comment_count".equals(field))
                    || (TARGET_COMMENT.equals(targetType) && List.of("like_count", "reply_count").contains(field));
        };
    }

    private Map<String, Long> relationCounts(String targetId, List<String> fields) {
        var request = new com.bluenote.social.relation.api.dto.CounterSourceRequest(List.of(
                new com.bluenote.social.relation.api.dto.CounterSourceTarget(TARGET_USER, targetId, fields)
        ));
        return relationApplicationService.counterSource(request).items().stream()
                .findFirst()
                .map(com.bluenote.social.relation.api.dto.CounterSourceItem::counts)
                .orElseGet(Map::of);
    }

    private Map<String, Long> zeroCounts(List<String> fields) {
        Map<String, Long> counts = new LinkedHashMap<>();
        fields.stream().distinct().forEach(field -> counts.put(field, 0L));
        return counts;
    }

    private enum Source {
        RELATION,
        NOTE,
        COMMENT
    }
}
