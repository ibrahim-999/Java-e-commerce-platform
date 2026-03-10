package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import reactor.core.publisher.Hooks;

// @EnableDiscoveryClient — tells this app to register with Eureka on startup
// and use Eureka to discover other services.
//
// How the gateway works:
//   1. Client sends: POST http://localhost:8080/api/orders
//   2. Gateway matches the path "/api/orders/**" to the route "order-service"
//   3. Gateway asks Eureka: "Where is order-service?"
//   4. Eureka replies: "It's at 192.168.1.10:8083"
//   5. Gateway forwards the request to http://192.168.1.10:8083/api/orders
//   6. Gateway returns the response to the client
//
// The client NEVER knows about individual service addresses — only the gateway.

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        // Enable automatic context propagation for Reactor.
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
