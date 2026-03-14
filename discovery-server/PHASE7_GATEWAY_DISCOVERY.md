# Phase 7 — API Gateway, Service Discovery, Config Server & Distributed Tracing

> **The Problem**: We have 5 microservices, each on its own port. Clients need to know every
> service address. If a service moves or scales, every client breaks. There's no central place
> to manage configuration, and when a request fails across 3 services, we have no idea where
> it went wrong.
>
> **The Solution**: A service registry (Eureka), an API gateway (single entry point),
> a config server (one place for all settings), and distributed tracing (Zipkin).

---

## Table of Contents

1. [The Big Picture — Before vs After](#1-the-big-picture--before-vs-after)
2. [Service Discovery with Eureka](#2-service-discovery-with-eureka)
3. [API Gateway with Spring Cloud Gateway](#3-api-gateway-with-spring-cloud-gateway)
4. [Rate Limiting — Token Bucket Algorithm](#4-rate-limiting--token-bucket-algorithm)
5. [Circuit Breaker & Fallback](#5-circuit-breaker--fallback)
6. [Config Server — Centralized Configuration](#6-config-server--centralized-configuration)
7. [Distributed Tracing with Zipkin](#7-distributed-tracing-with-zipkin)
8. [Structured Logging with Correlation IDs](#8-structured-logging-with-correlation-ids)
9. [SSL/HTTPS Routing Through the Gateway](#9-sslhttps-routing-through-the-gateway)
10. [How Everything Connects](#10-how-everything-connects)
11. [New Java Concepts Learned](#11-new-java-concepts-learned)
12. [Files Created & Modified](#12-files-created--modified)
13. [Port Reference](#13-port-reference)
14. [Common Issues & Fixes](#14-common-issues--fixes)

---

## 1. The Big Picture — Before vs After

### Before Phase 7

```
Client → https://localhost:8081/api/users/1      (must know user-service address)
Client → http://localhost:8082/api/products       (must know product-service address)
Client → http://localhost:8083/api/orders          (must know order-service address)
Client → http://localhost:8084/api/payments        (must know payment-service address)
```

**Problems:**
- Client must know every service's address and port
- If a service moves to a different server/port, all clients break
- No central place to see what's running
- No rate limiting — anyone can hammer any service
- No circuit breaker at the entry point
- Can't trace a request across multiple services
- Configuration is duplicated across 5 services

### After Phase 7

```
Client → http://localhost:8060/api/users/1         (ONE address for everything)
Client → http://localhost:8060/api/products
Client → http://localhost:8060/api/orders
Client → http://localhost:8060/api/payments
```

**What happens behind the scenes:**
```
Client
  │
  ▼
API Gateway (:8060)
  │  1. Rate limit check (token bucket)
  │  2. Log the request
  │  3. Ask Eureka: "Where is user-service?"
  │  4. Eureka replies: "192.168.1.5:8081"
  │  5. Forward request to user-service
  │  6. If service is down → circuit breaker → fallback response
  │  7. Send trace data to Zipkin
  │
  ▼
user-service (:8081)
  │  8. Process the request
  │  9. Send trace data to Zipkin (same traceId!)
  │
  ▼
API Gateway
  │  10. Return response to client
  │  11. Log the response
```

---

## 2. Service Discovery with Eureka

### What Problem Does It Solve?

Without service discovery, services find each other using **hardcoded URLs**:

```java
// order-service calls user-service with a HARDCODED address
WebClient.create("https://localhost:8081").get().uri("/api/users/1")
```

This breaks when:
- user-service moves to a different server
- You run 3 instances of user-service for load balancing
- You deploy to Docker where "localhost" means something different

### How Eureka Works

Eureka is a **phone book** for services. Instead of hardcoding addresses, services register
themselves and look each other up by name.

```
┌──────────────────────────────────────────────┐
│           Eureka Server (:8761)               │
│                                              │
│   Registry:                                  │
│   ┌────────────────┬───────────────────┐     │
│   │ Service Name   │ Address           │     │
│   ├────────────────┼───────────────────┤     │
│   │ USER-SERVICE   │ 192.168.1.5:8081  │     │
│   │ PRODUCT-SERVICE│ 192.168.1.5:8082  │     │
│   │ ORDER-SERVICE  │ 192.168.1.5:8083  │     │
│   │ PAYMENT-SERVICE│ 192.168.1.5:8084  │     │
│   │ API-GATEWAY    │ 192.168.1.5:8060  │     │
│   │ CONFIG-SERVER  │ 192.168.1.5:8888  │     │
│   └────────────────┴───────────────────┘     │
│                                              │
│   Heartbeat: every 30 seconds                │
│   Eviction: removed after 90s of silence     │
└──────────────────────────────────────────────┘
```

**The lifecycle:**
1. Service starts → registers with Eureka ("I'm user-service at 192.168.1.5:8081")
2. Every 30 seconds → sends heartbeat ("I'm still alive")
3. Another service needs user-service → asks Eureka ("Where is user-service?")
4. Eureka replies → "192.168.1.5:8081"
5. Service stops → Eureka notices no heartbeat after 90s → removes from registry

### Discovery Server Code

```java
@SpringBootApplication
@EnableEurekaServer    // ← This ONE annotation turns the app into a service registry
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

The `@EnableEurekaServer` annotation does a LOT behind the scenes:
- Opens REST endpoints for service registration (`/eureka/apps`)
- Starts a web dashboard showing all registered services
- Manages heartbeats and eviction
- Replicates the registry to other Eureka servers (in production clusters)

### Discovery Server Security

The Eureka dashboard needs to be protected — you don't want anyone seeing your service map.

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())          // Eureka clients send POST/PUT — CSRF blocks them
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/eureka/**").permitAll()  // Eureka client API must be open
                .anyRequest().authenticated()               // Dashboard requires login
            )
            .httpBasic(Customizer.withDefaults());  // Basic auth: username/password in headers
        return http.build();
    }
}
```

**Why disable CSRF?** CSRF (Cross-Site Request Forgery) protection requires a special token
in POST/PUT requests. Eureka clients are machines, not browsers — they don't have CSRF tokens.
Without disabling CSRF, every service registration would fail with a 403 Forbidden.

### Discovery Server Configuration

```properties
spring.application.name=discovery-server
server.port=8761

# Don't register with yourself — you ARE the registry
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false

# Enable the web dashboard at http://localhost:8761
eureka.dashboard.enabled=true

# Basic auth credentials (configured via environment variables in production)
spring.security.user.name=${EUREKA_USERNAME:eureka}
spring.security.user.password=${EUREKA_PASSWORD:eureka123}
```

### How Services Register (Client Side)

Every service adds the Eureka client dependency and these properties:

```properties
# The URL includes basic auth credentials to authenticate with Eureka
eureka.client.service-url.defaultZone=http://eureka:eureka123@localhost:8761/eureka

# Register using IP address, not hostname — more reliable in Docker/cloud
eureka.instance.prefer-ip-address=true
```

That's it. Spring Boot auto-configures everything else. On startup, the service automatically:
1. Registers with Eureka
2. Starts sending heartbeats every 30 seconds
3. Fetches the registry (so it knows about other services)

---

## 3. API Gateway with Spring Cloud Gateway

### What Is an API Gateway?

An API gateway is a **single front door** for your entire platform. Instead of clients
talking directly to 5+ services, they talk to ONE address.

Think of it like a hotel reception desk:
- **Without gateway**: Guests walk directly to the kitchen, laundry room, pool
- **With gateway**: Guests go to reception → reception routes them to the right place

### Why Spring Cloud Gateway (Not Zuul or Nginx)?

| Gateway | Type | Why/Why Not |
|---------|------|-------------|
| **Spring Cloud Gateway** | Reactive (WebFlux) | Built for Spring ecosystem, non-blocking, native Eureka integration |
| Netflix Zuul | Blocking (Servlet) | Deprecated by Netflix, replaced by Spring Cloud Gateway |
| Nginx/HAProxy | Standalone | No Eureka integration, separate configuration, not Spring-native |
| Kong/Traefik | Standalone | Powerful but overkill for our learning project |

### Gateway Route Configuration

```properties
# --- User Service Route ---
# lb:https:// = Load Balanced + HTTPS (user-service has a self-signed SSL cert)
spring.cloud.gateway.routes[0].id=user-service
spring.cloud.gateway.routes[0].uri=lb:https://user-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/users/**,/api/auth/**
spring.cloud.gateway.routes[0].metadata.response-timeout=5000

# --- Product Service Route ---
# lb:// = Load Balanced + HTTP (no SSL on product-service)
spring.cloud.gateway.routes[1].id=product-service
spring.cloud.gateway.routes[1].uri=lb://product-service
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/products/**,/api/categories/**
```

**How `lb://` works step by step:**

```
1. Client sends:  GET http://localhost:8060/api/products/1
2. Gateway sees:  Path matches /api/products/** → route to lb://product-service
3. "lb://" means: Ask Eureka for "product-service" address
4. Eureka says:   "product-service is at 192.168.1.5:8082"
5. Gateway sends: GET http://192.168.1.5:8082/api/products/1
6. Gateway gets:  200 OK with product JSON
7. Gateway sends: 200 OK with product JSON back to client
```

**What `lb:https://` adds:** Same as above, but the gateway connects to the downstream
service using HTTPS instead of HTTP. We need this for user-service because it has SSL enabled.

### Gateway Application Class

```java
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        // Enable Reactor context propagation for distributed tracing.
        // Without this, traceId/spanId won't appear in gateway logs because
        // Spring Cloud Gateway uses Project Reactor (reactive/non-blocking).
        //
        // In traditional (servlet) apps, trace context is stored in ThreadLocal.
        // But in reactive apps, a single request may hop across multiple threads.
        // Reactor's "context propagation" ensures the traceId follows the request
        // across thread boundaries by storing it in the Reactor Context instead.
        Hooks.enableAutomaticContextPropagation();

        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

**Why `Hooks.enableAutomaticContextPropagation()`?**

This is a subtle but critical concept:

```
Traditional (Servlet) App:
  Thread-1 handles entire request → ThreadLocal stores traceId → always available

Reactive (WebFlux) App:
  Thread-1 reads request → Thread-2 calls downstream → Thread-3 writes response
  ThreadLocal is per-thread, so traceId gets LOST between threads!

Solution: Reactor Context
  Stores traceId in the reactive pipeline itself, not in any thread.
  Hooks.enableAutomaticContextPropagation() bridges ThreadLocal ↔ Reactor Context.
```

### Request Logging Filter

```java
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Log BEFORE the request is forwarded
        log.info(">>> Gateway Request: {} {} from {}",
                request.getMethod(),
                request.getURI().getPath(),
                request.getRemoteAddress());

        // chain.filter() = pass the request to the next filter (or the route)
        // .then() = after the response comes back, log the status
        return chain.filter(exchange).then(Mono.fromRunnable(() ->
                log.info("<<< Gateway Response: {} for {} {}",
                        exchange.getResponse().getStatusCode(),
                        request.getMethod(),
                        request.getURI().getPath())
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // Run first (before all other filters)
    }
}
```

**Key Concepts:**
- **`GlobalFilter`** — runs on EVERY request through the gateway (all routes)
- **`GatewayFilterChain`** — the chain of filters; call `chain.filter()` to continue
- **`Mono<Void>`** — reactive return type (non-blocking "I'll finish eventually")
- **`Ordered`** — controls execution order; `HIGHEST_PRECEDENCE` = runs first
- **`ServerWebExchange`** — holds both the request and response (WebFlux equivalent of `HttpServletRequest`)

---

## 4. Rate Limiting — Token Bucket Algorithm

### What Is Rate Limiting?

Rate limiting prevents any single client from overwhelming your services. Without it,
one client making 10,000 requests/second could crash your entire platform.

### Why Token Bucket?

There are several rate limiting algorithms. Here's why we chose token bucket:

| Algorithm | How It Works | Downside |
|-----------|-------------|----------|
| **Fixed Window** | Count requests per second. Reset at second boundary. | Burst at window boundary (100 at 0.99s + 100 at 1.01s = 200 in 0.02s) |
| **Sliding Window** | Count requests in the last N seconds | More memory, more complex |
| **Leaky Bucket** | Process requests at a fixed rate | No burst tolerance — rejects even short spikes |
| **Token Bucket** | Tokens refill steadily; each request costs 1 token | Best balance of burst tolerance + sustained rate limiting |

### How Token Bucket Works — The Parking Garage Analogy

```
Imagine a parking garage with 20 spots:

🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️🅿️  (20 spots = MAX_TOKENS)

Cars arrive (= requests):
  🚗 arrives → takes a spot → 19 spots left     ✅ allowed
  🚗 arrives → takes a spot → 18 spots left     ✅ allowed
  ... (18 more cars)
  🚗 arrives → 0 spots left → TURNED AWAY       ❌ 429 Too Many Requests

Meanwhile, spots free up at 10 per second (= REFILL_RATE):
  After 1 second → 10 spots available again
  After 2 seconds → 20 spots (max capacity)

This means:
  - A client can BURST up to 20 rapid requests (the garage was full)
  - But the sustained rate is only 10 req/sec (refill rate)
  - A client sending 15 req/sec will gradually run out of tokens
```

### Implementation

```java
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int MAX_TOKENS = 20;    // Burst capacity
    private static final int REFILL_RATE = 10;   // Sustained rate (per second)

    // One bucket per client IP address
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Identify client by IP
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        // Get or create a bucket for this IP
        TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                ip -> new TokenBucket(MAX_TOKENS, REFILL_RATE));

        if (bucket.tryConsume()) {
            return chain.filter(exchange);  // Token available → let through
        } else {
            // No tokens → reject with 429
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }
}
```

### Thread-Safe Token Bucket (CAS Operations)

```java
static class TokenBucket {
    private final AtomicLong availableTokens;   // Thread-safe counter
    private final AtomicLong lastRefillTimestamp;

    boolean tryConsume() {
        refill();
        // CAS loop: atomically try to take a token
        long current = availableTokens.get();
        while (current > 0) {
            // compareAndSet = "if value is still 'current', set it to 'current - 1'"
            // If another thread changed it between get() and compareAndSet(), retry
            if (availableTokens.compareAndSet(current, current - 1)) {
                return true;  // Got a token!
            }
            current = availableTokens.get();  // Someone else took a token, re-read
        }
        return false;  // No tokens left
    }
}
```

**Why `AtomicLong` instead of `synchronized`?**

```
synchronized:
  Thread-1 locks → reads → writes → unlocks
  Thread-2 WAITS until Thread-1 unlocks        ← BLOCKING (slow under contention)

AtomicLong (CAS):
  Thread-1 reads (5) → tries to write (4) → succeeds
  Thread-2 reads (5) → tries to write (4) → FAILS (value is now 4)
  Thread-2 reads (4) → tries to write (3) → succeeds   ← NON-BLOCKING (fast)
```

CAS (Compare-And-Set) is a CPU-level atomic instruction. It never blocks — it just retries.
This is crucial for a gateway handling thousands of concurrent requests.

**Production Note:** This in-memory rate limiter works for a SINGLE gateway instance.
In production with multiple gateway instances, you'd use Redis-based rate limiting
(Spring Cloud Gateway has built-in support via `RequestRateLimiter` + Redis).

---

## 5. Circuit Breaker & Fallback

### What Happens When a Service Is Down?

Without a circuit breaker, the gateway waits indefinitely for the dead service to respond.
The client's request hangs, the gateway accumulates waiting connections, and eventually
the gateway itself crashes. This is called a **cascading failure**.

### How the Circuit Breaker Works

```
CLOSED (normal)                    OPEN (tripped)                  HALF-OPEN (testing)
  │                                  │                               │
  │  Requests flow through           │  ALL requests immediately     │  Allow a FEW test
  │  normally                        │  fail with fallback           │  requests through
  │                                  │  (no waiting!)                │
  │  If failures > 50%               │                               │  If test succeeds
  │  in last 5 calls:                │  After 10 seconds:            │  → back to CLOSED
  │  → switch to OPEN                │  → switch to HALF-OPEN        │  If test fails
  │                                  │                               │  → back to OPEN
```

### Gateway Configuration

```properties
# If a service is down, fail fast with a fallback instead of hanging
spring.cloud.gateway.default-filters[0]=CircuitBreaker=name=defaultCircuitBreaker,fallbackUri=forward:/fallback
```

### Fallback Controller

```java
@RestController
public class FallbackController {

    @GetMapping("/fallback")
    @PostMapping("/fallback")
    @PutMapping("/fallback")
    @DeleteMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Service is temporarily unavailable. Please try again later.");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
```

When the circuit breaker trips, instead of the client getting a raw connection error,
they get a clean JSON response:

```json
{
    "success": false,
    "message": "Service is temporarily unavailable. Please try again later.",
    "timestamp": "2026-03-10T06:30:00"
}
```

---

## 6. Config Server — Centralized Configuration

### The Problem with Local Configuration

Before the config server, every service had its own `application.properties`:

```
user-service/application.properties:     eureka.client.service-url.defaultZone=http://eureka:eureka123@localhost:8761/eureka
product-service/application.properties:  eureka.client.service-url.defaultZone=http://eureka:eureka123@localhost:8761/eureka
order-service/application.properties:    eureka.client.service-url.defaultZone=http://eureka:eureka123@localhost:8761/eureka
payment-service/application.properties:  eureka.client.service-url.defaultZone=http://eureka:eureka123@localhost:8761/eureka
notification-service/application.properties: eureka.client.service-url.defaultZone=http://eureka:eureka123@localhost:8761/eureka
```

**Same URL duplicated 5 times.** Change the Eureka password? Edit 5 files.

### How Config Server Solves This

```
config-repo/
├── application.properties          ← Shared by ALL services
│   (Eureka URL, Kafka, Zipkin, logging pattern)
│
├── user-service.properties         ← Only for user-service
│   (port 8081, SSL, JWT, database URL)
│
├── product-service.properties      ← Only for product-service
│   (port 8082, database URL)
│
├── order-service.properties        ← Only for order-service
│   (port 8083, database URL, circuit breaker)
│
├── payment-service.properties      ← Only for payment-service
│   (port 8084, database URL, retry config)
│
├── notification-service.properties ← Only for notification-service
│   (port 8085, Kafka consumer config)
│
└── api-gateway.properties          ← Only for api-gateway
    (port 8060, route definitions)
```

**How it works at runtime:**

```
user-service starts up:
  1. Reads local application.properties
  2. Sees: spring.config.import=optional:configserver:http://localhost:8888
  3. Calls Config Server: GET http://localhost:8888/user-service/default
  4. Config Server returns: user-service.properties + application.properties
  5. user-service merges remote config with local config
  6. LOCAL properties override remote ones (local always wins)
```

### The "optional:" Prefix

```properties
# Without "optional:" — if config server is down, service CRASHES on startup
spring.config.import=configserver:http://localhost:8888

# With "optional:" — if config server is down, use local properties and continue
spring.config.import=optional:configserver:http://localhost:8888
```

We use `optional:` because:
- Tests run without the config server
- Individual services can start without the full infrastructure
- The config server itself doesn't need to fetch from itself

### Config Server Application

```java
@SpringBootApplication
@EnableConfigServer     // ← Activates the config server endpoints
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

### Config Server Backend

```properties
# Use local filesystem (for development)
spring.profiles.active=native
spring.cloud.config.server.native.search-locations=file:${CONFIG_DIR:${user.home}/ecommerce-platform/config-repo}
```

The config server supports two backends:

| Backend | How It Works | When to Use |
|---------|-------------|-------------|
| **native** (filesystem) | Reads `.properties` files from a local folder | Development |
| **git** | Reads from a Git repository | Production (version control, audit trail) |

### Config Priority (What Overrides What)

```
Highest priority (wins)
  ↓ Environment variables (DB_URL=..., JWT_SECRET=...)
  ↓ Local application.properties (in each service)
  ↓ Config Server: service-specific file (user-service.properties)
  ↓ Config Server: shared file (application.properties)
Lowest priority
```

This means: if both the config server and the local file define `server.port`,
the LOCAL value wins. Environment variables always override everything.

### Gotcha: spring.profiles.active

One property that **CANNOT** be set in the config server: `spring.profiles.active`.
Spring Boot must know the profile BEFORE it fetches remote configuration (because the
profile determines which remote files to fetch). This must stay in the local
`application.properties`:

```properties
# This MUST be local — not in the config server
spring.profiles.active=dev
```

---

## 7. Distributed Tracing with Zipkin

### The Problem

A single "place order" request touches 5 services:

```
Gateway → order-service → user-service (validate user)
                        → product-service (reserve stock)
                        → payment-service (charge card)
                        → notification-service (via Kafka)
```

When something fails, which service caused it? How long did each service take?
Without tracing, you'd have to check logs across 5 different services manually.

### How Zipkin Tracing Works

```
Step 1: Gateway receives request, generates a TRACE ID
        traceId = "abc123"

Step 2: Gateway forwards to order-service, passes traceId in HTTP header
        Header: X-B3-TraceId: abc123

Step 3: order-service calls user-service, passes SAME traceId
        Header: X-B3-TraceId: abc123

Step 4: Each service reports its timing data (called a "span") to Zipkin:
        Gateway:         span{traceId=abc123, service=gateway, duration=500ms}
        order-service:   span{traceId=abc123, service=order,   duration=400ms}
        user-service:    span{traceId=abc123, service=user,    duration=50ms}

Step 5: Zipkin assembles all spans into a timeline:

        ┌─ api-gateway ─────────────────────────────────────────┐  500ms
        │  ┌─ order-service ──────────────────────────────────┐ │  400ms
        │  │  ┌─ user-service ──────┐                         │ │   50ms
        │  │  └─────────────────────┘                         │ │
        │  │              ┌─ product-service ──────┐          │ │   80ms
        │  │              └────────────────────────┘          │ │
        │  └──────────────────────────────────────────────────┘ │
        └───────────────────────────────────────────────────────┘
```

### Key Terms

| Term | What It Means | Analogy |
|------|---------------|---------|
| **Trace** | The entire journey of one request across all services | A FedEx tracking number |
| **Span** | One "hop" in the journey (one service's work) | One stop on the delivery route |
| **traceId** | Unique ID shared by all spans in one trace | The tracking number itself |
| **spanId** | Unique ID for each individual span | Each stop's receipt number |
| **parentSpanId** | The span that called this span | "Referred by" |

### Configuration

```properties
# Send 100% of traces to Zipkin (in production, reduce to 10-20% to save bandwidth)
management.tracing.sampling.probability=1.0

# Zipkin server URL
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

### What the Dependencies Do

```xml
<!-- Micrometer Tracing with Brave -->
<!-- Brave is the tracing library (originally from Twitter/Zipkin).
     It automatically:
     1. Generates a unique traceId for each incoming request
     2. Adds the traceId to every log line via SLF4J MDC
     3. Propagates the traceId to downstream services in HTTP headers -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin Reporter — sends span data to the Zipkin server via HTTP -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

### Viewing Traces in Zipkin

Open **http://localhost:9411** in your browser:

1. Select a service from the dropdown
2. Click "Run Query"
3. Click on a trace to see the full timeline
4. See which services were called, how long each took, and where errors occurred

---

## 8. Structured Logging with Correlation IDs

### The Problem

Without correlation IDs, logs from different services are impossible to connect:

```
[user-service]         INFO UserService - User registered: john@example.com
[notification-service] INFO Consumer - Sending welcome email to john@example.com
[order-service]        INFO OrderService - Order created for user 1
[payment-service]      INFO PaymentService - Payment processed for order 1
```

Which registration triggered which notification? Which order triggered which payment?
If 100 users are registering simultaneously, these logs are useless.

### The Solution

Every log line includes `[service-name, traceId, spanId]`:

```
[user-service,    abc123, def456] INFO UserService - User registered: john@example.com
[notification-service, abc123, ghi789] INFO Consumer - Sending welcome email
```

**Same traceId (`abc123`) = same request.** Search for `abc123` across all logs and you
see the entire journey.

### Log Pattern Configuration

```properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [${spring.application.name}, %X{traceId:-}, %X{spanId:-}] %-5level %logger{36} - %msg%n
```

**Breaking down the pattern:**

| Part | Meaning | Example Output |
|------|---------|----------------|
| `%d{yyyy-MM-dd HH:mm:ss}` | Timestamp | `2026-03-10 06:34:38` |
| `${spring.application.name}` | Service name | `api-gateway` |
| `%X{traceId:-}` | Trace ID from MDC (empty if none) | `69af9f5e2e732934...` |
| `%X{spanId:-}` | Span ID from MDC (empty if none) | `14c3dbd7eeb29c0e` |
| `%-5level` | Log level, left-padded to 5 chars | `INFO ` |
| `%logger{36}` | Class name, max 36 chars | `c.e.gateway.config.LoggingFilter` |
| `%msg%n` | The actual message + newline | `>>> Gateway Request: POST /api/auth/register` |

### Real Example from Our Platform

```
2026-03-10 06:34:38 [api-gateway,    69af9f5e2e73293414c3dbd7eeb29c0e, 14c3dbd7eeb29c0e] INFO  LoggingFilter - >>> Gateway Request: POST /api/auth/register
2026-03-10 06:34:38 [user-service,   69af9f5e2e73293414c3dbd7eeb29c0e, c8a10c87aeec2b1c] INFO  UserService   - Published UserRegisteredEvent for user 15 to Kafka
2026-03-10 06:34:38 [api-gateway,    69af9f5e2e73293414c3dbd7eeb29c0e, 14c3dbd7eeb29c0e] INFO  LoggingFilter - <<< Gateway Response: 201 CREATED
```

Notice:
- **Same traceId** across both services → this is ONE user registration
- **Different spanIds** → each service creates its own span within the trace
- You can grep for `69af9f5e2e73293414c3dbd7eeb29c0e` across ALL service logs

### What Is MDC (Mapped Diagnostic Context)?

MDC is a feature of SLF4J (the logging framework). It's a thread-local map where you
can store key-value pairs that automatically appear in every log line:

```java
// Micrometer Tracing does this automatically:
MDC.put("traceId", "69af9f5e...");
MDC.put("spanId", "14c3dbd7...");

// Now every log.info(), log.error(), etc. includes these values
log.info("User registered");
// Output: [service, 69af9f5e..., 14c3dbd7...] User registered
```

You never have to manually set MDC values — Micrometer Tracing handles it automatically.

---

## 9. SSL/HTTPS Routing Through the Gateway

### The Problem

user-service uses HTTPS with a self-signed certificate. When the gateway tries to
route to it, it gets rejected because the certificate isn't trusted (just like a
browser showing a security warning).

### The Solution: SslConfig

```java
@Configuration
public class SslConfig {
    @Bean
    public HttpClient httpClient() throws SSLException {
        return HttpClient.create()
                .secure(ssl -> {
                    try {
                        ssl.sslContext(SslContextBuilder.forClient()
                                // Trust ALL certificates (including self-signed)
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build());
                    } catch (SSLException e) {
                        throw new RuntimeException("Failed to configure SSL", e);
                    }
                });
    }
}
```

**WARNING:** `InsecureTrustManagerFactory` trusts ANY certificate — even malicious ones.
This is ONLY acceptable in development. In production, you would:
1. Use a real certificate from Let's Encrypt or AWS ACM
2. Or add the self-signed cert to a trust store

### Eureka HTTPS Registration

user-service must also tell Eureka it uses HTTPS, so the gateway connects on the
secure port:

```properties
# user-service tells Eureka: "I use HTTPS on port 8081"
eureka.instance.secure-port-enabled=true
eureka.instance.secure-port=${server.port}
eureka.instance.non-secure-port-enabled=false
```

Without this, Eureka would tell the gateway to connect via HTTP, and user-service
would reject the connection with "This combination of host and port requires TLS."

---

## 10. How Everything Connects

### Startup Order

```
1. Infrastructure (Docker)
   ├── PostgreSQL databases (4 instances)
   ├── Kafka + Zookeeper
   └── Zipkin

2. Discovery Server (Eureka)
   └── Must start FIRST — everyone else registers with it

3. Config Server
   └── Registers with Eureka, serves configuration

4. API Gateway
   └── Registers with Eureka, discovers other services

5. Business Services (any order)
   ├── user-service      → registers with Eureka, fetches config
   ├── product-service   → registers with Eureka, fetches config
   ├── order-service     → registers with Eureka, fetches config
   ├── payment-service   → registers with Eureka, fetches config
   └── notification-service → registers with Eureka, fetches config
```

### Full Request Flow (Register User Through Gateway)

```
Client: POST http://localhost:8060/api/auth/register

1. [API Gateway] RateLimitFilter
   → Check token bucket for client IP
   → Token available → continue

2. [API Gateway] LoggingFilter
   → Log: ">>> POST /api/auth/register from 127.0.0.1"

3. [API Gateway] Route Matching
   → Path /api/auth/** matches route[0] → lb:https://user-service

4. [API Gateway] Eureka Lookup
   → Ask Eureka: "Where is user-service?"
   → Eureka: "192.168.1.5:8081 (HTTPS)"

5. [API Gateway] Forward Request
   → POST https://192.168.1.5:8081/api/auth/register
   → SslConfig trusts the self-signed cert
   → Passes traceId in X-B3-TraceId header

6. [User Service] Process Registration
   → Validate input, hash password, save to DB
   → Publish UserRegisteredEvent to Kafka
   → Report span to Zipkin (same traceId)

7. [API Gateway] Return Response
   → 201 Created with JWT tokens
   → Log: "<<< 201 CREATED for POST /api/auth/register"
   → Report span to Zipkin (same traceId)

8. [Notification Service] (async, via Kafka)
   → Consumes UserRegisteredEvent
   → Logs welcome notification
```

---

## 11. New Java Concepts Learned

### ConcurrentHashMap

Thread-safe map that allows multiple threads to read and write simultaneously without
locking the entire map:

```java
// Regular HashMap — NOT thread-safe. Two threads writing simultaneously = data corruption
Map<String, TokenBucket> buckets = new HashMap<>();

// ConcurrentHashMap — thread-safe. Uses fine-grained locking (locks individual segments)
ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

// computeIfAbsent — atomically: "if key doesn't exist, create it"
bucket = buckets.computeIfAbsent(clientIp, ip -> new TokenBucket(20, 10));
```

### AtomicLong and Compare-And-Set (CAS)

Lock-free concurrency primitive. Uses CPU-level atomic instructions:

```java
AtomicLong counter = new AtomicLong(10);

// get() — read the current value
long current = counter.get();  // 10

// compareAndSet(expected, new) — "if value is still 10, set it to 9"
boolean success = counter.compareAndSet(10, 9);  // true, value is now 9
boolean failed  = counter.compareAndSet(10, 8);  // false, value is 9 not 10
```

### Reactor Mono and Reactive Programming

Spring Cloud Gateway uses Project Reactor for non-blocking I/O:

```java
// Mono<Void> = "a task that will complete eventually, returning nothing"
// Think of it like a Promise in JavaScript

Mono<Void> result = chain.filter(exchange)       // Forward request
    .then(Mono.fromRunnable(() -> {              // AFTER response comes back
        log.info("Response: {}", exchange.getResponse().getStatusCode());
    }));
```

### GlobalFilter vs GatewayFilter

```
GlobalFilter     — runs on ALL routes (we use this for logging and rate limiting)
GatewayFilter    — runs on specific routes only (configured per route)
```

### @Configuration and @Bean

```java
@Configuration  // "This class defines beans (objects managed by Spring)"
public class SslConfig {

    @Bean  // "Spring: create this object and manage it. Anyone who needs HttpClient gets this one."
    public HttpClient httpClient() {
        return HttpClient.create().secure(...);
    }
}
```

---

## 12. Files Created & Modified

### New Services Created

| Service | Files | Purpose |
|---------|-------|---------|
| **discovery-server/** | DiscoveryServerApplication.java, SecurityConfig.java, application.properties, pom.xml, Dockerfile, .dockerignore | Eureka service registry |
| **config-server/** | ConfigServerApplication.java, application.properties, pom.xml, Dockerfile, .dockerignore | Centralized configuration |
| **api-gateway/** | ApiGatewayApplication.java, LoggingFilter.java, RateLimitFilter.java, SslConfig.java, FallbackController.java, application.properties, pom.xml, Dockerfile, .dockerignore | API Gateway with routing, rate limiting, circuit breaker |
| **config-repo/** | application.properties, user-service.properties, product-service.properties, order-service.properties, payment-service.properties, notification-service.properties, api-gateway.properties | Centralized config files |

### Existing Services Modified

Every business service (user, product, order, payment, notification) had these changes:

| File | Change |
|------|--------|
| `pom.xml` | Added: spring-cloud-dependencies BOM, eureka-client, spring-cloud-config, micrometer-tracing-bridge-brave, zipkin-reporter-brave |
| `application.properties` | Added: config server import, Eureka registration, distributed tracing config, structured log pattern |

### Infrastructure Updated

| File | Change |
|------|--------|
| `docker-compose.infra.yml` | Added: Zipkin container (port 9411) |
| `docker-compose.yml` | Added: discovery-server, config-server, api-gateway, zipkin. Updated all services with Eureka/Zipkin/config env vars |
| `Makefile` | Added: run-discovery, run-config, run-gateway. Updated: build-all, clean-all, run-all |
| `README.md` | Full rewrite with gateway-centric architecture, new commands, new services |

---

## 13. Port Reference

| Service | Port | Protocol | Access |
|---------|------|----------|--------|
| **API Gateway** | 8060 | HTTP | All client traffic goes here |
| Discovery Server (Eureka) | 8761 | HTTP | Dashboard: http://localhost:8761 |
| Config Server | 8888 | HTTP | API: http://localhost:8888/{service}/default |
| user-service | 8081 | HTTPS | Direct: https://localhost:8081 |
| product-service | 8082 | HTTP | Direct: http://localhost:8082 |
| order-service | 8083 | HTTP | Direct: http://localhost:8083 |
| payment-service | 8084 | HTTP | Direct: http://localhost:8084 |
| notification-service | 8085 | HTTP | Direct: http://localhost:8085 |
| Zipkin | 9411 | HTTP | UI: http://localhost:9411 |
| Kafka | 9092 | TCP | localhost:9092 (host), kafka:29092 (Docker) |
| Kafka UI | 8090 | HTTP | http://localhost:8090 |
| pgAdmin | 5050 | HTTP | http://localhost:5050 |
| PostgreSQL (users) | 5432 | TCP | |
| PostgreSQL (products) | 5436 | TCP | |
| PostgreSQL (orders) | 5434 | TCP | |
| PostgreSQL (payments) | 5435 | TCP | |

---

## 14. Common Issues & Fixes

### "This combination of host and port requires TLS"

**Cause:** Gateway connecting to user-service via HTTP, but user-service requires HTTPS.

**Fix:**
1. Route must use `lb:https://user-service` (not `lb://user-service`)
2. user-service must register with Eureka using secure port:
   ```properties
   eureka.instance.secure-port-enabled=true
   eureka.instance.secure-port=${server.port}
   eureka.instance.non-secure-port-enabled=false
   ```
3. Gateway needs SslConfig with InsecureTrustManagerFactory

### "Property 'spring.profiles.active' is invalid in a profile specific resource"

**Cause:** `spring.profiles.active` was set in a config server file.

**Fix:** Remove it from config-repo and keep it only in the service's local application.properties.

### TraceId is empty in gateway logs

**Cause:** Gateway uses WebFlux (reactive). Trace context is stored in ThreadLocal, which
doesn't propagate across Reactor threads.

**Fix:** Add `Hooks.enableAutomaticContextPropagation()` in the gateway's main method.

### Gateway returns 504 on first request

**Cause:** Eureka cache needs time to sync. The first request may arrive before the gateway
has discovered all services.

**Fix:** Wait a few seconds after all services start, or retry the request. The second
request usually works because Eureka has synced by then.

### Services can't find config server

**Cause:** Config server isn't running or service can't reach it.

**Fix:** Use `optional:configserver:` prefix so services fall back to local properties
instead of crashing.

---

## Summary

Phase 7 transformed our platform from 5 independent services into a properly orchestrated
microservices architecture:

| Before | After |
|--------|-------|
| Clients must know every service address | One gateway address for everything |
| No service discovery | Eureka registry, services find each other by name |
| Config duplicated across 5 services | Centralized in config-repo |
| Can't trace requests across services | Zipkin shows the full journey |
| No rate limiting | Token bucket per IP (20 burst, 10/sec sustained) |
| No circuit breaker at entry point | Gateway fails fast with clean JSON fallback |
| Plain log lines with no context | Structured logs with traceId for correlation |

**Total test count: 98** (all passing after Phase 7 changes)

**Services: 8** (5 business + discovery-server + config-server + api-gateway)
