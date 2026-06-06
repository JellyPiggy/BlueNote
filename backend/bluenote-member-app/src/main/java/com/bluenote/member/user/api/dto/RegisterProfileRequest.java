package com.bluenote.member.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterProfileRequest(
        @NotBlank
        String userId,

        @NotBlank
        @Size(max = 32)
        String username,

        @NotBlank
        @Size(max = 32)
        String registerChannel,

        @NotBlank
        @Size(max = 64)
        String defaultNickname
) {
}
