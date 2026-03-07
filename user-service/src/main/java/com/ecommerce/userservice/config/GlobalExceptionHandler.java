package com.ecommerce.userservice.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

// @RestControllerAdvice — catches exceptions thrown by ANY controller
// and converts them into proper HTTP responses with the right status code.
//
// Without this, unhandled exceptions return ugly 500 errors with stack traces.
// With this, every error returns a clean, consistent JSON response.
// We'll expand this significantly in Phase 4.

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Catches IllegalArgumentException (e.g., "Email already exists")
    // Returns 409 Conflict — the request conflicts with existing data
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "error", "Conflict",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // Catches RuntimeException (e.g., "User not found with id: 99")
    // Returns 404 Not Found
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
