# E-Commerce Platform

A production-grade microservices e-commerce platform built with Java 21 and Spring Boot 3.4.

Eight independent services communicate over REST and Kafka, orchestrated through an API Gateway with Eureka service discovery, centralized configuration, and distributed tracing via Zipkin.

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
 │ Registration  │ │ Products      │ │ Order creation   │ │ Stripe/PayPal│
 │ Login (JWT)   │ │ Categories    │ │ User validation  │ │ Bank gateway │
 │ User mgmt     │ │ Inventory     │ │ Stock reservation│ │ Retry+backoff│
 │ Role-based    │ │ Stock ops     │ │ Payment trigger  │ │ Transaction  │
 │ access ctrl   │ │ Price stats   │ │ Circuit Breaker  │ │ audit trail  │
 └───────┬───────┘ └───────┬───────┘ └────────┬─────────┘ └──────┬───────┘
         │                 │                   │                  │
         ▼                 ▼                   ▼                  ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │                       Apache Kafka :9092                             │
 │       Topics: user-events | order-events | payment-events           │
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

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (Temurin LTS) |
| Framework | Spring Boot 3.4.3 |
| Security | Spring Security + JWT (JJWT 0.12.6) |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Messaging | Apache Kafka (Spring Kafka) |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Netflix Eureka |
| Config Management | Spring Cloud Config |
| Distributed Tracing | Micrometer Tracing + Zipkin |
| HTTP Client | WebClient (Spring WebFlux) |
| Resilience | Resilience4j (Circuit Breaker + Retry) |
| Testing | JUnit 5, Mockito, MockMvc, AssertJ |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |
| Containers | Docker, Docker Compose |

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.6+
- Docker & Docker Compose

### Option 1: Development mode (recommended for learning)

Run databases in Docker, services locally:

```bash
# Start infrastructure (4 PostgreSQL DBs + pgAdmin + Kafka + Zookeeper + Zipkin)
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

Everything runs in containers:

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

### Verify it works

```bash
# All requests go through the API Gateway on port 8060
curl -s -X POST http://localhost:8060/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com",
       "password":"Pass12345","phoneNumber":"+201000000001"}'

curl -s http://localhost:8060/api/products        # product-service
curl -s http://localhost:8060/api/categories       # product-service

# Web UIs
open http://localhost:8761                        # Eureka Dashboard (eureka/eureka123)
open http://localhost:9411                        # Zipkin Tracing UI
open http://localhost:8090                        # Kafka UI
open http://localhost:5050                        # pgAdmin (admin@admin.com / admin123)
```

---

## API Endpoints

All requests go through the **API Gateway** on port **8060**.

### User Service (via gateway: /api/users/**, /api/auth/**)

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

### Product Service (via gateway: /api/products/**, /api/categories/**)

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

### Order Service (via gateway: /api/orders/**)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Create order (triggers payment) |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/api/orders/user/{userId}` | User's orders (paginated) |
| GET | `/api/orders/{id}/history` | Order status audit trail |
| PUT | `/api/orders/{id}/cancel` | Cancel order (restores stock) |

### Payment Service (via gateway: /api/payments/**)

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
# All traffic goes through the API Gateway (port 8060)

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

# 3. Place an order (validates user, reserves stock, charges payment)
curl -s -X POST http://localhost:8060/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":1}],
       "paymentMethod":"CREDIT_CARD"}'

# 4. Check the full trace in Zipkin
open http://localhost:9411
```

---

## Testing

```bash
# Run all tests (98 total across all services)
make test-all

# Run tests for a specific service
cd user-service && mvn test          # 27 tests
cd product-service && mvn test       # 25 tests
cd order-service && mvn test         #  7 tests
cd payment-service && mvn test       # 28 tests
cd notification-service && mvn test  # 11 tests
```

Tests require infrastructure running (`make infra-up`).

---

## Project Structure

```
ecommerce-platform/
├── discovery-server/               # Eureka service registry (:8761)
├── config-server/                  # Centralized configuration (:8888)
├── config-repo/                    # Config files served by config-server
├── api-gateway/                    # Single entry point, routing, rate limiting (:8060)
│
├── user-service/                   # User management + JWT auth (:8081)
├── product-service/                # Product catalog + inventory (:8082)
├── order-service/                  # Order orchestration (:8083)
├── payment-service/                # Payment processing (:8084)
├── notification-service/           # Kafka event consumer (:8085)
│
├── docker-compose.yml              # Full stack (all services + infrastructure)
├── docker-compose.infra.yml        # Infrastructure only (DBs + Kafka + Zipkin)
└── Makefile                        # Build, test, and run commands
```

---

## Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | Payment gateways (`PaymentGateway` interface) | Swap between Stripe, PayPal, Bank without changing business logic |
| **Factory** | `PaymentGatewayFactory` | Maps payment method to the correct gateway implementation |
| **Builder** | DTOs and entities (Lombok `@Builder`) | Clean object construction with many optional fields |
| **Repository** | Spring Data JPA repositories | Abstracts database access behind interfaces |
| **Circuit Breaker** | order-service inter-service calls | Prevents cascading failures when a service is down |
| **Compensating Transaction** | Order creation rollback | Restores stock if payment or product validation fails mid-order |
| **Event-Driven** | Kafka producers + notification-service | Async notifications without coupling services |
| **Dead Letter Queue** | Kafka DLT topics | Failed messages saved for manual investigation |
| **Token Bucket** | API Gateway rate limiter | In-memory rate limiting per client IP |

---

## Makefile Commands

```bash
# ── Development ──
make infra-up         # Start databases + pgAdmin + Kafka + Zipkin
make infra-down       # Stop infrastructure
make infra-clean      # Stop infrastructure AND delete all data

# ── Run Services ──
make run-discovery    # Start Eureka in a new terminal
make run-config       # Start config server in a new terminal
make run-gateway      # Start API gateway in a new terminal
make run-user         # Start user-service in a new terminal
make run-product      # Start product-service in a new terminal
make run-order        # Start order-service in a new terminal
make run-payment      # Start payment-service in a new terminal
make run-notification # Start notification-service in a new terminal
make run-all          # Start ALL services (8 terminals)
make stop-all         # Stop all locally-running services

# ── Full Stack ──
make up               # Start everything in Docker (build + run)
make down             # Stop everything
make logs             # Follow all logs
make status           # Container health status
make rebuild s=<svc>  # Rebuild one service

# ── Build & Test ──
make build-all        # Package all services as JARs
make test-all         # Run all 98 tests
make clean-all        # Remove build artifacts

# ── SSL ──
make generate-cert    # Generate self-signed certificate for user-service
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
| `GATEWAY_PORT` | `8060` | api-gateway |

In Docker, these are set automatically via `docker-compose.yml` using container names.
