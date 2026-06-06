package com.bluenote.member.user.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchUserSummaryRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> userIds
) {
}
