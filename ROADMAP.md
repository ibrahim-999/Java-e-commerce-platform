# 🛒 E-Commerce Platform — Java Spring Boot Learning Roadmap

> One step at a time. Each phase is 100% complete before moving to the next.
> We build a real, promotable, production-grade application while learning everything about Java.

---

## 🖥️ Environment (Already Verified ✅)
- Java 21 (Temurin LTS)
- Maven 3.6.3
- Docker 29.0.2 + Docker Compose v2.40.3
- Git 2.34.1
- Ubuntu 22.04

---

## 🗺️ Phases Overview

| Phase | Topic | Status |
|-------|-------|--------|
| 1 | Project Setup + First REST API | ⬜ |
| 2 | Database Design + JPA + PostgreSQL | ⬜ |
| 3 | Spring Security + JWT Authentication | ⬜ |
| 4 | Advanced Java (Streams, Generics, Design Patterns) | ⬜ |
| 5 | Microservices Split | ⬜ |
| 6 | Messaging with Kafka | ⬜ |
| 7 | API Gateway + Service Discovery | ⬜ |
| 8 | Redis Caching | ⬜ |
| 9 | Testing (JUnit 5, Mockito, Testcontainers) | ⬜ |
| 10 | Full Docker Compose Stack | ⬜ |
| 11 | CI/CD with GitHub Actions | ⬜ |
| 12 | Kubernetes Deployment | ⬜ |

---

## 📁 What We Are Building

A full microservices e-commerce platform with:
- **user-service** — Registration, login, roles, JWT
- **product-service** — Products, categories, inventory
- **order-service** — Cart, orders, order history
- **payment-service** — Payment processing, invoices
- **notification-service** — Emails and SMS via events
- **api-gateway** — Single entry point for all services
- **discovery-server** — Eureka service registry
- **config-server** — Centralized configuration

---

## 🐳 Docker Infrastructure Stack

All infrastructure runs in Docker. We never install PostgreSQL, Kafka, or Redis directly on your machine.

| Service | Image | Port | Introduced In |
|---------|-------|------|---------------|
| PostgreSQL (users DB) | postgres:16 | 5432 | Phase 2 |
| PostgreSQL (products DB) | postgres:16 | 5436 | Phase 5 |
| PostgreSQL (orders DB) | postgres:16 | 5434 | Phase 5 |
| PostgreSQL (payments DB) | postgres:16 | 5435 | Phase 5 |
| pgAdmin (DB UI) | dpage/pgadmin4 | 5050 | Phase 2 |
| Redis | redis:7 | 6379 | Phase 8 |
| Zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 | Phase 6 |
| Kafka | confluentinc/cp-kafka:7.5.0 | 9092 | Phase 6 |
| Kafka UI | provectuslabs/kafka-ui | 8090 | Phase 6 |
| Zipkin (tracing) | openzipkin/zipkin | 9411 | Phase 7 |
| Prometheus (metrics) | prom/prometheus | 9090 | Phase 11 |
| Grafana (dashboards) | grafana/grafana | 3000 | Phase 11 |

### Infrastructure Strategy
- **Phase 2** — start with just PostgreSQL + pgAdmin
- **Phase 5** — add separate databases per service (products, orders, payments — each service owns its own DB)
- **Phase 6** — add Zookeeper + Kafka + Kafka UI
- **Phase 8** — add Redis
- **Phase 10** — everything runs together in one `docker-compose.yml`
- **Phase 11** — add Prometheus + Grafana for monitoring

### Two Compose Files
- `docker-compose.infra.yml` — only infrastructure (DB, Kafka, Redis). Used during development so you can start/stop your Spring Boot apps from the terminal freely
- `docker-compose.yml` — everything including all Spring Boot services. Used to run the full platform as it will run in production

---

## ✅ Phase 1 — Project Setup + First REST API

**What you will learn:**
- How Spring Boot projects are structured and why
- What Maven is and how dependency management works
- Core Spring annotations (@SpringBootApplication, @RestController, @GetMapping, etc.)
- How HTTP requests flow through a Spring Boot app
- How to build and run a Spring Boot app from the terminal
- API documentation with Swagger/OpenAPI (SpringDoc) — auto-generated interactive docs

**Steps:**
1. Create the workspace folder and initialize a Git repository (`main` branch + `phase-1` feature branch)
2. Create the `user-service` folder structure manually (so you understand every folder's purpose)
3. Create and understand the `pom.xml` — the heart of a Maven project
4. Create the main `UserServiceApplication.java` entry point and understand `@SpringBootApplication`
5. Create `application.properties` and understand how Spring Boot configuration works
6. Create your first `HealthController` with two GET endpoints
7. Add the `springdoc-openapi-starter-webmvc-ui` dependency and access Swagger UI at `/swagger-ui.html`
8. Build the project with Maven and fix any errors
9. Run the application and test both endpoints with `curl` and Swagger UI
10. Commit everything to Git, merge `phase-1` branch into `main`

**Definition of Done:**
- App starts on port 8081 with zero errors
- Both endpoints return correct JSON responses
- Swagger UI is accessible and shows all endpoints
- Code is committed to Git

---

## ✅ Phase 2 — Database Design + JPA + PostgreSQL

**What you will learn:**
- How relational databases map to Java objects (ORM concept)
- JPA and Hibernate — what they are and how Spring uses them
- Entities, Repositories, and the @Entity, @Table, @Column annotations
- Database relationships: @OneToMany, @ManyToOne, @ManyToMany
- Spring Data JPA — query methods, custom queries with JPQL
- Database migrations with Flyway (proper, production-grade migrations)
- How to run PostgreSQL locally with Docker
- The N+1 query problem — the #1 JPA performance trap and how to solve it
- Database indexing and pagination for real-world performance

**Steps:**
1. Write a `docker-compose.yml` with just PostgreSQL and start it
2. Connect the user-service to the database via `application.properties`
3. Design the `User` entity with all fields and understand each JPA annotation
4. Create the `UserRepository` interface and understand how Spring generates queries
5. Create the `Role` entity and set up the `@ManyToMany` relationship with User
6. Add Flyway and write your first migration SQL script
7. Create a `UserService` class — understand the @Service layer and why it exists
8. Create a `UserController` with endpoints: create user, get user by ID, get all users
9. Create DTOs (Data Transfer Objects) and understand why we never expose entities directly
10. Add pagination to the "get all users" endpoint using Spring Data's `Pageable`
11. Add database indexes on frequently queried columns (email, username) via `@Index`
12. Understand and fix the N+1 query problem — use `@EntityGraph` or `JOIN FETCH` on relationships
13. Test all endpoints with `curl` and verify data is saved in PostgreSQL
14. Commit everything to Git

**Definition of Done:**
- PostgreSQL running in Docker
- User data persists across application restarts
- All CRUD endpoints work correctly
- Flyway migrations run on startup
- List endpoints support pagination
- No N+1 query issues (verify with Hibernate SQL logging)

---

## ✅ Phase 3 — Spring Security + JWT Authentication

**What you will learn:**
- How Spring Security works internally (filter chain concept)
- Password hashing with BCrypt
- What JWT (JSON Web Token) is and how it works
- Stateless authentication vs session-based authentication
- Role-based access control (RBAC) — ADMIN, CUSTOMER, SELLER roles
- How to protect specific endpoints based on roles
- Refresh tokens and token expiry

**Steps:**
1. Understand the Spring Security filter chain — what happens before your controller runs
2. Add BCrypt password encoding and hash passwords on user registration
3. Add the JWT library dependency to `pom.xml`
4. Create a `JwtService` class that generates and validates tokens
5. Create a `JwtAuthenticationFilter` — intercepts every request and checks the token
6. Configure the `SecurityFilterChain` — define which endpoints are public and which are protected
7. Build the `POST /auth/register` endpoint
8. Build the `POST /auth/login` endpoint that returns a JWT
9. Protect the user endpoints — only ADMIN can get all users
10. Test the full auth flow: register → login → get token → access protected endpoint
11. Add refresh token support
12. Commit everything to Git

**Definition of Done:**
- Cannot access protected endpoints without a valid JWT
- Registration and login work correctly
- Roles are enforced on endpoints
- Tokens expire correctly

---

## ✅ Phase 4 — Advanced Java

**What you will learn:**
- Java Generics — write reusable, type-safe code
- Streams and Lambdas — process collections elegantly
- Optional — handle nulls safely and cleanly
- Functional interfaces — Predicate, Function, Consumer, Supplier
- Design Patterns used in real Spring apps:
  - Builder (clean object creation)
  - Factory (creating objects without exposing logic)
  - Strategy (swappable behavior)
  - Observer (event-driven logic)
- Java Records — immutable data classes
- Exception handling best practices — @ControllerAdvice, @ExceptionHandler

**Steps:**
1. Refactor existing code to use Streams and Lambdas wherever possible
2. Replace all null checks with Optional
3. Create a generic `ApiResponse<T>` wrapper class using Generics — all endpoints return this
4. Implement the Builder pattern for your DTOs using Lombok's @Builder
5. Implement a Strategy pattern for user search (search by email, by name, by role)
6. Implement a global exception handler with @ControllerAdvice
7. Create custom exception classes (UserNotFoundException, EmailAlreadyExistsException, etc.)
8. Convert DTOs to Java Records where appropriate
9. Write unit tests for all service layer methods
10. Commit everything to Git

**Definition of Done:**
- No raw nulls anywhere in the codebase
- All endpoints return a consistent `ApiResponse<T>` format
- All exceptions return proper, consistent error responses
- Service layer is fully unit tested

---

## ✅ Phase 5 — Microservices Split

**What you will learn:**
- What microservices are and when to use them
- How services communicate with each other (synchronous vs asynchronous)
- RestTemplate vs WebClient (reactive HTTP client)
- How to handle failures when a service is down (Circuit Breaker with Resilience4j)
- Inter-service authentication

**Steps:**
1. Create the `product-service` following the same structure as user-service
2. Create the `order-service` following the same structure
3. Create the `payment-service` following the same structure
4. Design the Product entity and all product-related endpoints
5. Design the Order entity — an order belongs to a user and contains products
6. Design the Payment entity — a payment belongs to an order (status: PENDING, COMPLETED, FAILED)
7. Make `order-service` call `user-service` to validate users using WebClient
8. Make `order-service` call `product-service` to validate and reserve products
9. Make `order-service` call `payment-service` to initiate payment after order creation
10. Add Resilience4j Circuit Breaker — handle what happens when a service is down
11. Add a `docker-compose.yml` that runs all services together
12. Test the full flow: register user → create product → place order → process payment
13. Commit everything to Git

**Definition of Done:**
- Four services run independently (user, product, order, payment)
- order-service successfully communicates with the other three
- Circuit breaker triggers correctly when a service is unavailable

---

## ✅ Phase 6 — Messaging with Kafka

**What you will learn:**
- What message queues are and why we need them
- Kafka core concepts: topics, producers, consumers, partitions
- Event-driven architecture
- When to use async messaging vs sync REST calls
- Exactly-once vs at-least-once delivery

**Steps:**
1. Add Kafka to the `docker-compose.yml` infrastructure
2. Create the `notification-service`
3. Define your application events (OrderPlacedEvent, UserRegisteredEvent, PaymentProcessedEvent)
4. Make `user-service` publish a `UserRegisteredEvent` when a new user registers
5. Make `notification-service` consume `UserRegisteredEvent` and log a welcome message
6. Make `order-service` publish an `OrderPlacedEvent` when an order is created
7. Make `notification-service` consume `OrderPlacedEvent` and log an order confirmation
8. Add error handling for failed message processing (Dead Letter Queue)
9. Test the full async flow end to end
10. Commit everything to Git

**Definition of Done:**
- Kafka running in Docker
- Events flow correctly between services
- notification-service reacts to all events without being directly called

---

## ✅ Phase 7 — API Gateway + Service Discovery

**What you will learn:**
- What an API Gateway is and why it is essential
- Spring Cloud Gateway — routing, filters, rate limiting
- Eureka Service Discovery — services find each other dynamically
- Centralized configuration with Spring Cloud Config
- Load balancing
- Structured logging with SLF4J — consistent, searchable logs across all services
- Distributed tracing with Zipkin — follow a request across multiple services

**Steps:**
1. Create the `discovery-server` with Eureka
2. Register all existing services with Eureka
3. Create the `api-gateway` with Spring Cloud Gateway
4. Configure routing rules — all traffic goes through port 8060
5. Add rate limiting on the gateway
6. Add request logging filter on the gateway
7. Create the `config-server` for centralized config management
8. Move all `application.properties` configurations to the config server
9. Add structured logging across all services — use SLF4J with a consistent log format including correlation IDs
10. Add Zipkin for distributed tracing — trace requests as they flow through multiple services
11. Test that all services are discoverable and routable through the gateway
12. Commit everything to Git

**Definition of Done:**
- All services register with Eureka on startup
- All API calls go through the gateway only
- Config server serves configuration to all services
- Logs include correlation IDs so you can trace a request across services
- Zipkin UI shows the full request trace across services

---

## ✅ Phase 8 — Redis Caching

**What you will learn:**
- What caching is and when to use it
- Redis data structures
- Spring Cache abstraction (@Cacheable, @CacheEvict, @CachePut)
- Cache invalidation strategies
- Session management with Redis

**Steps:**
1. Add Redis to the `docker-compose.yml` infrastructure
2. Add Spring Cache + Redis dependency to product-service
3. Cache the `GET /products/{id}` endpoint with @Cacheable
4. Invalidate the cache when a product is updated with @CacheEvict
5. Cache the product list with a TTL (time-to-live) of 5 minutes
6. Add caching to user-service for user lookups
7. Monitor cache hits and misses via Spring Actuator
8. Test cache behavior — verify second request is faster and doesn't hit the database
9. Commit everything to Git

**Definition of Done:**
- Redis running in Docker
- Product and user lookups are cached
- Cache is properly invalidated on updates

---

## ✅ Phase 9 — Testing (Comprehensive Pass)

> **Note:** You should already have basic unit tests from earlier phases (especially Phase 4).
> This phase is about filling gaps, adding integration tests, and achieving full coverage.

**What you will learn:**
- Unit testing with JUnit 5
- Mocking with Mockito
- Integration testing with @SpringBootTest
- Testing databases with Testcontainers (real PostgreSQL in tests)
- Testing REST APIs with MockMvc
- Code coverage with JaCoCo

**Steps:**
1. Write unit tests for all service classes using Mockito (fill any gaps from earlier phases)
2. Write unit tests for all utility classes (JwtService, etc.)
3. Write integration tests for all repository classes using Testcontainers
4. Write integration tests for all controller endpoints using MockMvc
5. Write an end-to-end test for the full auth flow (register → login → access protected endpoint)
6. Write integration tests for inter-service communication (order → user, order → product)
7. Add JaCoCo and achieve minimum 80% code coverage
8. Make sure all tests pass with `mvn test`
9. Commit everything to Git

**Definition of Done:**
- All tests pass
- Minimum 80% code coverage
- Tests run with a real PostgreSQL container (no H2 in-memory shortcuts)

---

## ✅ Phase 10 — Full Docker Compose Stack

**What you will learn:**
- Multi-service Docker Compose orchestration
- Docker networking between containers
- Health checks and startup dependencies
- Environment variable management
- Docker volumes for data persistence

**Steps:**
1. Write a `Dockerfile` for each service
2. Build Docker images for all services
3. Write a single `docker-compose.yml` that starts everything (all services + all infrastructure)
4. Add health checks to all services and infrastructure containers
5. Configure `depends_on` with health checks so services wait for their dependencies
6. Use Docker volumes for PostgreSQL and Redis data persistence
7. Use environment variables for all secrets — no hardcoded passwords
8. Start the entire platform with a single `docker compose up`
9. Test the full user journey end to end through the running stack
10. Commit everything to Git

**Definition of Done:**
- `docker compose up` starts the entire platform from scratch
- All services are healthy and communicating
- Data persists after restarting containers

---

## ✅ Phase 11 — CI/CD with GitHub Actions

**What you will learn:**
- What CI/CD is and why it matters
- GitHub Actions workflows
- Automated testing in CI
- Building and pushing Docker images to Docker Hub
- Environment secrets management in GitHub

**Steps:**
1. Push the entire project to a GitHub repository
2. Create a GitHub Actions workflow that runs all tests on every pull request
3. Add a step that builds all Docker images in CI
4. Add a step that pushes Docker images to Docker Hub on merge to main
5. Add a code quality check with a linter
6. Add a step that checks code coverage and fails if below 80%
7. Create a README with badges (build status, coverage, Docker pulls)
8. Test the pipeline by making a pull request
9. Commit everything

**Definition of Done:**
- Every pull request triggers automated tests
- Merging to main builds and pushes Docker images automatically
- README shows live build status badge

---

## ✅ Phase 12 — Kubernetes Deployment

**What you will learn:**
- Kubernetes core concepts (Pods, Deployments, Services, Ingress)
- ConfigMaps and Secrets
- Persistent Volumes
- Horizontal scaling
- Rolling deployments with zero downtime

**Steps:**
1. Install `minikube` locally for a local Kubernetes cluster
2. Write Kubernetes Deployment manifests for each service
3. Write Kubernetes Service manifests for internal and external access
4. Write ConfigMaps for configuration and Secrets for passwords
5. Write PersistentVolumeClaims for PostgreSQL and Redis
6. Write an Ingress manifest to route external traffic
7. Deploy the full platform to minikube
8. Scale the product-service to 3 replicas and verify load balancing
9. Simulate a rolling deployment (update an image, watch the rollout)
10. Commit all Kubernetes manifests to Git

**Definition of Done:**
- Full platform running on local Kubernetes
- Services scale horizontally
- Zero-downtime rolling deployment works

---

## 🏁 End Result — What You Will Have

After completing all 12 phases you will have:

- ✅ A real, working, production-grade microservices application
- ✅ Deep knowledge of Java 21 and Spring Boot ecosystem
- ✅ A strong GitHub project to show employers or clients
- ✅ Experience with every major tool in the Java backend world
- ✅ A project you can deploy live and put on your resume

---

## 📌 Rules We Follow

1. **One phase at a time** — never move forward until the current phase is 100% done
2. **Understand before you copy** — every line of code is explained
3. **Git commits after every step** — clean history tells the story of the project
4. **Git branches per phase** — create a `phase-X` branch, work on it, merge to `main` when complete
5. **Test as you build** — write unit tests for each service class as you create it, not just in Phase 9
6. **Test everything** — if it is not tested, it is not done
7. **Ask questions** — no question is too basic
