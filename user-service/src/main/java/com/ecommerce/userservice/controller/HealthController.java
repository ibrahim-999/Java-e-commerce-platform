package com.ecommerce.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

// @RestController = @Controller + @ResponseBody combined
//
// @Controller   — tells Spring "this class handles HTTP requests"
// @ResponseBody — tells Spring "convert the return value to JSON automatically"
//
// So @RestController means: "this class handles HTTP requests and always returns JSON"
// Spring finds this class automatically because of @ComponentScan (remember Step 4).

@RestController

// @RequestMapping("/api") — every endpoint in this controller starts with /api
// So if you have @GetMapping("/status"), the full URL is: GET /api/status
@RequestMapping("/api")
public class HealthController {

    // @GetMapping("/status") — this method runs when someone sends:
    //     GET http://localhost:8081/api/status
    //
    // ResponseEntity<Map<String, Object>> — lets you control:
    //   - The response body (the Map becomes JSON)
    //   - The HTTP status code (200 OK, 404 Not Found, etc.)
    //
    // Map.of() creates an immutable map. Jackson (included in starter-web)
    // automatically converts it to JSON like: {"service": "user-service", ...}

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "user-service",
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // Second endpoint — a simple welcome message
    //     GET http://localhost:8081/api/welcome
    //
    // This one returns a simpler response to show that any Java object
    // can be returned — Spring converts it to JSON automatically.

    @GetMapping("/welcome")
    public ResponseEntity<Map<String, String>> getWelcome() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the User Service API",
                "version", "0.0.1"
        ));
    }
}
