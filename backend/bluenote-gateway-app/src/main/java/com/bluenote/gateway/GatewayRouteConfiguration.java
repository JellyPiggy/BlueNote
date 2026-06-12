package com.bluenote.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfiguration {

    @Bean
    public RouteLocator bluenoteRoutes(
            RouteLocatorBuilder builder,
            @Value("${bluenote.routes.member-uri}") String memberUri,
            @Value("${bluenote.routes.content-uri}") String contentUri,
            @Value("${bluenote.routes.social-uri}") String socialUri,
            @Value("${bluenote.routes.social-ws-uri}") String socialWsUri,
            @Value("${bluenote.routes.order-uri}") String orderUri
    ) {
        return builder.routes()
                .route("member-auth", route -> route.path("/api/auth/**").uri(memberUri))
                .route("member-user", route -> route.path("/api/users/**").uri(memberUri))
                .route("content-file", route -> route.path("/api/files/**").uri(contentUri))
                .route("content-note", route -> route.path("/api/notes/**").uri(contentUri))
                .route("content-comment", route -> route.path("/api/comments/**").uri(contentUri))
                .route("social-relation", route -> route.path("/api/relations/**").uri(socialUri))
                .route("social-feed", route -> route.path("/api/feed/**").uri(socialUri))
                .route("social-notification", route -> route.path("/api/notifications/**").uri(socialUri))
                .route("social-push", route -> route.path("/api/push/**").uri(socialUri))
                .route("social-im", route -> route.path("/api/im/**").uri(socialUri))
                .route("social-realtime", route -> route.path("/ws/realtime").uri(socialWsUri))
                .route("order-api", route -> route.path("/api/order/**").uri(orderUri))
                .build();
    }
}
