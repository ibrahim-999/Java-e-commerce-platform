package com.ecommerce.userservice.service;

import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.factory.UserFactory;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.RoleRepository;
import com.ecommerce.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Additional Unit Tests")
class UserServiceAdditionalTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            User user1 = UserFactory.createUserWithId(1L);
            User user2 = UserFactory.createUserWithId(2L);
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> expectedPage = new PageImpl<>(List.of(user1, user2), pageable, 2);

            when(userRepository.findAll(pageable)).thenReturn(expectedPage);

            Page<User> result = userService.getAllUsers(pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getNumber()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(10);
            verify(userRepository).findAll(pageable);
        }

        @Test
        @DisplayName("should return empty page when no users exist")
        void shouldReturnEmptyPageWhenNoUsers() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(userRepository.findAll(pageable)).thenReturn(emptyPage);

            Page<User> result = userService.getAllUsers(pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
            verify(userRepository).findAll(pageable);
        }

        @Test
        @DisplayName("should respect pagination parameters")
        void shouldRespectPaginationParameters() {
            User user3 = UserFactory.createUserWithId(3L);
            Pageable pageable = PageRequest.of(1, 2); // second page, 2 per page
            Page<User> page = new PageImpl<>(List.of(user3), pageable, 3);

            when(userRepository.findAll(pageable)).thenReturn(page);

            Page<User> result = userService.getAllUsers(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getNumber()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
            verify(userRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("getUsersByStatus")
    class GetUsersByStatus {

        @Test
        @DisplayName("should return users with ACTIVE status")
        void shouldReturnActiveUsers() {
            User user1 = UserFactory.createUserWithId(1L);
            user1.setStatus(UserStatus.ACTIVE);
            User user2 = UserFactory.createUserWithId(2L);
            user2.setStatus(UserStatus.ACTIVE);

            when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user1, user2));

            List<User> result = userService.getUsersByStatus(UserStatus.ACTIVE);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(u -> u.getStatus() == UserStatus.ACTIVE);
            verify(userRepository).findByStatus(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should return users with INACTIVE status")
        void shouldReturnInactiveUsers() {
            User user1 = UserFactory.createUserWithId(1L);
            user1.setStatus(UserStatus.INACTIVE);

            when(userRepository.findByStatus(UserStatus.INACTIVE)).thenReturn(List.of(user1));

            List<User> result = userService.getUsersByStatus(UserStatus.INACTIVE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(userRepository).findByStatus(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("should return users with SUSPENDED status")
        void shouldReturnSuspendedUsers() {
            when(userRepository.findByStatus(UserStatus.SUSPENDED)).thenReturn(List.of());

            List<User> result = userService.getUsersByStatus(UserStatus.SUSPENDED);

            assertThat(result).isEmpty();
            verify(userRepository).findByStatus(UserStatus.SUSPENDED);
        }
    }

    @Nested
    @DisplayName("getUserEntityById")
    class GetUserEntityById {

        @Test
        @DisplayName("should return user entity when found")
        void shouldReturnUserEntityWhenFound() {
            User user = UserFactory.createUserWithId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = userService.getUserEntityById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getFirstName()).isEqualTo(user.getFirstName());
            assertThat(result.getEmail()).isEqualTo(user.getEmail());
            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserEntityById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with id: 99");

            verify(userRepository).findById(99L);
        }
    }
}
