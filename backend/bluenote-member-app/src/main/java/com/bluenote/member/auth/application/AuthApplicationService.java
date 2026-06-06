package com.bluenote.member.auth.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.JwtAccessTokenService;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.member.auth.api.dto.ChangePasswordRequest;
import com.bluenote.member.auth.api.dto.LoginRequest;
import com.bluenote.member.auth.api.dto.LogoutRequest;
import com.bluenote.member.auth.api.dto.LogoutResponse;
import com.bluenote.member.auth.api.dto.RefreshTokenRequest;
import com.bluenote.member.auth.api.dto.RegisterRequest;
import com.bluenote.member.auth.api.dto.TokenPairResponse;
import com.bluenote.member.auth.infrastructure.entity.AuthAccountEntity;
import com.bluenote.member.auth.infrastructure.entity.AuthLoginAuditEntity;
import com.bluenote.member.auth.infrastructure.entity.AuthOutboxEventEntity;
import com.bluenote.member.auth.infrastructure.entity.AuthPasswordEntity;
import com.bluenote.member.auth.infrastructure.entity.AuthSessionEntity;
import com.bluenote.member.auth.infrastructure.mapper.AuthAccountMapper;
import com.bluenote.member.auth.infrastructure.mapper.AuthLoginAuditMapper;
import com.bluenote.member.auth.infrastructure.mapper.AuthOutboxEventMapper;
import com.bluenote.member.auth.infrastructure.mapper.AuthPasswordMapper;
import com.bluenote.member.auth.infrastructure.mapper.AuthSessionMapper;
import com.bluenote.member.common.JsonPayloads;
import com.bluenote.member.common.MemberIdGenerator;
import com.bluenote.member.user.api.dto.RegisterProfileRequest;
import com.bluenote.member.user.application.UserApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthApplicationService {

    private static final long REFRESH_TOKEN_EXPIRES_IN = 2_592_000L;
    private static final String DEFAULT_NICKNAME = "小蓝";
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthAccountMapper accountMapper;
    private final AuthPasswordMapper passwordMapper;
    private final AuthSessionMapper sessionMapper;
    private final AuthLoginAuditMapper loginAuditMapper;
    private final AuthOutboxEventMapper outboxEventMapper;
    private final UserApplicationService userApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAccessTokenService accessTokenService;
    private final MemberIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;

    public AuthApplicationService(
            AuthAccountMapper accountMapper,
            AuthPasswordMapper passwordMapper,
            AuthSessionMapper sessionMapper,
            AuthLoginAuditMapper loginAuditMapper,
            AuthOutboxEventMapper outboxEventMapper,
            UserApplicationService userApplicationService,
            PasswordEncoder passwordEncoder,
            JwtAccessTokenService accessTokenService,
            MemberIdGenerator idGenerator,
            JsonPayloads jsonPayloads
    ) {
        this.accountMapper = accountMapper;
        this.passwordMapper = passwordMapper;
        this.sessionMapper = sessionMapper;
        this.loginAuditMapper = loginAuditMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.userApplicationService = userApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
    }

    @Transactional
    public TokenPairResponse register(RegisterRequest request) {
        validatePasswordStrength(request.password());
        if (accountMapper.selectByUsername(request.username()) != null) {
            throw new BusinessException(ApiErrorCode.USERNAME_ALREADY_EXISTS);
        }

        LocalDateTime now = now();
        Long userId = idGenerator.nextId();
        String registerChannel = registerChannel(request.platform());

        try {
            accountMapper.insert(account(userId, request.username(), registerChannel, now));
            passwordMapper.insert(password(userId, request.password(), now));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiErrorCode.USERNAME_ALREADY_EXISTS);
        }

        userApplicationService.registerProfile(new RegisterProfileRequest(
                String.valueOf(userId),
                request.username(),
                registerChannel,
                DEFAULT_NICKNAME
        ));

        TokenMaterial token = issueSession(
                userId,
                request.deviceId(),
                request.deviceName(),
                request.platform(),
                request.appVersion(),
                now
        );
        insertAudit(userId, request.username(), "REGISTER", "SUCCESS", null,
                request.deviceId(), request.platform(), request.appVersion(), now);
        insertAuthOutbox("UserRegistered", userId, userRegisteredPayload(userId, request, registerChannel), now);
        return tokenPair(userId, token);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPairResponse login(LoginRequest request) {
        LocalDateTime now = now();
        AuthAccountEntity account = accountMapper.selectByUsername(request.username());
        if (account == null) {
            insertAudit(null, request.username(), "LOGIN", "FAIL", "USERNAME_OR_PASSWORD_ERROR",
                    request.deviceId(), request.platform(), request.appVersion(), now);
            throw new BusinessException(ApiErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!"NORMAL".equals(account.getAccountStatus())) {
            insertAudit(account.getUserId(), request.username(), "LOGIN", "FAIL", "ACCOUNT_DISABLED",
                    request.deviceId(), request.platform(), request.appVersion(), now);
            throw new BusinessException(ApiErrorCode.ACCOUNT_DISABLED);
        }

        AuthPasswordEntity password = passwordMapper.selectByUserId(account.getUserId());
        if (password == null || !passwordEncoder.matches(request.password(), password.getPasswordHash())) {
            insertAudit(account.getUserId(), request.username(), "LOGIN", "FAIL", "USERNAME_OR_PASSWORD_ERROR",
                    request.deviceId(), request.platform(), request.appVersion(), now);
            throw new BusinessException(ApiErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        sessionMapper.revokeActiveByUserDevice(account.getUserId(), request.deviceId(), "LOGIN_ROTATED", now);
        TokenMaterial token = issueSession(
                account.getUserId(),
                request.deviceId(),
                request.deviceName(),
                request.platform(),
                request.appVersion(),
                now
        );
        insertAudit(account.getUserId(), request.username(), "LOGIN", "SUCCESS", null,
                request.deviceId(), request.platform(), request.appVersion(), now);
        return tokenPair(account.getUserId(), token);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPairResponse refresh(RefreshTokenRequest request) {
        LocalDateTime now = now();
        String oldRefreshTokenHash = sha256Hex(request.refreshToken());
        AuthSessionEntity session = sessionMapper.selectByRefreshTokenHash(oldRefreshTokenHash);
        if (session == null) {
            throw new BusinessException(ApiErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!request.deviceId().equals(session.getDeviceId())) {
            sessionMapper.updateStatus(session.getSessionId(), "REVOKED", "TOKEN_REFRESH_REPLAYED", now);
            insertAudit(session.getUserId(), null, "REFRESH", "FAIL", "TOKEN_REFRESH_REPLAYED",
                    request.deviceId(), session.getPlatform(), session.getAppVersion(), now);
            throw new BusinessException(ApiErrorCode.TOKEN_REFRESH_REPLAYED);
        }
        if (!"ACTIVE".equals(session.getSessionStatus())) {
            throw new BusinessException(ApiErrorCode.SESSION_REVOKED);
        }
        if (session.getRefreshTokenExpiresAt().isBefore(now)) {
            sessionMapper.updateStatus(session.getSessionId(), "EXPIRED", "REFRESH_TOKEN_EXPIRED", now);
            insertAudit(session.getUserId(), null, "REFRESH", "FAIL", "REFRESH_TOKEN_EXPIRED",
                    request.deviceId(), session.getPlatform(), session.getAppVersion(), now);
            throw new BusinessException(ApiErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        ensureNormalAccount(session.getUserId());

        TokenMaterial token = newTokenMaterial(session.getUserId(), session.getDeviceId(), session.getSessionId());
        int updated = sessionMapper.rotateRefreshToken(
                session.getSessionId(),
                oldRefreshTokenHash,
                token.refreshTokenHash(),
                now.plusSeconds(REFRESH_TOKEN_EXPIRES_IN),
                now
        );
        if (updated == 0) {
            sessionMapper.updateStatus(session.getSessionId(), "REVOKED", "TOKEN_REFRESH_REPLAYED", now);
            throw new BusinessException(ApiErrorCode.TOKEN_REFRESH_REPLAYED);
        }
        insertAudit(session.getUserId(), null, "REFRESH", "SUCCESS", null,
                request.deviceId(), session.getPlatform(), session.getAppVersion(), now);
        return tokenPair(session.getUserId(), token);
    }

    @Transactional
    public LogoutResponse logout(LogoutRequest request) {
        LocalDateTime now = now();
        String refreshTokenHash = sha256Hex(request.refreshToken());
        AuthSessionEntity session = sessionMapper.selectByRefreshTokenHash(refreshTokenHash);
        sessionMapper.revokeByRefreshTokenHash(refreshTokenHash, "USER_LOGOUT", now);
        if (session != null) {
            insertAudit(session.getUserId(), null, "LOGOUT", "SUCCESS", null,
                    session.getDeviceId(), session.getPlatform(), session.getAppVersion(), now);
        }
        return new LogoutResponse(true);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPairResponse changePassword(String userId, ChangePasswordRequest request) {
        validatePasswordStrength(request.newPassword());
        LocalDateTime now = now();
        Long parsedUserId = parseUserId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        AuthAccountEntity account = ensureNormalAccount(parsedUserId);
        AuthPasswordEntity currentPassword = passwordMapper.selectByUserId(parsedUserId);
        if (currentPassword == null || !passwordEncoder.matches(request.oldPassword(), currentPassword.getPasswordHash())) {
            insertAudit(parsedUserId, account.getUsername(), "CHANGE_PASSWORD", "FAIL", "OLD_PASSWORD_ERROR",
                    currentDeviceId(), "H5", "password-change", now);
            throw new BusinessException(ApiErrorCode.OLD_PASSWORD_ERROR);
        }

        int nextVersion = currentPassword.getPasswordVersion() + 1;
        passwordMapper.updatePassword(parsedUserId, passwordEncoder.encode(request.newPassword()), nextVersion, now);

        String deviceId = currentDeviceId();
        sessionMapper.revokeActiveByUserDevice(parsedUserId, deviceId, "PASSWORD_CHANGED", now);
        TokenMaterial token = issueSession(parsedUserId, deviceId, null, "H5", "password-change", now);
        insertAudit(parsedUserId, account.getUsername(), "CHANGE_PASSWORD", "SUCCESS", null,
                deviceId, "H5", "password-change", now);
        return tokenPair(parsedUserId, token);
    }

    private AuthAccountEntity account(Long userId, String username, String registerChannel, LocalDateTime now) {
        AuthAccountEntity entity = new AuthAccountEntity();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setAccountStatus("NORMAL");
        entity.setRegisterChannel(registerChannel);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        return entity;
    }

    private AuthPasswordEntity password(Long userId, String rawPassword, LocalDateTime now) {
        AuthPasswordEntity entity = new AuthPasswordEntity();
        entity.setId(idGenerator.nextId());
        entity.setUserId(userId);
        entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        entity.setPasswordAlgo("BCrypt");
        entity.setPasswordVersion(1);
        entity.setPasswordUpdatedAt(now);
        entity.setNeedReset(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private TokenMaterial issueSession(
            Long userId,
            String deviceId,
            String deviceName,
            String platform,
            String appVersion,
            LocalDateTime now
    ) {
        Long sessionId = idGenerator.nextId();
        TokenMaterial token = newTokenMaterial(userId, deviceId, sessionId);
        AuthSessionEntity session = new AuthSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setDeviceId(deviceId);
        session.setDeviceName(deviceName);
        session.setPlatform(platform);
        session.setAppVersion(appVersion);
        session.setRefreshTokenHash(token.refreshTokenHash());
        session.setRefreshTokenExpiresAt(now.plusSeconds(REFRESH_TOKEN_EXPIRES_IN));
        session.setSessionStatus("ACTIVE");
        session.setLoginIp(requestIp());
        session.setLastActiveAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return token;
    }

    private AuthAccountEntity ensureNormalAccount(Long userId) {
        AuthAccountEntity account = accountMapper.selectByUserId(userId);
        if (account == null) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        if (!"NORMAL".equals(account.getAccountStatus())) {
            throw new BusinessException(ApiErrorCode.ACCOUNT_DISABLED);
        }
        return account;
    }

    private void insertAudit(
            Long userId,
            String username,
            String action,
            String result,
            String failReason,
            String deviceId,
            String platform,
            String appVersion,
            LocalDateTime now
    ) {
        AuthLoginAuditEntity entity = new AuthLoginAuditEntity();
        entity.setId(idGenerator.nextId());
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setAction(action);
        entity.setResult(result);
        entity.setFailReason(failReason);
        entity.setIp(requestIp());
        entity.setDeviceId(deviceId);
        entity.setPlatform(platform);
        entity.setAppVersion(appVersion);
        entity.setTraceId(TraceIdHolder.currentOrNew());
        entity.setCreatedAt(now);
        loginAuditMapper.insert(entity);
    }

    private void insertAuthOutbox(String eventType, Long aggregateId, Map<String, Object> payload, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        AuthOutboxEventEntity entity = new AuthOutboxEventEntity();
        entity.setId(idGenerator.nextId());
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, aggregateId, "bluenote-auth", payload)));
        entity.setStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> userRegisteredPayload(Long userId, RegisterRequest request, String registerChannel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", String.valueOf(userId));
        payload.put("username", request.username());
        payload.put("registerChannel", registerChannel);
        payload.put("deviceId", request.deviceId());
        return payload;
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            Long aggregateId,
            String producer,
            Map<String, Object> payload
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", OffsetDateTime.now(CHINA_ZONE).toString());
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", producer);
        envelope.put("bizKey", String.valueOf(aggregateId));
        envelope.put("payload", payload);
        return envelope;
    }

    private TokenPairResponse tokenPair(Long userId, TokenMaterial token) {
        return new TokenPairResponse(
                String.valueOf(userId),
                token.accessToken(),
                token.refreshToken(),
                accessTokenService.expiresInSeconds(),
                REFRESH_TOKEN_EXPIRES_IN
        );
    }

    private TokenMaterial newTokenMaterial(Long userId, String deviceId, Long sessionId) {
        String accessToken = accessTokenService.issue(
                String.valueOf(userId),
                deviceId,
                String.valueOf(sessionId)
        );
        String refreshToken = "refresh." + randomToken();
        return new TokenMaterial(accessToken, refreshToken, sha256Hex(refreshToken));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Long parseUserId(String userId, ApiErrorCode errorCode) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(ApiErrorCode.PASSWORD_TOO_WEAK);
        }
    }

    private String registerChannel(String platform) {
        return "H5".equals(platform) ? "H5" : "APP";
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String currentDeviceId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || userContext.deviceId() == null || userContext.deviceId().isBlank()) {
            return "PASSWORD_CHANGE";
        }
        return userContext.deviceId();
    }

    private String requestIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",", 2)[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private record TokenMaterial(String accessToken, String refreshToken, String refreshTokenHash) {
    }
}
