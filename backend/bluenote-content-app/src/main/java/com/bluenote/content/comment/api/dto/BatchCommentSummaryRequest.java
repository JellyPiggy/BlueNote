package com.bluenote.content.comment.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchCommentSummaryRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> commentIds
) {
}
