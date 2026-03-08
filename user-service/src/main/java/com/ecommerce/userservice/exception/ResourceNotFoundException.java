package com.ecommerce.userservice.exception;

// Custom exception for when a resource is not found (user, role, etc.).
// Extending RuntimeException makes it an "unchecked" exception —
// you don't have to declare it in method signatures with "throws".

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
        // Example: "User not found with id: 5"
        // Example: "Role not found with name: ROLE_ADMIN"
    }
}
