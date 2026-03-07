package com.ecommerce.userservice.service;

import com.ecommerce.userservice.factory.UserFactory;
import com.ecommerce.userservice.model.Role;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.RoleRepository;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class) — enables Mockito annotations (@Mock, @InjectMocks)
// This is a UNIT test — no Spring context, no database, no server.
// It tests UserService in complete isolation by faking its dependencies.
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    // @Mock — creates a fake (mock) version of UserRepository.
    // It doesn't talk to the database — you tell it what to return.
    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    // @InjectMocks — creates a real UserService and injects the mocks above into it.
    // So userService.userRepository is actually the mock, not the real repository.
    @InjectMocks
    private UserService userService;

    // @Nested — groups related tests together. Makes test output readable.
    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user successfully with ROLE_CUSTOMER")
        void shouldCreateUserSuccessfully() {
            // ARRANGE — set up the test data and mock behavior
            User user = UserFactory.createUser();
            Role customerRole = UserFactory.customerRole();

            // Tell the mocks what to return when called
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                savedUser.setId(1L);  // simulate database assigning an ID
                return savedUser;
            });

            // ACT — call the method being tested
            User result = userService.createUser(user);

            // ASSERT — verify the result is correct
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(result.getRoles()).contains(customerRole);

            // Verify the mocks were called as expected
            verify(userRepository).existsByEmail(user.getEmail());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should throw exception when email already exists")
        void shouldThrowWhenEmailExists() {
            User user = UserFactory.createUser();

            when(userRepository.existsByEmail(user.getEmail())).thenReturn(true);

            // assertThatThrownBy — verifies an exception is thrown
            assertThatThrownBy(() -> userService.createUser(user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already exists");

            // save() should NEVER be called if email exists
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            User user = UserFactory.createUserWithId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = userService.getUserById(1L);

            assertThat(result).isEqualTo(user);
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void shouldThrowWhenNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("should return user when email found")
        void shouldReturnUserWhenEmailFound() {
            User user = UserFactory.createUserWithId(1L);

            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            User result = userService.getUserByEmail(user.getEmail());

            assertThat(result.getEmail()).isEqualTo(user.getEmail());
        }

        @Test
        @DisplayName("should throw exception when email not found")
        void shouldThrowWhenEmailNotFound() {
            when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserByEmail("nonexistent@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("should update only provided fields")
        void shouldUpdateOnlyProvidedFields() {
            User existingUser = UserFactory.createUserWithId(1L);
            String originalLastName = existingUser.getLastName();
            String originalPhone = existingUser.getPhoneNumber();

            // Only updating firstName — other fields should stay the same
            User updateData = User.builder().firstName("NewName").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            User result = userService.updateUser(1L, updateData);

            assertThat(result.getFirstName()).isEqualTo("NewName");
            assertThat(result.getLastName()).isEqualTo(originalLastName);   // unchanged
            assertThat(result.getPhoneNumber()).isEqualTo(originalPhone);   // unchanged
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should delete user when found")
        void shouldDeleteUserWhenFound() {
            User user = UserFactory.createUserWithId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.deleteUser(1L);

            verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("should throw exception when deleting non-existent user")
        void shouldThrowWhenDeletingNonExistent() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");

            verify(userRepository, never()).delete(any());
        }
    }
}
