package com.bluenote.content.comment.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CommentCounterSourceRequest(
        @Valid
        @NotEmpty
        @Size(max = 100)
        List<CommentCounterSourceTarget> targets
) {
}
