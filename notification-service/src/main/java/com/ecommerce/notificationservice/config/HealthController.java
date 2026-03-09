package com.ecommerce.notificationservice.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "notification-service",
                "status", "UP",
                "timestamp", LocalDateTime.now()
        );
    }
}
