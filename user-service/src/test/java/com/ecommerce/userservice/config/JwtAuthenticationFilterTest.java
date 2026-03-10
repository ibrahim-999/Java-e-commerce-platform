package com.ecommerce.userservice.config;

import com.ecommerce.userservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        // Clear security context before each test
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("No Authorization header")
    class NoAuthorizationHeader {

        @Test
        @DisplayName("should continue filter chain when no Authorization header is present")
        void shouldContinueFilterChainWhenNoAuthHeader() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Non-Bearer Authorization header")
    class NonBearerHeader {

        @Test
        @DisplayName("should continue filter chain when Authorization header does not start with Bearer")
        void shouldContinueWhenNotBearer() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Valid access token")
    class ValidAccessToken {

        @Test
        @DisplayName("should set SecurityContext when a valid access token is provided")
        void shouldSetSecurityContextForValidAccessToken() throws ServletException, IOException {
            String token = "valid.jwt.token";
            String email = "test@example.com";
            UserDetails userDetails = new User(
                    email, "password",
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
            );

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.extractUsername(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
            when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
            when(jwtService.extractTokenType(token)).thenReturn("access");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("Invalid token")
    class InvalidToken {

        @Test
        @DisplayName("should continue without auth when token is invalid")
        void shouldContinueWithoutAuthWhenTokenInvalid() throws ServletException, IOException {
            String token = "invalid.jwt.token";

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.extractUsername(token)).thenThrow(new RuntimeException("Invalid token"));

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should continue without auth when token validation fails")
        void shouldContinueWithoutAuthWhenTokenValidationFails() throws ServletException, IOException {
            String token = "expired.jwt.token";
            String email = "test@example.com";
            UserDetails userDetails = new User(
                    email, "password",
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
            );

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.extractUsername(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
            when(jwtService.isTokenValid(token, userDetails)).thenReturn(false);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Refresh token rejection")
    class RefreshTokenRejection {

        @Test
        @DisplayName("should reject refresh tokens and not set SecurityContext")
        void shouldRejectRefreshTokens() throws ServletException, IOException {
            String token = "refresh.jwt.token";
            String email = "test@example.com";
            UserDetails userDetails = new User(
                    email, "password",
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
            );

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.extractUsername(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
            when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
            when(jwtService.extractTokenType(token)).thenReturn("refresh");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
