package com.bluenote.common.security;

import com.bluenote.common.core.ApiErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtAccessTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_TYPE = "access";
    private static final long CLOCK_SKEW_SECONDS = 30L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String issuer;
    private final long expiresInSeconds;
    private final Clock clock;

    public JwtAccessTokenService(ObjectMapper objectMapper, String secret, String issuer, long expiresInSeconds) {
        this(objectMapper, secret, issuer, expiresInSeconds, Clock.systemUTC());
    }

    JwtAccessTokenService(
            ObjectMapper objectMapper,
            String secret,
            String issuer,
            long expiresInSeconds,
            Clock clock
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Access token secret must contain at least 32 characters");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("Access token issuer must not be blank");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("Access token expiresInSeconds must be positive");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.expiresInSeconds = expiresInSeconds;
        this.clock = clock;
    }

    public long expiresInSeconds() {
        return expiresInSeconds;
    }

    public String issue(String userId, String deviceId, String sessionId) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(expiresInSeconds);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("typ", TOKEN_TYPE);
        payload.put("sub", userId);
        payload.put("did", deviceId);
        payload.put("sid", sessionId);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String signingInput = base64Url(json(header)) + "." + base64Url(json(payload));
        return signingInput + "." + sign(signingInput);
    }

    public AccessTokenClaims validate(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalid();
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.US_ASCII), sign(signingInput).getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }

        Map<String, Object> header = readJson(parts[0]);
        if (!"HS256".equals(stringClaim(header, "alg"))) {
            throw invalid();
        }

        Map<String, Object> payload = readJson(parts[1]);
        if (!issuer.equals(stringClaim(payload, "iss")) || !TOKEN_TYPE.equals(stringClaim(payload, "typ"))) {
            throw invalid();
        }

        String userId = stringClaim(payload, "sub");
        String deviceId = stringClaim(payload, "did");
        String sessionId = stringClaim(payload, "sid");
        String tokenId = stringClaim(payload, "jti");
        long issuedAt = longClaim(payload, "iat");
        long expiresAt = longClaim(payload, "exp");
        if (userId == null || deviceId == null || sessionId == null || tokenId == null) {
            throw invalid();
        }

        Instant now = Instant.now(clock);
        if (Instant.ofEpochSecond(expiresAt).plusSeconds(CLOCK_SKEW_SECONDS).isBefore(now)) {
            throw new AccessTokenException(ApiErrorCode.ACCESS_TOKEN_EXPIRED);
        }
        if (Instant.ofEpochSecond(issuedAt).minusSeconds(CLOCK_SKEW_SECONDS).isAfter(now)) {
            throw invalid();
        }

        return new AccessTokenClaims(
                userId,
                deviceId,
                sessionId,
                tokenId,
                Instant.ofEpochSecond(issuedAt),
                Instant.ofEpochSecond(expiresAt)
        );
    }

    private byte[] json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize access token", exception);
        }
    }

    private Map<String, Object> readJson(String base64UrlValue) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(base64UrlValue), MAP_TYPE);
        } catch (IllegalArgumentException | IOException exception) {
            throw invalid();
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign access token", exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private long longClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalid();
    }

    private AccessTokenException invalid() {
        return new AccessTokenException(ApiErrorCode.ACCESS_TOKEN_INVALID);
    }
}
