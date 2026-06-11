package com.bluenote.content.comment.api.dto;

import java.util.Map;

public record CommentCounterSourceItem(
        String targetType,
        String targetId,
        Map<String, Long> counts
) {
}
