package com.ecommerce.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

// @EnableConfigServer turns this Spring Boot app into a Config Server.
//
// What happens on startup:
// 1. Spring Boot starts normally
// 2. @EnableConfigServer activates the config server endpoints
// 3. The server reads config files from the configured location (native filesystem or Git)
// 4. It exposes REST endpoints that other services call to get their configuration:
//
//    GET /{application}/{profile}
//    GET /{application}-{profile}.properties
//
// Examples:
//    GET /user-service/default     → returns user-service.properties
//    GET /user-service/dev         → returns user-service.properties + user-service-dev.properties
//    GET /application/default      → returns application.properties (shared by all services)

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
