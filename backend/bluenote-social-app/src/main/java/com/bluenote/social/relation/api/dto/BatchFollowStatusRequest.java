package com.bluenote.social.relation.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchFollowStatusRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> targetUserIds
) {
}
