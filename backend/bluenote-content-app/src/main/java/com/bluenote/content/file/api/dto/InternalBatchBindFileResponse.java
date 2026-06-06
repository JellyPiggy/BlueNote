package com.bluenote.content.file.api.dto;

import java.util.List;

public record InternalBatchBindFileResponse(
        String bindStatus,
        List<String> fileIds
) {
}

