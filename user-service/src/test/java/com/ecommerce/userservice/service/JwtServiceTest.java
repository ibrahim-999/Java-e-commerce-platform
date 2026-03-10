package com.ecommerce.userservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    // Must be at least 32 bytes (256 bits) for HMAC-SHA256
    private static final String TEST_SECRET_KEY = "this-is-a-test-secret-key-that-is-at-least-32-characters-long-for-testing";
    private static final long ACCESS_TOKEN_EXPIRY = 900000L;    // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRY = 604800000L; // 7 days

    private UserDetails testUserDetails;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();

        // Use reflection to set @Value-injected private fields
        setField(jwtService, "secretKey", TEST_SECRET_KEY);
        setField(jwtService, "accessTokenExpiry", ACCESS_TOKEN_EXPIRY);
        setField(jwtService, "refreshTokenExpiry", REFRESH_TOKEN_EXPIRY);

        testUserDetails = new User(
                "test@example.com",
                "password123",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("should return a valid JWT string")
        void shouldReturnValidJwt() {
            String token = jwtService.generateAccessToken(testUserDetails);

            assertThat(token).isNotNull();
            assertThat(token).isNotBlank();
            // JWT has 3 parts separated by dots
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("should contain 'access' as token type")
        void shouldContainAccessType() {
            String token = jwtService.generateAccessToken(testUserDetails);

            String tokenType = jwtService.extractTokenType(token);
            assertThat(tokenType).isEqualTo("access");
        }

        @Test
        @DisplayName("should contain the correct username as subject")
        void shouldContainCorrectUsername() {
            String token = jwtService.generateAccessToken(testUserDetails);

            String username = jwtService.extractUsername(token);
            assertThat(username).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("should return a valid JWT with 'refresh' type")
        void shouldReturnValidJwtWithRefreshType() {
            String token = jwtService.generateRefreshToken(testUserDetails);

            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3);

            String tokenType = jwtService.extractTokenType(token);
            assertThat(tokenType).isEqualTo("refresh");
        }

        @Test
        @DisplayName("should contain the correct username as subject")
        void shouldContainCorrectUsername() {
            String token = jwtService.generateRefreshToken(testUserDetails);

            String username = jwtService.extractUsername(token);
            assertThat(username).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("extractUsername")
    class ExtractUsername {

        @Test
        @DisplayName("should extract the correct username from access token")
        void shouldExtractCorrectUsernameFromAccessToken() {
            String token = jwtService.generateAccessToken(testUserDetails);

            String username = jwtService.extractUsername(token);
            assertThat(username).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should extract the correct username from refresh token")
        void shouldExtractCorrectUsernameFromRefreshToken() {
            String token = jwtService.generateRefreshToken(testUserDetails);

            String username = jwtService.extractUsername(token);
            assertThat(username).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("should return true for a valid token with matching user")
        void shouldReturnTrueForValidToken() {
            String token = jwtService.generateAccessToken(testUserDetails);

            boolean isValid = jwtService.isTokenValid(token, testUserDetails);
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should throw exception for an expired token")
        void shouldThrowForExpiredToken() throws Exception {
            // Set access token expiry to 0 so token is immediately expired
            setField(jwtService, "accessTokenExpiry", 0L);

            String token = jwtService.generateAccessToken(testUserDetails);

            // Small delay to ensure expiration
            Thread.sleep(10);

            // JJWT throws ExpiredJwtException when parsing an expired token,
            // before isTokenValid even gets to check the expiration date.
            // The filter catches this exception and treats it as unauthenticated.
            assertThatThrownBy(() -> jwtService.isTokenValid(token, testUserDetails))
                    .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);

            // Restore original expiry
            setField(jwtService, "accessTokenExpiry", ACCESS_TOKEN_EXPIRY);
        }

        @Test
        @DisplayName("should return false for token belonging to a different user")
        void shouldReturnFalseForWrongUser() {
            String token = jwtService.generateAccessToken(testUserDetails);

            UserDetails differentUser = new User(
                    "other@example.com",
                    "password123",
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
            );

            boolean isValid = jwtService.isTokenValid(token, differentUser);
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("extractTokenType")
    class ExtractTokenType {

        @Test
        @DisplayName("should return 'access' for access tokens")
        void shouldReturnAccessForAccessTokens() {
            String token = jwtService.generateAccessToken(testUserDetails);

            String tokenType = jwtService.extractTokenType(token);
            assertThat(tokenType).isEqualTo("access");
        }

        @Test
        @DisplayName("should return 'refresh' for refresh tokens")
        void shouldReturnRefreshForRefreshTokens() {
            String token = jwtService.generateRefreshToken(testUserDetails);

            String tokenType = jwtService.extractTokenType(token);
            assertThat(tokenType).isEqualTo("refresh");
        }
    }

    @Nested
    @DisplayName("Token tamper detection")
    class TamperDetection {

        @Test
        @DisplayName("should fail validation when token signature is tampered with")
        void shouldFailWhenSignatureTampered() {
            String token = jwtService.generateAccessToken(testUserDetails);

            // Tamper with the token by modifying the last character of the signature
            String tamperedToken;
            if (token.endsWith("a")) {
                tamperedToken = token.substring(0, token.length() - 1) + "b";
            } else {
                tamperedToken = token.substring(0, token.length() - 1) + "a";
            }

            // Extracting username from a tampered token should throw an exception
            assertThatThrownBy(() -> jwtService.extractUsername(tamperedToken))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should fail validation when token was signed with a different key")
        void shouldFailWhenSignedWithDifferentKey() {
            // Create a token signed with a different secret key
            String differentSecret = "a-completely-different-secret-key-that-is-also-long-enough";
            SecretKey differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));

            String foreignToken = Jwts.builder()
                    .claims(Map.of("type", "access"))
                    .subject("test@example.com")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 900000))
                    .signWith(differentKey)
                    .compact();

            // Our JwtService should reject this token since it was signed with a different key
            assertThatThrownBy(() -> jwtService.extractUsername(foreignToken))
                    .isInstanceOf(Exception.class);
        }
    }
}
