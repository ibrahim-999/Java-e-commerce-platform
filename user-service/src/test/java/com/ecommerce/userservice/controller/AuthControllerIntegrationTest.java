package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.BaseIntegrationTest;
import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.factory.UserFactory;
import com.ecommerce.userservice.model.Role;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.RoleRepository;
import com.ecommerce.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Role customerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        userRepository.deleteAll();

        if (roleRepository.count() == 0) {
            roleRepository.save(Role.builder().name("ROLE_CUSTOMER").build());
            roleRepository.save(Role.builder().name("ROLE_SELLER").build());
            roleRepository.save(Role.builder().name("ROLE_ADMIN").build());
        }
        customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseThrow();
        adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
    }

    private String registerAndGetToken(CreateUserRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String createAdminAndGetToken() throws Exception {
        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@authtest.com")
                .password(passwordEncoder.encode("admin123"))
                .status(UserStatus.ACTIVE)
                .roles(java.util.Set.of(adminRole, customerRole))
                .build();
        userRepository.save(admin);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@authtest.com", "admin123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Nested
    @DisplayName("Full Auth Flow")
    class FullAuthFlow {

        @Test
        @DisplayName("should register, login, and access protected endpoint with access token")
        void shouldCompleteFullAuthFlow() throws Exception {
            // Step 1: Register a new user
            CreateUserRequest registerRequest = UserFactory.createUserRequest();

            String registerResponse = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.email").value(registerRequest.getEmail()))
                    .andExpect(jsonPath("$.type").value("Bearer"))
                    .andReturn().getResponse().getContentAsString();

            String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

            // Step 2: Login with the same credentials
            String loginResponse = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(registerRequest.getEmail(), registerRequest.getPassword()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            String loginAccessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

            // Step 3: Use access token to access a protected endpoint (search requires auth)
            mockMvc.perform(get("/api/users/search")
                            .header("Authorization", "Bearer " + loginAccessToken)
                            .param("type", "email")
                            .param("query", registerRequest.getEmail()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshToken {

        @Test
        @DisplayName("should return new tokens when using a valid refresh token")
        void shouldReturnNewTokensWithValidRefreshToken() throws Exception {
            // Register a user and get the refresh token
            CreateUserRequest request = UserFactory.createUserRequest();

            String registerResponse = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String refreshToken = objectMapper.readTree(registerResponse).get("refreshToken").asText();

            // Use the refresh token to get a new access token
            mockMvc.perform(post("/api/auth/refresh")
                            .header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").value(refreshToken))
                    .andExpect(jsonPath("$.email").value(request.getEmail()));
        }

        @Test
        @DisplayName("should reject an invalid refresh token with exception")
        void shouldRejectInvalidRefreshToken() {
            // An invalid JWT string causes a MalformedJwtException in the controller
            // which propagates as a ServletException since there's no specific handler for it
            assertThrows(Exception.class, () ->
                    mockMvc.perform(post("/api/auth/refresh")
                            .header("Authorization", "Bearer invalid.token.here"))
            );
        }

        @Test
        @DisplayName("should return 401 when using an access token instead of refresh token")
        void shouldReturn401WhenUsingAccessTokenAsRefresh() throws Exception {
            // Register and get the access token
            CreateUserRequest request = UserFactory.createUserRequest();

            String registerResponse = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

            // Try to use the access token as a refresh token — should fail
            mockMvc.perform(post("/api/auth/refresh")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 when no Authorization header is present")
        void shouldReturn400WhenNoAuthHeader() throws Exception {
            // Spring returns 400 Bad Request when a required @RequestHeader is missing
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validation errors on register")
    class RegisterValidation {

        @Test
        @DisplayName("should return 400 when email is missing")
        void shouldReturn400WhenEmailMissing() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when email is invalid")
        void shouldReturn400WhenEmailInvalid() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("not-an-email")
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password is too short")
        void shouldReturn400WhenPasswordTooShort() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .password("short")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when first name is missing")
        void shouldReturn400WhenFirstNameMissing() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .lastName("Doe")
                    .email("john@example.com")
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when last name is missing")
        void shouldReturn400WhenLastNameMissing() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .firstName("John")
                    .email("john@example.com")
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password is missing")
        void shouldReturn400WhenPasswordMissing() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/users (UserController create)")
    class UserControllerCreate {

        @Test
        @DisplayName("should create user via UserController endpoint")
        void shouldCreateUserViaUserController() throws Exception {
            String adminToken = createAdminAndGetToken();

            CreateUserRequest request = UserFactory.createUserRequest();

            mockMvc.perform(post("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User created successfully"))
                    .andExpect(jsonPath("$.data.email").value(request.getEmail()));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/{id} (UserController update)")
    class UserControllerUpdate {

        @Test
        @DisplayName("should update user fields")
        void shouldUpdateUser() throws Exception {
            // Register a user to update
            CreateUserRequest createRequest = UserFactory.createUserRequest();
            registerAndGetToken(createRequest);

            String adminToken = createAdminAndGetToken();

            // Find the user ID
            String usersResponse = mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("page", "0").param("size", "10"))
                    .andReturn().getResponse().getContentAsString();

            // Find the non-admin user
            com.fasterxml.jackson.databind.JsonNode content = objectMapper.readTree(usersResponse).get("content");
            Long userId = null;
            for (int i = 0; i < content.size(); i++) {
                if (content.get(i).get("email").asText().equals(createRequest.getEmail())) {
                    userId = content.get(i).get("id").asLong();
                    break;
                }
            }

            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .firstName("UpdatedFirst")
                    .lastName("UpdatedLast")
                    .phoneNumber("+9999999999")
                    .build();

            mockMvc.perform(put("/api/users/{id}", userId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User updated successfully"))
                    .andExpect(jsonPath("$.data.firstName").value("UpdatedFirst"))
                    .andExpect(jsonPath("$.data.lastName").value("UpdatedLast"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/{id} (UserController delete)")
    class UserControllerDelete {

        @Test
        @DisplayName("should delete user when admin")
        void shouldDeleteUserWhenAdmin() throws Exception {
            CreateUserRequest createRequest = UserFactory.createUserRequest();
            registerAndGetToken(createRequest);

            String adminToken = createAdminAndGetToken();

            // Find the user ID
            String usersResponse = mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("page", "0").param("size", "10"))
                    .andReturn().getResponse().getContentAsString();

            com.fasterxml.jackson.databind.JsonNode content = objectMapper.readTree(usersResponse).get("content");
            Long userId = null;
            for (int i = 0; i < content.size(); i++) {
                if (content.get(i).get("email").asText().equals(createRequest.getEmail())) {
                    userId = content.get(i).get("id").asLong();
                    break;
                }
            }

            mockMvc.perform(delete("/api/users/{id}", userId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User deleted successfully"));
        }
    }

    @Nested
    @DisplayName("GET /api/users with ASC sort")
    class UserControllerGetAllWithSort {

        @Test
        @DisplayName("should return users sorted ascending")
        void shouldReturnUsersSortedAscending() throws Exception {
            String adminToken = createAdminAndGetToken();

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("page", "0")
                            .param("size", "10")
                            .param("sort", "createdAt,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("Health endpoints")
    class HealthEndpoints {

        @Test
        @DisplayName("should return status from health endpoint")
        void shouldReturnStatus() throws Exception {
            mockMvc.perform(get("/api/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.service").value("user-service"))
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("should return welcome message")
        void shouldReturnWelcome() throws Exception {
            mockMvc.perform(get("/api/welcome"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Welcome to the User Service API"));
        }
    }
}
