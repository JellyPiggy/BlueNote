package com.bluenote.social.im.api.dto;

import java.util.List;

public record ImConversationListResponse(
        List<ImConversationItem> items,
        String nextCursor,
        Boolean hasMore
) {
}
