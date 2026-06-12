package com.bluenote.social.relation.api.dto;

import java.util.List;

public record InternalRelationPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {
}
