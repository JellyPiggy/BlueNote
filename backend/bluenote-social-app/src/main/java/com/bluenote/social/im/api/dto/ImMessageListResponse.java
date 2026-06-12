package com.bluenote.social.im.api.dto;

import java.util.List;

public record ImMessageListResponse(
        List<ImMessageItem> items,
        Long nextAfterSeq,
        Long nextBeforeSeq,
        Boolean hasMore
) {
}
