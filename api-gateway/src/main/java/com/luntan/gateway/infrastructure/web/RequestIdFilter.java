package com.luntan.gateway.infrastructure.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    private static final String HEADER_NAME = "X-Request-Id";

    @Override
    public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String headerRequestId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String requestId = headerRequestId == null || headerRequestId.isBlank()
                ? UUID.randomUUID().toString()
                : headerRequestId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, requestId))
                .build();
        exchange.getResponse().getHeaders().set(HEADER_NAME, requestId);
        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(context -> context.put(HEADER_NAME, requestId))
                .doOnEach(signal -> {
                    if (!signal.isOnComplete()) {
                        MDC.put("requestId", requestId);
                    }
                })
                .doFinally(signalType -> MDC.remove("requestId"));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}