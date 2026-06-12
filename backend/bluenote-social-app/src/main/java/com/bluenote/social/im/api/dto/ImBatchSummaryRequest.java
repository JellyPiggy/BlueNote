package com.bluenote.social.im.api.dto;

import java.util.List;

public record ImBatchSummaryRequest(
        List<String> conversationIds
) {
}
