package com.bluenote.content.comment.api.dto;

import java.util.List;

public record CommentCursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        boolean degraded
) {
}
