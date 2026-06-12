package com.bluenote.social.relation.api.dto;

import java.util.List;

public record RelationCursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        boolean degraded
) {
}
