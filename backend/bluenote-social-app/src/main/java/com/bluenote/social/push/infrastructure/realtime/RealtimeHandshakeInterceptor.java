package com.bluenote.social.push.infrastructure.realtime;

import com.bluenote.common.security.AccessTokenClaims;
import com.bluenote.common.security.AccessTokenException;
import com.bluenote.common.security.JwtAccessTokenService;
import com.bluenote.social.push.infrastructure.entity.PushDeviceEntity;
import com.bluenote.social.push.infrastructure.mapper.PushDeviceMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";
    static final String ATTR_DEVICE_ID = "deviceId";
    static final String ATTR_SESSION_ID = "sessionId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenService accessTokenService;
    private final PushDeviceMapper deviceMapper;

    public RealtimeHandshakeInterceptor(JwtAccessTokenService accessTokenService, PushDeviceMapper deviceMapper) {
        this.accessTokenService = accessTokenService;
        this.deviceMapper = deviceMapper;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = token(request);
        String deviceId = queryParam(request, "deviceId");
        if (isBlank(token) || isBlank(deviceId)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            AccessTokenClaims claims = accessTokenService.validate(token);
            if (!deviceId.equals(claims.deviceId())) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            Long userId = Long.valueOf(claims.userId());
            PushDeviceEntity device = deviceMapper.selectByDeviceId(deviceId);
            if (device == null || !Objects.equals(device.getUserId(), userId) || !"ACTIVE".equals(device.getDeviceStatus())) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_DEVICE_ID, deviceId);
            attributes.put(ATTR_SESSION_ID, claims.sessionId());
            return true;
        } catch (AccessTokenException | NumberFormatException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private String token(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return queryParam(request, "accessToken");
    }

    private String queryParam(ServerHttpRequest request, String name) {
        List<String> values = request.getURI() == null ? List.of() : org.springframework.web.util.UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get(name);
        if (values == null || values.isEmpty() || isBlank(values.get(0))) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                return servletRequest.getServletRequest().getParameter(name);
            }
            return null;
        }
        return values.get(0);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
