package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// A GLOBAL FILTER runs on EVERY request that passes through the gateway.
//
// GlobalFilter vs GatewayFilter:
//   - GlobalFilter: applies to ALL routes automatically
//   - GatewayFilter: applies only to specific routes you configure
//
// This filter logs the method + path + client IP for every request.
// In production, you'd add correlation IDs here for distributed tracing.
//
// The Ordered interface lets you control WHEN this filter runs.
// Lower number = runs earlier. We use Ordered.HIGHEST_PRECEDENCE so
// this runs FIRST (before routing, auth, etc.) — we want to log everything.

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        log.info(">>> Gateway Request: {} {} from {}",
                request.getMethod(),
                request.getURI().getPath(),
                request.getRemoteAddress());

        // chain.filter(exchange) passes the request to the next filter in the chain.
        // .then() runs AFTER the response comes back from the downstream service.
        return chain.filter(exchange).then(Mono.fromRunnable(() ->
                log.info("<<< Gateway Response: {} for {} {}",
                        exchange.getResponse().getStatusCode(),
                        request.getMethod(),
                        request.getURI().getPath())
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
