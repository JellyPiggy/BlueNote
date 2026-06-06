package com.bluenote.member.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(min = 4, max = 32)
        String username,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotBlank
        @Size(max = 128)
        String deviceId,

        @Size(max = 128)
        String deviceName,

        @NotBlank
        @Pattern(regexp = "^(IOS|ANDROID|H5)$")
        String platform,

        @NotBlank
        @Size(max = 32)
        String appVersion
) {
}
