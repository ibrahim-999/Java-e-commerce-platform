package com.ecommerce.userservice.service;

import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.strategy.EmailSearchStrategy;
import com.ecommerce.userservice.strategy.NameSearchStrategy;
import com.ecommerce.userservice.strategy.StatusSearchStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// Tests for UserSearchService — the "context" in the Strategy pattern.
// We mock the strategies themselves and verify that the correct one is called.

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSearchService Unit Tests")
class UserSearchServiceTest {

    @Mock
    private EmailSearchStrategy emailStrategy;

    @Mock
    private NameSearchStrategy nameStrategy;

    @Mock
    private StatusSearchStrategy statusStrategy;

    @Test
    @DisplayName("should delegate to email strategy when type is 'email'")
    void shouldDelegateToEmailStrategy() {
        // Build the service with our mocks
        UserSearchService searchService = new UserSearchService(emailStrategy, nameStrategy, statusStrategy);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> expectedPage = new PageImpl<>(List.of());

        when(emailStrategy.search("john", pageable)).thenReturn(expectedPage);

        Page<User> result = searchService.search("email", "john", pageable);

        assertThat(result).isEqualTo(expectedPage);
        verify(emailStrategy).search("john", pageable);
        verify(nameStrategy, never()).search(anyString(), any());
        verify(statusStrategy, never()).search(anyString(), any());
    }

    @Test
    @DisplayName("should delegate to name strategy when type is 'name'")
    void shouldDelegateToNameStrategy() {
        UserSearchService searchService = new UserSearchService(emailStrategy, nameStrategy, statusStrategy);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> expectedPage = new PageImpl<>(List.of());

        when(nameStrategy.search("doe", pageable)).thenReturn(expectedPage);

        Page<User> result = searchService.search("name", "doe", pageable);

        assertThat(result).isEqualTo(expectedPage);
        verify(nameStrategy).search("doe", pageable);
        verify(emailStrategy, never()).search(anyString(), any());
    }

    @Test
    @DisplayName("should delegate to status strategy when type is 'status'")
    void shouldDelegateToStatusStrategy() {
        UserSearchService searchService = new UserSearchService(emailStrategy, nameStrategy, statusStrategy);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> expectedPage = new PageImpl<>(List.of());

        when(statusStrategy.search("ACTIVE", pageable)).thenReturn(expectedPage);

        Page<User> result = searchService.search("status", "ACTIVE", pageable);

        assertThat(result).isEqualTo(expectedPage);
        verify(statusStrategy).search("ACTIVE", pageable);
    }

    @Test
    @DisplayName("should throw exception for unknown search type")
    void shouldThrowForUnknownType() {
        UserSearchService searchService = new UserSearchService(emailStrategy, nameStrategy, statusStrategy);
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> searchService.search("phone", "12345", pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown search type: phone");
    }

    @Test
    @DisplayName("should handle type case-insensitively")
    void shouldHandleTypeCaseInsensitively() {
        UserSearchService searchService = new UserSearchService(emailStrategy, nameStrategy, statusStrategy);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> expectedPage = new PageImpl<>(List.of());

        when(emailStrategy.search("john", pageable)).thenReturn(expectedPage);

        // "EMAIL" (uppercase) should still work — we call type.toLowerCase()
        Page<User> result = searchService.search("EMAIL", "john", pageable);

        assertThat(result).isEqualTo(expectedPage);
        verify(emailStrategy).search("john", pageable);
    }
}
