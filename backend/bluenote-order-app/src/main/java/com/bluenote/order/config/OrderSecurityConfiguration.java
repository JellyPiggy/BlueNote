package com.bluenote.order.config;

import com.bluenote.common.security.JwtAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderSecurityConfiguration {

    @Bean
    public JwtAccessTokenService jwtAccessTokenService(
            ObjectMapper objectMapper,
            @Value("${bluenote.security.access-token.secret}") String secret,
            @Value("${bluenote.security.access-token.issuer:bluenote}") String issuer,
            @Value("${bluenote.security.access-token.expires-in-seconds:3600}") long expiresInSeconds
    ) {
        return new JwtAccessTokenService(objectMapper, secret, issuer, expiresInSeconds);
    }
}
