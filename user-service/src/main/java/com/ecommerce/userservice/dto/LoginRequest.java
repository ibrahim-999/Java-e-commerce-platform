package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Record with validation — annotations go directly on the record components.
// Spring's @Valid works exactly the same as with regular classes.
//
// Jackson deserializes the JSON request body into this record automatically:
//   {"email": "john@example.com", "password": "secret123"}
//   → new LoginRequest("john@example.com", "secret123")

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
