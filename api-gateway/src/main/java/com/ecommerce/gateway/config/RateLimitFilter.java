package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// In-Memory Rate Limiter using the Token Bucket algorithm.
//
// HOW TOKEN BUCKET WORKS:
// Imagine each client has a bucket that holds tokens.
// - The bucket starts full (e.g., 20 tokens)
// - Each request "costs" 1 token
// - Tokens refill at a steady rate (e.g., 10 per second)
// - If the bucket is empty → request is rejected with 429
//
// WHY TOKEN BUCKET?
// It allows short bursts (a user can quickly make 20 requests)
// while still enforcing an average rate (10 req/sec over time).
// This is more user-friendly than a strict "1 request per 100ms" limit.
//
// PRODUCTION NOTE: In production with multiple gateway instances,
// you'd use Redis-based rate limiting (Spring Cloud Gateway + Redis RateLimiter)
// so all instances share the same counters. We'll add Redis in Phase 8.

@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    // Max tokens in the bucket — allows bursts up to this size
    private static final int MAX_TOKENS = 20;

    // Tokens added per second — the sustained request rate
    private static final int REFILL_RATE = 10;

    // One bucket per client IP address
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Identify the client by their IP address.
        // In production behind a load balancer, you'd use the X-Forwarded-For header instead.
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        // Get or create a bucket for this IP
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, ip -> new TokenBucket(MAX_TOKENS, REFILL_RATE));

        if (bucket.tryConsume()) {
            // Token available — let the request through
            return chain.filter(exchange);
        } else {
            // No tokens — reject with 429 Too Many Requests
            log.warn("Rate limit exceeded for client: {}", clientIp);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "1");
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        // Run BEFORE the logging filter — no point logging requests we're going to reject.
        // Lower number = higher priority. LoggingFilter is HIGHEST_PRECEDENCE (Integer.MIN_VALUE),
        // so we run right after it.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    // Inner class that implements a thread-safe token bucket.
    //
    // Thread safety matters because the gateway is reactive — multiple requests
    // from the same IP could arrive simultaneously on different threads.
    // We use AtomicLong for lock-free concurrency (much faster than synchronized).
    static class TokenBucket {
        private final int maxTokens;
        private final int refillRate;
        private final AtomicLong availableTokens;
        private final AtomicLong lastRefillTimestamp;

        TokenBucket(int maxTokens, int refillRate) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.availableTokens = new AtomicLong(maxTokens);
            this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
        }

        boolean tryConsume() {
            refill();
            // Atomically try to decrement. If > 0, we got a token.
            long current = availableTokens.get();
            while (current > 0) {
                if (availableTokens.compareAndSet(current, current - 1)) {
                    return true;
                }
                current = availableTokens.get();
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long lastRefill = lastRefillTimestamp.get();
            // Calculate how many tokens to add based on elapsed time
            long elapsedNanos = now - lastRefill;
            long tokensToAdd = elapsedNanos * refillRate / 1_000_000_000L;

            if (tokensToAdd > 0 && lastRefillTimestamp.compareAndSet(lastRefill, now)) {
                long newTokens = Math.min(maxTokens, availableTokens.get() + tokensToAdd);
                availableTokens.set(newTokens);
            }
        }
    }
}
