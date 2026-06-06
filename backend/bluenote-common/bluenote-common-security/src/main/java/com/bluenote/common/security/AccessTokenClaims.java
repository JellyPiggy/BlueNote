package com.bluenote.common.security;

import java.time.Instant;

public record AccessTokenClaims(
        String userId,
        String deviceId,
        String sessionId,
        String tokenId,
        Instant issuedAt,
        Instant expiresAt
) {
}
