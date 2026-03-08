package com.ecommerce.userservice.dto;

// Record — immutable data carrier for authentication tokens.
// Jackson (Spring's JSON library) fully supports records since Spring Boot 3.x.
// It serializes record components as JSON fields:
//   {"accessToken": "...", "refreshToken": "...", "email": "...", "type": "Bearer"}

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String email,
        String type
) {}
