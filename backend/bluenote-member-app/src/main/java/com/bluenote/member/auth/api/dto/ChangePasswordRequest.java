package com.bluenote.member.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        @Size(min = 8, max = 128)
        String oldPassword,

        @NotBlank
        @Size(min = 8, max = 128)
        String newPassword
) {
}
