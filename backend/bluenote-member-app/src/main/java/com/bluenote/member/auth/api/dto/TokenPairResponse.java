package com.bluenote.member.auth.api.dto;

public record TokenPairResponse(
        String userId,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {
}
