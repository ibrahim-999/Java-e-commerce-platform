package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

// This DTO defines what the client RECEIVES.
// Notice: NO password field. The client never sees the password.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String status;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Static factory method — converts a User entity to a UserResponse DTO.
    // This is the ONLY place where entity → DTO conversion happens.
    // Having it here keeps the conversion logic in one place.
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus().name())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName())   // Role object → role name string
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
