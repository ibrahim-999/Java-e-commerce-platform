# E-Commerce Platform

A production-grade microservices e-commerce platform built with Java 21 and Spring Boot 3.4.

Four independent services communicate over REST, each with its own PostgreSQL database, Flyway migrations, and comprehensive test suite.

---

## Architecture

```
                         ┌──────────────┐
                         │   Client     │
                         └──────┬───────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          │                     │                      │
          ▼                     ▼                      ▼
  ┌───────────────┐   ┌────────────────┐   ┌──────────────────┐
  │ user-service  │   │product-service │   │ payment-service  │
  │   :8081 (HTTPS)│   │    :8082      │   │     :8084        │
  │               │   │               │   │                  │
  │ Registration  │   │ Products      │   │ Stripe gateway   │
  │ Login (JWT)   │   │ Categories    │   │ PayPal gateway   │
  │ User mgmt    │   │ Inventory     │   │ Bank gateway     │
  │ Role-based    │   │ Stock ops     │   │ Retry + backoff  │
  │ access ctrl   │   │ Price stats   │   │ Transaction log  │
  └───────┬───────┘   └───────┬───────┘   └────────┬─────────┘
          │                   │                     │
          │           ┌───────┴─────────────────────┘
          │           │
          ▼           ▼
  ┌───────────────────────────┐
  │      order-service        │
  │         :8083             │
  │                           │
  │ Validates user  ──────► user-service
  │ Reserves stock  ──────► product-service
  │ Processes payment ────► payment-service
  │                           │
  │ Circuit Breaker (Resilience4j)
  │ Compensating transactions │
  │ Order status history      │
  └───────────────────────────┘
```

Each service owns its own database (database-per-service pattern):

| Service | Database | Port |
|---------|----------|------|
| user-service | `users_db` | 5432 |
| product-service | `products_db` | 5436 |
| order-service | `orders_db` | 5434 |
| payment-service | `payments_db` | 5435 |

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
# Start infrastructure (4 PostgreSQL databases + pgAdmin)
make infra-up

# Run each service in a separate terminal
cd user-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
```

### Option 2: Full Docker stack

Everything runs in containers:

```bash
# Build and start all 9 containers
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
# Health checks
curl -sk https://localhost:8081/api/status        # user-service
curl -s  http://localhost:8082/api/status          # product-service
curl -s  http://localhost:8083/api/orders          # order-service
curl -s  http://localhost:8084/api/health          # payment-service

# pgAdmin UI
open http://localhost:5050                         # admin@admin.com / admin123
```

---

## API Endpoints

### User Service (port 8081 — HTTPS)

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

### Product Service (port 8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products` | Create product |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products` | List products (paginated) |
| GET | `/api/products/search?name=` | Search by name |
| GET | `/api/products/category/{id}` | Products by category |
| GET | `/api/products/stats` | Price statistics (AVG, MIN, MAX) |
| GET | `/api/products/stats/category/{id}` | Category price stats |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product |
| PUT | `/api/products/{id}/stock/reduce` | Reduce stock (internal) |
| PUT | `/api/products/{id}/stock/restore` | Restore stock (internal) |
| GET | `/api/categories` | List all categories |

### Order Service (port 8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Create order (triggers payment) |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/api/orders/user/{userId}` | User's orders (paginated) |
| GET | `/api/orders/{id}/history` | Order status audit trail |
| PUT | `/api/orders/{id}/cancel` | Cancel order (restores stock) |

### Payment Service (port 8084)

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
curl -sk -X POST https://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com",
       "password":"Pass12345","phoneNumber":"+201000000001"}'

# 2. Create a product
curl -s -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"MacBook Pro","description":"Laptop","price":2499.99,
       "stockQuantity":10,"sku":"MBP-001","categoryId":1}'

# 3. Place an order (validates user, reserves stock, charges payment)
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":1}],
       "paymentMethod":"CREDIT_CARD"}'

# 4. Check order history
curl -s http://localhost:8083/api/orders/1/history

# 5. Check payment transaction log
curl -s http://localhost:8084/api/payments/1/transactions

# 6. Refund the payment
curl -s -X PUT http://localhost:8084/api/payments/1/refund

# 7. Cancel the order (stock is restored)
curl -s -X PUT http://localhost:8083/api/orders/1/cancel
```

---

## Testing

```bash
# Run all tests (87 total across all services)
make test-all

# Run tests for a specific service
cd user-service && mvn test       # 27 tests
cd product-service && mvn test    # 25 tests
cd order-service && mvn test      #  7 tests
cd payment-service && mvn test    # 28 tests
```

Tests require infrastructure running (`make infra-up`).

---

## Project Structure

```
ecommerce-platform/
├── user-service/                    # User management + JWT auth
│   ├── src/main/java/.../
│   │   ├── config/                  # Security, JWT filter, WebClient
│   │   ├── controller/              # REST endpoints
│   │   ├── dto/                     # Request/Response objects
│   │   ├── exception/               # Custom exceptions + global handler
│   │   ├── model/                   # JPA entities (User, Role)
│   │   ├── repository/              # Data access layer
│   │   ├── service/                 # Business logic
│   │   └── search/                  # Strategy pattern for user search
│   ├── src/main/resources/
│   │   ├── db/migration/            # Flyway SQL migrations
│   │   └── keystore.p12             # Self-signed SSL certificate
│   └── Dockerfile
│
├── product-service/                 # Product catalog + inventory
│   ├── src/main/java/.../
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── exception/
│   │   ├── model/                   # Product, Category entities
│   │   ├── repository/
│   │   └── service/                 # Stock ops with optimistic locking
│   ├── PRODUCTION_CONCERNS.md       # Race conditions, caching, aggregations
│   └── Dockerfile
│
├── order-service/                   # Order orchestration
│   ├── src/main/java/.../
│   │   ├── config/                  # WebClient beans, circuit breaker
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── exception/
│   │   ├── model/                   # Order, OrderItem, OrderStatusHistory
│   │   ├── repository/
│   │   └── service/                 # Inter-service calls, compensating txns
│   ├── ORDER_ARCHITECTURE.md        # Status history, distributed rollback, payment integration
│   └── Dockerfile
│
├── payment-service/                 # Payment processing
│   ├── src/main/java/.../
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── exception/
│   │   ├── gateway/                 # Strategy pattern: Stripe, PayPal, Bank
│   │   ├── model/                   # Payment, PaymentTransaction
│   │   ├── repository/
│   │   └── service/                 # Retry with exponential backoff
│   ├── PAYMENT_ARCHITECTURE.md      # Retry logic, gateway patterns, audit trails
│   └── Dockerfile
│
├── docker-compose.yml               # Full stack (services + infrastructure)
├── docker-compose.infra.yml         # Infrastructure only (databases + pgAdmin)
├── Makefile                         # Build, test, and deployment commands
├── .env.example                     # Environment variable template
└── .gitignore
```

---

## Architecture Documentation

Each service has a dedicated architecture document explaining production concerns, the problems they solve, and how our solutions compare to production-scale approaches:

| Document | Location | Topics |
|----------|----------|--------|
| [Production Concerns](product-service/PRODUCTION_CONCERNS.md) | `product-service/` | Race conditions (optimistic locking, atomic SQL), price caching, aggregate queries, scaling to millions of products |
| [Order Architecture](order-service/ORDER_ARCHITECTURE.md) | `order-service/` | Order status audit trail, compensating transactions (distributed rollback), payment integration, price snapshotting |
| [Payment Architecture](payment-service/PAYMENT_ARCHITECTURE.md) | `payment-service/` | Retry with exponential backoff, Strategy + Factory pattern for gateways, transaction audit trail, duplicate payment prevention |

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

---

## Makefile Commands

```bash
# ── Development ──
make infra-up         # Start databases + pgAdmin
make infra-down       # Stop databases
make infra-clean      # Stop databases AND delete all data

# ── Full Stack ──
make up               # Start everything (build + run)
make down             # Stop everything
make logs             # Follow all logs
make status           # Container health status
make rebuild s=<svc>  # Rebuild one service (e.g., make rebuild s=user-service)

# ── Build & Test ──
make build-all        # Package all services as JARs
make test-all         # Run all 87 tests
make clean-all        # Remove build artifacts

# ── SSL ──
make generate-cert    # Generate self-signed certificate for user-service
```

---

## Environment Variables

Copy `.env.example` to `.env` and fill in values for local development:

| Variable | Default | Used By |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/users_db` | All services |
| `DB_USERNAME` | `admin` | All services |
| `DB_PASSWORD` | `admin123` | All services |
| `SSL_KEYSTORE_PASSWORD` | `changeit` | user-service |
| `JWT_SECRET` | (32+ char secret) | user-service |
| `USER_SERVICE_URL` | `https://localhost:8081` | order-service |
| `PRODUCT_SERVICE_URL` | `http://localhost:8082` | order-service |
| `PAYMENT_SERVICE_URL` | `http://localhost:8084` | order-service |

In Docker, these are set automatically via `docker-compose.yml` using container names.
