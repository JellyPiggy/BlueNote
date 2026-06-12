package com.bluenote.content.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        String clientRequestId,

        @NotBlank
        @Size(max = 1000)
        String content
) {
}
