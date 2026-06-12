package com.bluenote.social.push.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfiguration implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;

    public RealtimeWebSocketConfiguration(
            RealtimeWebSocketHandler realtimeWebSocketHandler,
            RealtimeHandshakeInterceptor handshakeInterceptor
    ) {
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeWebSocketHandler, "/ws/realtime")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
