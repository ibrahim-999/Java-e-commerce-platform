package com.ecommerce.userservice.service;

import com.ecommerce.userservice.model.Role;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService Unit Tests")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test
        @DisplayName("should return correct UserDetails when user is found")
        void shouldReturnUserDetailsWhenUserFound() {
            // Arrange
            Role customerRole = Role.builder().id(1L).name("ROLE_CUSTOMER").build();
            User user = User.builder()
                    .id(1L)
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .password("hashedPassword123")
                    .phoneNumber("+1234567890")
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(customerRole))
                    .build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

            // Act
            UserDetails result = customUserDetailsService.loadUserByUsername("john@example.com");

            // Assert
            assertThat(result.getUsername()).isEqualTo("john@example.com");
            assertThat(result.getPassword()).isEqualTo("hashedPassword123");
            assertThat(result.getAuthorities()).hasSize(1);

            verify(userRepository).findByEmail("john@example.com");
        }

        @Test
        @DisplayName("should throw UsernameNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("nonexistent@example.com"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found: nonexistent@example.com");

            verify(userRepository).findByEmail("nonexistent@example.com");
        }

        @Test
        @DisplayName("should return UserDetails with correct authorities from multiple roles")
        void shouldReturnCorrectAuthoritiesFromRoles() {
            // Arrange
            Role customerRole = Role.builder().id(1L).name("ROLE_CUSTOMER").build();
            Role adminRole = Role.builder().id(2L).name("ROLE_ADMIN").build();

            User user = User.builder()
                    .id(1L)
                    .firstName("Admin")
                    .lastName("User")
                    .email("admin@example.com")
                    .password("hashedPassword")
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(customerRole, adminRole))
                    .build();

            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

            // Act
            UserDetails result = customUserDetailsService.loadUserByUsername("admin@example.com");

            // Assert
            assertThat(result.getAuthorities()).hasSize(2);

            Set<String> authorityNames = result.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(authorityNames).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
        }
    }
}
