package com.bluenote.member.user.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 64)
        String nickname,

        String avatarFileId,

        @Size(max = 256)
        String bio,

        @Pattern(regexp = "^(UNKNOWN|MALE|FEMALE)$")
        String gender,

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        String birthday,

        @Size(max = 32)
        String regionCode,

        String homeCoverFileId,

        Long baseProfileVersion
) {
}
