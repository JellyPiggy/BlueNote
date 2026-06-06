package com.bluenote.member.auth.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.member.auth.api.dto.ChangePasswordRequest;
import com.bluenote.member.auth.api.dto.LoginRequest;
import com.bluenote.member.auth.api.dto.LogoutRequest;
import com.bluenote.member.auth.api.dto.LogoutResponse;
import com.bluenote.member.auth.api.dto.RefreshTokenRequest;
import com.bluenote.member.auth.api.dto.RegisterRequest;
import com.bluenote.member.auth.api.dto.TokenPairResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService {

    private static final long ACCESS_TOKEN_EXPIRES_IN = 3600L;
    private static final long REFRESH_TOKEN_EXPIRES_IN = 2_592_000L;

    private final AtomicLong userIdSequence = new AtomicLong(10000);
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, String> usernameToUserId = new ConcurrentHashMap<>();

    public TokenPairResponse register(RegisterRequest request) {
        if (request.password().length() < 8) {
            throw new BusinessException(ApiErrorCode.PASSWORD_TOO_WEAK);
        }
        String userId = String.valueOf(userIdSequence.incrementAndGet());
        String existing = usernameToUserId.putIfAbsent(request.username(), userId);
        if (existing != null) {
            throw new BusinessException(ApiErrorCode.USERNAME_ALREADY_EXISTS);
        }
        return tokenPair(userId);
    }

    public TokenPairResponse login(LoginRequest request) {
        String userId = usernameToUserId.computeIfAbsent(request.username(), ignored -> String.valueOf(userIdSequence.incrementAndGet()));
        return tokenPair(userId);
    }

    public TokenPairResponse refresh(RefreshTokenRequest request) {
        return tokenPair("10001");
    }

    public LogoutResponse logout(LogoutRequest request) {
        return new LogoutResponse(true);
    }

    public TokenPairResponse changePassword(ChangePasswordRequest request) {
        if (request.newPassword().length() < 8) {
            throw new BusinessException(ApiErrorCode.PASSWORD_TOO_WEAK);
        }
        return tokenPair("10001");
    }

    private TokenPairResponse tokenPair(String userId) {
        return new TokenPairResponse(
                userId,
                "dev-access-" + randomToken(),
                "dev-refresh-" + randomToken(),
                ACCESS_TOKEN_EXPIRES_IN,
                REFRESH_TOKEN_EXPIRES_IN
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
