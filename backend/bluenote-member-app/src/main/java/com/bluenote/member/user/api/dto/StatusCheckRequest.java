package com.bluenote.member.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StatusCheckRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> userIds,

        @NotBlank
        String scene
) {
}
