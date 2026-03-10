package com.ecommerce.userservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

// Shared base class for integration tests.
// Uses Testcontainers to spin up real PostgreSQL and Redis Docker containers.
//
// Singleton pattern — the containers start ONCE and are reused by ALL integration tests.
// This is much faster than starting a new container per test class.
// The JVM shuts them down automatically via Ryuk (Testcontainers' cleanup daemon).

public abstract class BaseIntegrationTest {

    // Real PostgreSQL — same engine as production. No H2 shortcuts.
    static final PostgreSQLContainer<?> postgres;

    // Real Redis — same engine as production.
    static final GenericContainer<?> redis;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();

        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        redis.start();
    }

    // @DynamicPropertySource — injects container-specific properties into Spring's Environment
    // AFTER the containers start. This replaces the hardcoded values in application-test.properties.
    //
    // Why dynamic? Because Testcontainers assigns RANDOM ports to avoid conflicts.
    // We can't know the port at compile time, so we inject it at runtime.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL — Testcontainers provides the JDBC URL, username, and password
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis — inject the dynamic host and mapped port
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
