package com.bluenote.gateway;

import com.bluenote.common.observability.TraceConstants;
import com.bluenote.common.observability.TraceIdGenerator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayTraceFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
        }

        String finalTraceId = traceId;
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(TraceConstants.TRACE_ID_HEADER, finalTraceId)
                .build();
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, finalTraceId);

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
