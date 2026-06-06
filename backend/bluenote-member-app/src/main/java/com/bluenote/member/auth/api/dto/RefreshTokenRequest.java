package com.bluenote.member.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank
        @Size(max = 512)
        String refreshToken,

        @NotBlank
        @Size(max = 128)
        String deviceId
) {
}
