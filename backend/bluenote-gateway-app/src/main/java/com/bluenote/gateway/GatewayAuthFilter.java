package com.bluenote.gateway;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdGenerator;
import com.bluenote.common.security.AccessTokenClaims;
import com.bluenote.common.security.AccessTokenException;
import com.bluenote.common.security.JwtAccessTokenService;
import com.bluenote.common.security.SecurityHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern PUBLIC_USER_PATH = Pattern.compile("^/api/users/[^/]+/(public|home)$");
    private static final Pattern FILE_ACCESS_URL_PATH = Pattern.compile("^/api/files/[^/]+/access-url$");
    private static final Pattern NOTE_DETAIL_PATH = Pattern.compile("^/api/notes/(?!(me|drafts)$|users/)[^/]+$");
    private static final Pattern NOTE_USER_LIST_PATH = Pattern.compile("^/api/notes/users/[^/]+$");

    private final JwtAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    public GatewayAuthFilter(JwtAccessTokenService accessTokenService, ObjectMapper objectMapper) {
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String token = bearerToken(request);
        if (isAnonymousAuthEndpoint(request)) {
            return chain.filter(exchangeWithUserContext(exchange, null));
        }
        if (token == null) {
            if (isOptionalAuthEndpoint(request)) {
                return chain.filter(exchangeWithUserContext(exchange, null));
            }
            return writeFailure(exchange, ApiErrorCode.ACCESS_TOKEN_INVALID);
        }

        try {
            AccessTokenClaims claims = accessTokenService.validate(token);
            return chain.filter(exchangeWithUserContext(exchange, claims));
        } catch (AccessTokenException exception) {
            return writeFailure(exchange, exception.errorCode());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private ServerWebExchange exchangeWithUserContext(ServerWebExchange exchange, AccessTokenClaims claims) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(SecurityHeaders.USER_ID);
                    headers.remove(SecurityHeaders.DEVICE_ID);
                    headers.remove(SecurityHeaders.SESSION_ID);
                    if (claims != null) {
                        headers.set(SecurityHeaders.USER_ID, claims.userId());
                        headers.set(SecurityHeaders.DEVICE_ID, claims.deviceId());
                        headers.set(SecurityHeaders.SESSION_ID, claims.sessionId());
                    }
                });
        return exchange.mutate().request(requestBuilder.build()).build();
    }

    private boolean isAnonymousAuthEndpoint(ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.POST) {
            return false;
        }
        String path = request.getURI().getPath();
        return "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/token/refresh".equals(path);
    }

    private boolean isOptionalAuthEndpoint(ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.GET) {
            return false;
        }
        String path = request.getURI().getPath();
        return PUBLIC_USER_PATH.matcher(path).matches()
                || FILE_ACCESS_URL_PATH.matcher(path).matches()
                || NOTE_DETAIL_PATH.matcher(path).matches()
                || NOTE_USER_LIST_PATH.matcher(path).matches();
    }

    private String bearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    private Mono<Void> writeFailure(ServerWebExchange exchange, ApiErrorCode errorCode) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
            exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        }
        byte[] body = responseBody(ApiResponse.failure(errorCode, traceId));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private byte[] responseBody(ApiResponse<Object> response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JsonProcessingException exception) {
            return "{\"code\":10000,\"message\":\"system busy\",\"data\":{\"reason\":\"SYSTEM_ERROR\"}}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
