package com.bluenote.content.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CommentCounterSourceTarget(
        @NotBlank
        String targetType,

        @NotBlank
        String targetId,

        @NotEmpty
        @Size(max = 10)
        List<String> fields
) {
}
