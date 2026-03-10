package com.ecommerce.productservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// @EnableCaching activates Spring's caching infrastructure.
// Without this, @Cacheable, @CacheEvict, and @CachePut annotations are IGNORED.
//
// How Spring Cache works under the hood:
//   1. @Cacheable method is called with argument id=5
//   2. Spring creates a cache key: "products::5"
//   3. Spring checks Redis: GET products::5
//   4. If found (cache HIT) → return cached value, SKIP the method entirely
//   5. If not found (cache MISS) → execute the method, store result in Redis
//
// This uses AOP (Aspect-Oriented Programming) — Spring wraps your service
// in a proxy that intercepts method calls and checks the cache first.

@Configuration
@EnableCaching
public class CacheConfig {

    // Custom CacheManager that controls HOW data is stored in Redis.
    //
    // Why customize? The defaults use Java serialization (binary blobs in Redis).
    // We use JSON instead because:
    //   1. Human-readable — you can inspect cache contents with redis-cli
    //   2. Cross-language — other services could read the cache if needed
    //   3. Debuggable — see exact JSON in Redis when troubleshooting
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // ObjectMapper configured for Redis JSON serialization.
        // By default, Jackson does NOT know how to serialize Java 8 date/time types
        // (LocalDateTime, Instant, etc.). JavaTimeModule adds that support.
        // Without it, caching a DTO with LocalDateTime fields would throw:
        //   "Java 8 date/time type not supported by default"
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // Write dates as "2024-01-15T10:30:00" (ISO string), not [2024, 1, 15, 10, 30, 0]
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Store the Java class name as "@class" property in the JSON object.
        // Without this, Redis would deserialize everything as LinkedHashMap.
        //
        // EVERYTHING — include type info for all types (including final classes like records).
        // As.PROPERTY — embed type info as a "@class" JSON property inside the object.
        //   Example: {"@class": "com.ecommerce.productservice.dto.ProductResponse", "id": 5, ...}
        //
        // We use EVERYTHING (not NON_FINAL) because ProductResponse is a Java record,
        // and records are implicitly final — NON_FINAL would skip them.
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);

        // Default config for all caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // TTL (Time-To-Live): cached entries expire after 10 minutes.
                // After 10 minutes, the next request hits the DB and refreshes the cache.
                // This prevents stale data from living forever in Redis.
                .entryTtl(Duration.ofMinutes(10))
                // Keys are stored as readable strings: "products::5"
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // Values are stored as JSON (not binary Java serialization).
                // We pass our custom ObjectMapper with JavaTimeModule so LocalDateTime works.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                // Don't cache null values — a cache miss for a non-existent product
                // should always go to the DB (the product might be created later)
                .disableCachingNullValues();

        // Per-cache configuration — different caches can have different TTLs.
        // Product lists change more often (new products, stock updates),
        // so we give them a shorter TTL than individual product lookups.
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("product-list", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
