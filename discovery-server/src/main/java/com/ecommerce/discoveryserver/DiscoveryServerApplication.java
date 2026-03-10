package com.ecommerce.discoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

// @EnableEurekaServer — this single annotation transforms a regular Spring Boot app
// into a Eureka Service Registry.
//
// What happens when this starts:
//   1. Eureka opens a REST API that other services call to REGISTER themselves
//   2. Eureka opens a web dashboard (http://localhost:8761) showing all registered services
//   3. Eureka sends heartbeats to registered services every 30s — if a service stops
//      responding, Eureka removes it from the registry after 90s
//
// How services use it:
//   - On startup: "Hi Eureka, I'm user-service at 192.168.1.5:8081"
//   - When calling another service: "Eureka, where is product-service?" → "It's at 192.168.1.6:8082"
//   - This is called SERVICE DISCOVERY — services find each other dynamically

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
