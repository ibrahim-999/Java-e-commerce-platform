# E-Commerce Platform

[![CI — Test & Validate](https://github.com/ibrahim-999/Java-e-commerce-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ibrahim-999/Java-e-commerce-platform/actions/workflows/ci.yml)
[![CD — Build & Push Images](https://github.com/ibrahim-999/Java-e-commerce-platform/actions/workflows/cd.yml/badge.svg)](https://github.com/ibrahim-999/Java-e-commerce-platform/actions/workflows/cd.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.3-green)
![Spring Cloud 2024.0](https://img.shields.io/badge/Spring%20Cloud-2024.0.0-green)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5-231F20)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Ready-326CE5)
![Coverage 80%+](https://img.shields.io/badge/Coverage-80%25%2B-brightgreen)
![Tests 300+](https://img.shields.io/badge/Tests-300%2B-brightgreen)

A production-grade microservices e-commerce platform built with Java 21 and Spring Boot 3.4.

Eight independent services communicate over REST and Kafka, orchestrated through an API Gateway with Eureka service discovery, centralized configuration, distributed tracing, and full observability via Prometheus and Grafana. Deployable via Docker Compose or Kubernetes.

---

## Architecture

```
                                ┌──────────────┐
                                │    Client    │
                                └──────┬───────┘
                                       │
                              ┌────────▼────────┐
                              │   API Gateway   │
                              │     :8060       │
                              │                 │
                              │  Rate Limiting  │
                              │  Circuit Breaker│
                              │  Request Logging│
                              └───┬───┬───┬───┬─┘
                                  │   │   │   │
         ┌────────────────────────┘   │   │   └────────────────────┐
         │              ┌─────────────┘   └──────────┐             │
         ▼              ▼                            ▼             ▼
 ┌───────────────┐ ┌────────────────┐ ┌──────────────────┐ ┌──────────────┐
 │ user-service  │ │product-service │ │  order-service   │ │payment-service│
 │ :8081 (HTTPS) │ │    :8082      │ │     :8083        │ │    :8084     │
 │               │ │               │ │                  │ │              │
 │ Registration  │ │ Products      │ │ Saga orchestrator│ │ Stripe/PayPal│
 │ Login (JWT)   │ │ Categories    │ │ User validation  │ │ Bank gateway │
 │ User mgmt     │ │ Inventory     │ │ Stock reservation│ │ Retry+backoff│
 │ Role-based    │ │ Stock ops     │ │ Payment trigger  │ │ Transaction  │
 │ access ctrl   │ │ Price stats   │ │ Circuit Breaker  │ │ audit trail  │
 └───────┬───────┘ └───────┬───────┘ └────────┬─────────┘ └──────┬───────┘
    ▼         ▼         ▼                      ▼                  ▼
 [Redis]   [Redis]  [Postgres]             [Postgres]          [Postgres]
 [Postgres]         ×4 DBs (database-per-service)
         │                 │                   │                  │
         ▼                 ▼                   ▼                  ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │                       Apache Kafka :9092                             │
 │       Topics: user-events | order-events | payment-events           │
 │       Dead Letter Topics: *-dlt (for failed message investigation)  │
 └──────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
                   ┌──────────────────────┐
                   │notification-service  │
                   │       :8085          │
                   │                      │
                   │ Consumes all events  │
                   │ Sends notifications  │
                   │ Dead Letter Topics   │
                   └──────────────────────┘

 ┌───────────────────────────────────────────────────────────────────┐
 │                    Platform Services                              │
 │                                                                   │
 │  Discovery Server (Eureka) :8761  — service registry              │
 │  Config Server :8888              — centralized configuration     │
 │  Zipkin :9411                     — distributed tracing UI        │
 │  Prometheus :9090                 — metrics collection            │
 │  Grafana :3000                    — monitoring dashboards         │
 └───────────────────────────────────────────────────────────────────┘
```

Each service owns its own database (database-per-service pattern):

| Service | Database | Port |
|---------|----------|------|
| user-service | `users_db` | 5432 |
| product-service | `products_db` | 5436 |
| order-service | `orders_db` | 5434 |
| payment-service | `payments_db` | 5435 |
| notification-service | None (Kafka only) | — |

---

## Technology Stack

### Core

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Java** | 21 (Temurin LTS) | Language runtime. Latest LTS release. | Virtual threads, pattern matching, records, strong enterprise ecosystem. | All 8 services compile to Java 21 bytecode via Maven. |
| **Spring Boot** | 3.4.3 | Auto-configured framework with embedded Tomcat, dependency injection, and convention-over-config. | Eliminates boilerplate — focus on business logic, not XML config. De-facto standard for Java microservices. | Each service is a standalone `@SpringBootApplication` JAR with embedded web server. |
| **Spring Cloud** | 2024.0.0 | Cloud-native toolkit: service discovery, config management, gateway routing, circuit breakers. | Battle-tested solutions for distributed systems — no need to reinvent service discovery or config management. | `dependencyManagement` BOM imported in each service's pom.xml. |
| **Maven** | 3.6+ | Build tool and dependency manager. Compiles, tests, packages JARs, runs plugins. | Reproducible builds with declarative pom.xml. Same dependency tree on every machine and CI server. | `mvn clean package` builds JARs; `mvn test` runs tests with JaCoCo + Checkstyle enforcement. |
| **Lombok** | (managed) | Generates boilerplate (`@Getter`, `@Builder`, `@Slf4j`) at compile time. | Reduces entity/DTO classes from 100+ lines to ~20 lines. | Annotation processor configured in pom.xml, excluded from final JAR. |

### Data Layer

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **PostgreSQL** | 16 | Relational database. 4 isolated instances (database-per-service). | ACID transactions, JSON support, enforces loose coupling — services can't read each other's tables. | Each service connects via JDBC URL from env vars. Docker Compose runs 4 containers with health checks. |
| **Spring Data JPA / Hibernate** | (managed) | ORM that maps Java objects to tables. Generates repository implementations from interfaces. | Write `findByEmail(String email)`, get SQL for free. Eliminates 90% of manual SQL with type safety. | Repository interfaces extend `JpaRepository`. Hibernate validates schema on startup (`ddl-auto=validate`). |
| **Flyway** | (managed) | Database migration tool. Numbered SQL files (V1__init.sql) applied in order on startup. | Every environment gets the exact same schema. Migrations are version-controlled and immutable. | SQL files in `src/main/resources/db/migration/`. Runs automatically before Hibernate validation. |
| **Redis** | 7 (Alpine) | In-memory cache. Stores frequently-accessed data to avoid hitting PostgreSQL every request. | Sub-ms reads (~0.3ms vs ~5ms DB). Reduces DB load on read-heavy endpoints. | `@Cacheable`/`@CacheEvict` on user-service and product-service. JSON serialization with TTLs (5-15 min). |

### Messaging & Events

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Apache Kafka** | 7.5 (Confluent) | Event streaming platform. Producers publish to topics, consumers read async. Messages persist on disk. | Decouples services — events wait in Kafka until consumers are ready. Enables saga pattern coordination. | 3 topics: `user-events`, `order-events`, `payment-events`. Each has a Dead Letter Topic (`*-dlt`). |
| **Spring Kafka** | (managed) | Spring integration for Kafka. `KafkaTemplate` for producing, `@KafkaListener` for consuming. | Abstracts low-level Kafka API. Built-in DLT support routes failed messages automatically. | Producers use `KafkaTemplate.send()`, consumers use `@KafkaListener` with consumer group IDs. |
| **Zookeeper** | 7.5 (Confluent) | Manages Kafka cluster metadata — broker health, partition leaders, consumer offsets. | Required by Confluent Kafka distribution. Acts as the cluster coordinator. | Runs as a Docker container. Kafka connects via `zookeeper:2181`. |

### Service Discovery & Configuration

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Netflix Eureka** | (Spring Cloud) | Service registry. Services register on startup, send heartbeats every 30s. | No hardcoded URLs. Gateway finds services automatically. Enables horizontal scaling. | `@EnableEurekaServer` on discovery-server. All services include `eureka-client` dependency and register on boot. |
| **Spring Cloud Config** | (Spring Cloud) | Centralized configuration. Services fetch properties from config server on startup. | Change config in one place, restart the service, done. Single source of truth. | `@EnableConfigServer` serves files from `config-repo/`. Services import via `spring.config.import`. |
| **Spring Cloud Gateway** | (Spring Cloud) | Reactive API gateway. Routes requests to services via Eureka. Handles cross-cutting concerns. | Single entry point (`:8060`). Clients never need internal service addresses. | Route rules in `application.properties`. Custom `LoggingFilter` and `RateLimitFilter` as global filters. |

### Security

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Spring Security** | (managed) | Auth framework. Role-based access control, filter chains, password encoding. | Declarative security — `@PreAuthorize("hasRole('ADMIN')")` protects endpoints. | `SecurityFilterChain` bean configures public vs protected paths. `JwtAuthenticationFilter` validates tokens. |
| **JJWT** | 0.12.6 | JWT library. Generates/validates signed tokens. Access (15 min) + refresh (7 day). | Stateless auth — no server-side sessions. Any instance can validate tokens. Scales horizontally. | `JwtService` signs with HS256. `JwtAuthenticationFilter` extracts Bearer token from Authorization header. |
| **BCrypt** | (Spring Security) | Password hashing with automatic salting. | Passwords never stored as plaintext. Work factor makes brute-force expensive. | `BCryptPasswordEncoder` bean used in registration and login flows. |
| **HTTPS / TLS** | PKCS12 | user-service runs HTTPS with self-signed cert. Encrypts traffic in transit. | Protects JWTs and credentials from network sniffing. K8s uses Ingress TLS instead. | `keystore.p12` in resources. `server.ssl.*` properties configure the embedded Tomcat. |

### Resilience

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Resilience4j** | 2.2.0 | Circuit breakers, retries, and rate limiters as lightweight decorators. | Prevents cascading failures when services are down. Retries transient payment errors. | `@CircuitBreaker` on order-service (5-call window, 50% threshold). `@Retry` on payment-service (3 attempts, exponential backoff). |
| **WebClient** | (managed) | Non-blocking HTTP client for inter-service REST calls. | Thread isn't held during network round-trips. Prevents thread exhaustion with circuit breakers. | order-service calls user/product/payment services via `WebClient.Builder` with Resilience4j decorators. |

### Observability

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Micrometer** | (managed) | Metrics facade. Instruments code and exports to monitoring systems. | JVM memory, HTTP rates, DB pool, cache hit ratios — all automatic. Vendor-neutral. | `micrometer-registry-prometheus` dependency exposes `/actuator/prometheus` on all 8 services. |
| **Prometheus** | latest | Time-series DB. Pulls (scrapes) metrics from services every 15 seconds. | Answers "what is happening right now?" across all services. Pull-based — services don't push. | `prometheus.yml` defines 8 scrape jobs. Runs as Docker container on `:9090`. |
| **Grafana** | latest | Dashboarding tool. Visualizes Prometheus data as real-time graphs and gauges. | Answers "is anything broken?" at a glance with a pre-built 10-panel dashboard. | Auto-provisioned datasource + dashboard JSON on startup. Runs on `:3000` (admin/admin). |
| **Zipkin** | latest | Distributed tracing. Visualizes request journey across services as a span timeline. | Shows the full call chain and where latency/errors occur across service hops. | Runs on `:9411`. Services report spans via `zipkin-reporter-brave`. 100% sampling in dev. |
| **Micrometer Tracing (Brave)** | (managed) | Bridges tracing API to Zipkin. Auto-generates trace IDs, propagates via HTTP headers. | Zero-code instrumentation — every call gets a traceId in logs and Zipkin automatically. | `micrometer-tracing-bridge-brave` dependency. Log pattern includes `[service, traceId, spanId]`. |

### Testing

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **JUnit 5** | (managed) | Java testing framework. `@Test`, assertions, lifecycle hooks, parameterized tests. | Industry standard. Extensible with Spring Boot, Testcontainers, and Mockito. | All test classes use `@SpringBootTest` or `@WebMvcTest` with JUnit 5 runners. |
| **Mockito** | (managed) | Mocking framework. Creates fake dependencies for isolated unit tests. | Test OrderService without calling payment-service or writing to the DB. | `@MockBean` injects mocks into Spring context. `when().thenReturn()` stubs behavior. |
| **MockMvc** | (managed) | Simulates HTTP requests to controllers without starting a real server. | Test REST layer (routing, validation, serialization) in milliseconds. | `mockMvc.perform(get("/api/products")).andExpect(status().isOk())` in `@WebMvcTest` classes. |
| **AssertJ** | (managed) | Fluent assertion library with rich error messages. | More readable than JUnit assertions. Better collection/exception matchers. | `assertThat(order.getStatus()).isEqualTo(CONFIRMED)` throughout all test suites. |
| **Testcontainers** | 2.0.3 (BOM) | Spins up real Docker containers (PostgreSQL, Kafka) during tests. | No H2 shortcuts. Tests hit real PostgreSQL with real Flyway migrations. | `@Container` annotation starts PostgreSQL. `@DynamicPropertySource` wires the JDBC URL. |
| **DataFaker** | 2.4.2 | Generates realistic test data (names, emails, phones). | Catches edge cases (special chars, long strings) that "test123" misses. | `new Faker().name().fullName()` in test fixtures and builders. |
| **JaCoCo** | 0.8.12 | Code coverage tool. Enforces 80% minimum line coverage. Build fails if below. | Prevents coverage regressions without chasing 100% on trivial code. | Maven plugin runs during `test` phase. Reports uploaded as CI artifacts. |
| **Checkstyle** | 3.6.0 (plugin) | Linter. Enforces naming conventions, import rules, no empty catch blocks, line length. | Consistent code style across all services. Caught at build time, not code review. | Shared `checkstyle.xml` at project root. `mvn checkstyle:check` in CI pipeline. |
| **Spring Kafka Test** | (managed) | Embedded Kafka broker for testing consumers/producers without external cluster. | notification-service tests verify event logic without Docker infra. | `@EmbeddedKafka` annotation starts an in-process Kafka broker for the test. |

### API Documentation

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **SpringDoc OpenAPI** | 2.8.5 | Auto-generates OpenAPI 3.0 specs and Swagger UI from controllers and DTOs. | Living docs that stay in sync with code. Explore and test endpoints in browser. | Dependency added to user, product, order, payment services. UI at `/swagger-ui.html`. |

### Infrastructure & Deployment

| Technology | Version | What | Why | How |
|-----------|---------|------|-----|-----|
| **Docker** | - | Packages each service as a container image with JDK + JAR. | "Works on my machine" → "Works everywhere." Identical behavior across all environments. | Multi-stage Dockerfile per service: Maven build stage → minimal JRE runtime stage. |
| **Docker Compose** | - | Orchestrates all 16 containers with one command. | Defines dependencies, health checks, networking, volumes, and env vars declaratively. | `docker-compose.yml` (full stack) + `docker-compose.infra.yml` (DBs + Kafka + Redis only). |
| **Kubernetes** | - | Container orchestration. 20 manifests: deployments, services, secrets, ingress, health probes. | Horizontal scaling, rolling updates, self-healing, declarative infrastructure. | `k8s/` directory with `deploy.sh` (deploy/teardown/status) and `test-k8s.sh` (scale/rolling). |
| **GitHub Actions** | - | CI/CD pipeline. Tests 5 services in parallel, enforces coverage + linting, builds images. | Automated quality gates. Docker images built and pushed on every merge to main. | `ci.yml` (matrix strategy for parallel tests) + `cd.yml` (build & push to Docker Hub). |
| **Docker Hub** | - | Public container registry. Stores versioned images tagged with `latest` and git SHA. | Any cluster can deploy by pulling images. SHA tags enable instant rollback. | CD pipeline pushes `ibmid99/<service>:latest` and `ibmid99/<service>:<sha>` on merge. |
| **pgAdmin** | 4 | Web-based PostgreSQL admin tool. Run queries, inspect schemas, manage data visually. | Visual DB debugging at `localhost:5050` without command-line psql. | Docker container connected to all 4 PostgreSQL instances on the same network. |
| **Kafka UI** | latest | Web dashboard for Kafka topics, messages, consumer groups, and offsets. | Inspect payloads, monitor lag, investigate DLTs visually at `localhost:8090`. | Docker container connected to Kafka broker via internal Docker network. |

---

## Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Saga (Orchestration)** | order-service orchestrates a 4-step distributed transaction: validate user → reserve stock → create order → process payment. If any step fails, compensating transactions (stock restoration, order cancellation) run automatically. Kafka reconciles async payment outcomes. | Distributed transactions can't use traditional DB transactions across services. The saga pattern guarantees data consistency by undoing completed steps when a later step fails. |
| **Strategy** | Payment gateways (`StripePaymentGateway`, `PayPalPaymentGateway`, `BankTransferGateway` all implement `PaymentGateway`). User search strategies (`EmailSearchStrategy`, `NameSearchStrategy`, `StatusSearchStrategy`). | Swap implementations without touching business logic. Adding a new payment provider means writing one class and registering it — zero changes to the payment processing code. |
| **Factory** | `PaymentGatewayFactory` maps `PaymentMethod` enum values to the correct gateway implementation using a lookup map built from Spring-injected beans. | Eliminates if/else chains. The factory auto-discovers all `PaymentGateway` implementations via Spring DI. |
| **Circuit Breaker** | order-service wraps calls to user-service, product-service, and payment-service with `@CircuitBreaker`. Config: 5-call sliding window, 50% failure rate threshold, 10s wait in open state. | If payment-service is down, order-service stops calling it after 5 failures and returns a fallback response instantly, instead of queueing up timeout-bound requests that would exhaust the thread pool. |
| **Database-per-Service** | 4 separate PostgreSQL instances: users_db, products_db, orders_db, payments_db. Services never share a database. | Enforces loose coupling at the data layer. Services evolve their schemas independently. No risk of one service's heavy query slowing down another service's database. |
| **Event-Driven / Pub-Sub** | Kafka topics decouple producers from consumers. user-service publishes `UserRegisteredEvent`, notification-service consumes it. Neither service knows about the other. | Adding a new consumer (e.g., analytics-service) requires zero changes to the producer. Messages persist in Kafka even if consumers are temporarily down. |
| **Dead Letter Queue** | Failed Kafka messages route to `*-dlt` topics (user-events-dlt, order-events-dlt, payment-events-dlt) instead of being discarded. | Poison messages (malformed data, schema mismatches) don't block the consumer loop. Operations can investigate and replay failed messages from the DLT. |
| **Cache-Aside** | `@Cacheable` checks Redis before querying PostgreSQL. `@CacheEvict` removes stale entries on writes. DTOs (not entities) are cached to avoid serialization issues. | Dramatically reduces database load for read-heavy endpoints. Product lookups go from ~5ms to ~0.3ms on cache hits. |
| **Token Bucket** | API Gateway rate limiter: 20-token capacity, 10 tokens/second refill rate, per-client-IP tracking. Returns HTTP 429 when exhausted. | Protects backend services from traffic spikes and abuse. Each client IP gets its own independent rate limit. |
| **API Gateway** | Single entry point on port 8060 routes all client traffic. Handles logging, rate limiting, and circuit breaking as cross-cutting concerns. | Clients interact with one URL instead of memorizing 5 service addresses. Security, monitoring, and traffic management happen in one place. |
| **Builder** | Lombok `@Builder` on DTOs and entities. Clean object construction with many optional fields. | `Order.builder().userId(1).status(PENDING).build()` is more readable and less error-prone than a 12-argument constructor. |

---

## Services

### user-service `:8081` (HTTPS)
Registration, login, JWT authentication, role-based access control, user CRUD. Publishes `UserRegisteredEvent` to Kafka. Redis caching for user profile lookups.

### product-service `:8082`
Product catalog, categories, inventory management, stock reduction/restoration, price statistics, paginated search. Redis caching with separate TTLs for individual products (10 min) and lists (5 min).

### order-service `:8083`
Order creation with full saga orchestration. Validates users, reserves stock, triggers payments, and handles compensating transactions on failure. Consumes `payment-events` for async reconciliation. Circuit breakers on all inter-service calls.

### payment-service `:8084`
Multi-gateway payment processing (Stripe, PayPal, Bank Transfer). Retry with exponential backoff (3 attempts). Full transaction audit trail. Publishes `PaymentProcessedEvent` to Kafka. Supports refunds.

### notification-service `:8085`
Event-driven Kafka consumer. Listens to `user-events`, `order-events`, and `payment-events`. Sends notifications (welcome emails, order confirmations, payment receipts). Routes failed messages to Dead Letter Topics.

### api-gateway `:8060`
Reactive Spring Cloud Gateway. Routes requests to services via Eureka discovery. Global filters for request logging, per-IP rate limiting (token bucket), and circuit breaking.

### discovery-server `:8761`
Netflix Eureka server. All services register on startup with periodic heartbeats. Web dashboard shows registered instances. Secured with basic auth.

### config-server `:8888`
Spring Cloud Config Server. Serves centralized configuration from the `config-repo/` directory. All services fetch their properties on startup.

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.6+
- Docker & Docker Compose

### Option 1: Development mode (recommended for learning)

Run databases in Docker, services locally:

```bash
# Start infrastructure (4 PostgreSQL DBs + pgAdmin + Kafka + Zookeeper + Redis + Zipkin)
make infra-up

# Start platform services first (each in a new terminal)
make run-discovery    # Eureka service registry (:8761)
# wait ~10 seconds for Eureka to start
make run-config       # Config server (:8888)
make run-gateway      # API Gateway (:8060)

# Start business services
make run-user         # user-service (:8081)
make run-product      # product-service (:8082)
make run-payment      # payment-service (:8084)
make run-order        # order-service (:8083)
make run-notification # notification-service (:8085)

# Or start everything at once:
make run-all
```

### Option 2: Full Docker stack

Everything runs in containers (16 total):

```bash
# Build and start all containers
make up

# Check status
make status

# View logs
make logs

# Stop everything
make down
```

### Option 3: Kubernetes

Deploy to any Kubernetes cluster:

```bash
cd k8s/

# Deploy everything
./deploy.sh deploy

# Check status
./deploy.sh status

# Test scaling and rolling updates
./test-k8s.sh

# Teardown
./deploy.sh teardown
```

### Verify it works

```bash
# All requests go through the API Gateway on port 8060

# Register a user
curl -s -X POST http://localhost:8060/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com",
       "password":"Pass12345","phoneNumber":"+201000000001"}'

# Browse products
curl -s http://localhost:8060/api/products
curl -s http://localhost:8060/api/categories

# Web UIs
open http://localhost:8761                        # Eureka Dashboard (eureka/eureka123)
open http://localhost:9411                        # Zipkin Tracing UI
open http://localhost:8090                        # Kafka UI
open http://localhost:5050                        # pgAdmin
open http://localhost:3000                        # Grafana (admin/admin)
open http://localhost:9090                        # Prometheus
```

---

## API Endpoints

All requests go through the **API Gateway** on port **8060**.

### User Service (`/api/users/**`, `/api/auth/**`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/register` | Register new user | Public |
| POST | `/api/auth/login` | Login, returns JWT | Public |
| POST | `/api/auth/refresh` | Refresh access token | Public |
| GET | `/api/users/{id}` | Get user by ID | Public |
| GET | `/api/users` | List all users (paginated) | ADMIN |
| GET | `/api/users/search` | Search by email/name/status | Authenticated |
| PUT | `/api/users/{id}` | Update user | Authenticated |
| DELETE | `/api/users/{id}` | Delete user | ADMIN |

### Product Service (`/api/products/**`, `/api/categories/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products` | Create product |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products` | List products (paginated) |
| GET | `/api/products/search?name=` | Search by name |
| GET | `/api/products/category/{id}` | Products by category |
| GET | `/api/products/stats` | Price statistics (AVG, MIN, MAX) |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product |
| GET | `/api/categories` | List all categories |

### Order Service (`/api/orders/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Create order (full saga: validate → reserve → pay) |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/api/orders/user/{userId}` | User's orders (paginated) |
| GET | `/api/orders/{id}/history` | Order status audit trail |
| PUT | `/api/orders/{id}/cancel` | Cancel order (restores stock) |
| POST | `/api/orders/{id}/retry-payment` | Retry failed payment |

### Payment Service (`/api/payments/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/payments` | Process payment |
| GET | `/api/payments/{id}` | Get payment by ID |
| GET | `/api/payments/order/{orderId}` | Get payment for order |
| GET | `/api/payments/user/{userId}` | User's payment history |
| GET | `/api/payments/{id}/transactions` | Transaction audit trail |
| PUT | `/api/payments/{id}/refund` | Refund a payment |

---

## Full E2E Flow

```bash
# 1. Register a user
curl -s -X POST http://localhost:8060/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com",
       "password":"Pass12345","phoneNumber":"+201000000001"}'

# 2. Create a product
curl -s -X POST http://localhost:8060/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"MacBook Pro","description":"Laptop","price":2499.99,
       "stockQuantity":10,"sku":"MBP-001","categoryId":1}'

# 3. Place an order (validates user → reserves stock → charges payment)
curl -s -X POST http://localhost:8060/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":1}],
       "paymentMethod":"CREDIT_CARD"}'

# 4. Check Zipkin to see the full distributed trace
open http://localhost:9411
```

---

## Testing

All services use **Testcontainers** (real PostgreSQL and Kafka in Docker containers) — no H2 in-memory shortcuts. JaCoCo enforces **80% minimum line coverage** on every service. Checkstyle enforces consistent code style.

```bash
# Run all tests
make test-all

# Run tests for a specific service
cd user-service && mvn test          # 75 tests  (84% coverage)
cd product-service && mvn test       # 45 tests  (80%+ coverage)
cd order-service && mvn test         # 79 tests  (81% coverage)
cd payment-service && mvn test       # 68 tests  (96% coverage)
cd notification-service && mvn test  # 49 tests  (98% coverage)
```

| Service | Tests | Coverage |
|---------|-------|----------|
| user-service | 75 | 84% |
| product-service | 45 | 80%+ |
| order-service | 79 | 81% |
| payment-service | 68 | 96% |
| notification-service | 49 | 98% |
| **Total** | **316** | **80%+ (enforced)** |

Tests use Testcontainers (Docker must be running). No external infrastructure needed.

---

## CI/CD Pipeline

### CI (every push/PR to main)

```
Pull Request → Checkout → Java 21 → Cache Maven → Tests (5 services in parallel)
                                                  → Checkstyle (5 services in parallel)
                                                  → Build Docker Images
```

- **Matrix strategy**: 5 services tested simultaneously on separate VMs
- **JaCoCo**: Build fails if any service drops below 80% line coverage
- **Checkstyle**: Build fails on style violations (naming, imports, formatting)
- **Docker build**: Validates all 8 Dockerfiles compile correctly
- Coverage reports uploaded as downloadable artifacts

### CD (on merge to main)

```
Merge to main → Build all 8 images → Push to Docker Hub (ibmid99/*)
```

- Images tagged with `latest` and git SHA (e.g., `ibmid99/user-service:abc1234`)
- Docker layer caching via GitHub Actions cache for fast rebuilds
- Git SHA tags enable instant rollback to any previous version

---

## Monitoring

### Prometheus (`:9090`)
Scrapes `/actuator/prometheus` from all 8 services every 15 seconds. Metrics include HTTP request counts, latencies, error rates, JVM memory, GC pauses, DB connection pool stats, and Redis cache hit ratios.

### Grafana (`:3000`)
Pre-configured 10-panel dashboard visualizing metrics from Prometheus. Login: `admin/admin`.

### Zipkin (`:9411`)
Distributed tracing UI. Shows the full request journey across services with timing data. All services inject traceId/spanId into log output for correlation.

---

## Project Structure

```
ecommerce-platform/
├── api-gateway/                    # Reactive API gateway (:8060)
├── config-server/                  # Centralized config server (:8888)
├── config-repo/                    # Config files served by config-server
├── discovery-server/               # Eureka service registry (:8761)
│
├── user-service/                   # User management + JWT auth (:8081)
├── product-service/                # Product catalog + inventory (:8082)
├── order-service/                  # Saga orchestrator (:8083)
├── payment-service/                # Multi-gateway payments (:8084)
├── notification-service/           # Kafka event consumer (:8085)
│
├── monitoring/
│   ├── prometheus.yml              # Prometheus scrape config (8 jobs)
│   └── grafana/
│       └── provisioning/           # Auto-configured datasource + dashboards
│
├── k8s/
│   ├── namespace.yml               # ecommerce namespace
│   ├── ingress.yml                 # External traffic routing
│   ├── infrastructure/             # postgres, kafka, redis, zipkin, prometheus, grafana
│   ├── services/                   # All 8 service deployments
│   ├── deploy.sh                   # Deploy/teardown/status script
│   └── test-k8s.sh                # Scale and rolling update tests
│
├── .github/workflows/
│   ├── ci.yml                      # Test, lint, build (on PR/push)
│   └── cd.yml                      # Build & push Docker images (on merge)
│
├── docker-compose.yml              # Full stack (16 containers)
├── docker-compose.infra.yml        # Infrastructure only (DBs + Kafka + Redis + Zipkin)
├── checkstyle.xml                  # Shared code style rules
├── Makefile                        # Build, test, and run commands
└── .env                            # Secrets (gitignored)
```

---

## Makefile Commands

```bash
# ── Development ──
make infra-up          # Start databases + pgAdmin + Kafka + Redis + Zipkin
make infra-down        # Stop infrastructure
make infra-clean       # Stop infrastructure AND delete all data

# ── Run Services ──
make run-discovery     # Start Eureka in a new terminal
make run-config        # Start config server in a new terminal
make run-gateway       # Start API gateway in a new terminal
make run-user          # Start user-service in a new terminal
make run-product       # Start product-service in a new terminal
make run-order         # Start order-service in a new terminal
make run-payment       # Start payment-service in a new terminal
make run-notification  # Start notification-service in a new terminal
make run-all           # Start ALL services (8 terminals)
make stop-all          # Stop all locally-running services

# ── Full Stack ──
make up                # Start everything in Docker (build + run)
make down              # Stop everything
make logs              # Follow all logs
make status            # Container health status
make rebuild s=<svc>   # Rebuild one service
make full-clean        # Stop everything + delete data + images
make e2e-test          # Run end-to-end test against running stack

# ── Build & Test ──
make build-all         # Package all services as JARs
make test-all          # Run all 316 tests (80%+ coverage enforced)
make clean-all         # Remove build artifacts

# ── SSL ──
make generate-cert     # Generate self-signed certificate for user-service
```

---

## Environment Variables

| Variable | Default | Used By |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/users_db` | All services with DBs |
| `DB_USERNAME` | `admin` | All services with DBs |
| `DB_PASSWORD` | `admin123` | All services with DBs |
| `SSL_KEYSTORE_PASSWORD` | `changeit` | user-service |
| `JWT_SECRET` | (32+ char secret) | user-service |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | user, order, payment, notification |
| `EUREKA_HOST` | `localhost` | All services |
| `EUREKA_USERNAME` | `eureka` | All services |
| `EUREKA_PASSWORD` | `eureka123` | All services |
| `CONFIG_SERVER_HOST` | `localhost` | All services |
| `ZIPKIN_URL` | `http://localhost:9411/api/v2/spans` | All services |
| `REDIS_HOST` | `localhost` | user-service, product-service |
| `REDIS_PORT` | `6379` | user-service, product-service |
| `PGADMIN_EMAIL` | `admin@admin.com` | pgAdmin |
| `PGADMIN_PASSWORD` | (from .env) | pgAdmin |

In Docker, these are set automatically via `docker-compose.yml` using container names.
All secrets come from the `.env` file (which is in `.gitignore` — never committed).
Copy `.env.example` to `.env` to get started: `cp .env.example .env`