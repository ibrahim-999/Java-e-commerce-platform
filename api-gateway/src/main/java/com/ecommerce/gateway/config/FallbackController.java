package com.ecommerce.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

// Fallback controller — returns a friendly error when a downstream service is unavailable.
//
// When the circuit breaker trips (service is down or too slow), the gateway redirects
// to this endpoint instead of returning a raw error. This gives the client a consistent
// JSON error format instead of an HTML error page or connection timeout.

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping
    @PostMapping
    @PutMapping
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> fallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "message", "Service is temporarily unavailable. Please try again later.",
                        "timestamp", Instant.now().toString()
                ));
    }
}
