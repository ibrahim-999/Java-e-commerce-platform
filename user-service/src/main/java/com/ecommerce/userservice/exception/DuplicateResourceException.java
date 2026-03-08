package com.ecommerce.userservice.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(String.format("%s already exists with %s: %s", resource, field, value));
        // Example: "User already exists with email: john@example.com"
    }
}
