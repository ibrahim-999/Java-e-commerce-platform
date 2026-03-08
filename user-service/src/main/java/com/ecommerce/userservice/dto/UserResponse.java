package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.model.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

// Java Record — an immutable data carrier introduced in Java 16.
//
// This single line:
//   public record UserResponse(Long id, String firstName, ...) {}
//
// Replaces ALL of this Lombok boilerplate:
//   @Data @Builder @NoArgsConstructor @AllArgsConstructor
//   private Long id;
//   private String firstName;
//   ... (plus generated getters, setters, equals, hashCode, toString)
//
// Key differences from a regular class:
//   1. All fields are automatically "final" — immutable, no setters
//   2. Getters use the field name directly: user.firstName() not user.getFirstName()
//   3. Constructor, equals(), hashCode(), toString() are auto-generated
//   4. Records CAN have static methods and custom constructors
//
// Records are perfect for DTOs — they carry data and nothing else.

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String status,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // Static factory method — converts a User entity to a UserResponse record.
    // Records can have static methods just like regular classes.
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus().name(),
                user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toSet()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
