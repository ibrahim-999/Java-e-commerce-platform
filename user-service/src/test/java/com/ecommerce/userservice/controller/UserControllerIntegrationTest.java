package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.BaseIntegrationTest;
import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.factory.UserFactory;
import com.ecommerce.userservice.model.Role;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.RoleRepository;
import com.ecommerce.userservice.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    private Role customerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        // Clear Redis cache before clearing DB — prevents stale cached data
        // from leaking between tests
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

    // Helper: register a user and return the access token
    private String registerAndGetToken(CreateUserRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    // Helper: create an admin user directly in the DB and return their access token
    private String createAdminAndGetToken() throws Exception {
        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@test.com")
                .password(passwordEncoder.encode("admin123"))
                .status(UserStatus.ACTIVE)
                .roles(Set.of(adminRole, customerRole))
                .build();
        userRepository.save(admin);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@test.com", "admin123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("should register user and return tokens")
        void shouldRegisterAndReturnTokens() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.email").value(request.getEmail()))
                    .andExpect(jsonPath("$.type").value("Bearer"));
        }

        @Test
        @DisplayName("should return 409 when email already exists")
        void shouldReturn409WhenDuplicateEmail() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("should login and return tokens")
        void shouldLoginAndReturnTokens() throws Exception {
            CreateUserRequest registerReq = UserFactory.createUserRequest();
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerReq)));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(registerReq.getEmail(), registerReq.getPassword()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        }

        @Test
        @DisplayName("should return 401 with wrong password")
        void shouldReturn401WithWrongPassword() throws Exception {
            CreateUserRequest registerReq = UserFactory.createUserRequest();
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerReq)));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(registerReq.getEmail(), "wrongpassword"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserById {

        @Test
        @DisplayName("should return user when authenticated")
        void shouldReturnUserWhenAuthenticated() throws Exception {
            // Register a user and get their token
            CreateUserRequest request = UserFactory.createUserRequest();
            String regResponse = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(regResponse).get("accessToken").asText();

            // Find the actual user ID from the database
            String adminToken = createAdminAndGetToken();
            String usersResponse = mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("page", "0").param("size", "10"))
                    .andReturn().getResponse().getContentAsString();
            Long userId = objectMapper.readTree(usersResponse).get("content").get(0).get("id").asLong();

            mockMvc.perform(get("/api/users/{id}", userId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should allow unauthenticated access to GET /api/users/{id} (inter-service)")
        void shouldAllowUnauthenticatedGetUserById() throws Exception {
            // GET /api/users/{id} is permitted without auth for inter-service communication.
            // Returns 404 because user with ID 1 doesn't exist in the test DB.
            mockMvc.perform(get("/api/users/1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/users (admin only)")
    class GetAllUsers {

        @Test
        @DisplayName("should return users when admin")
        void shouldReturnUsersWhenAdmin() throws Exception {
            String adminToken = createAdminAndGetToken();

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should return 403 when not admin")
        void shouldReturn403WhenNotAdmin() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String customerToken = registerAndGetToken(request);

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/{id} (admin only)")
    class DeleteUser {

        @Test
        @DisplayName("should return 403 when non-admin tries to delete")
        void shouldReturn403WhenNotAdmin() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String customerToken = registerAndGetToken(request);

            mockMvc.perform(delete("/api/users/1")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/users/search (Strategy pattern)")
    class SearchUsers {

        @Test
        @DisplayName("should search by email")
        void shouldSearchByEmail() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String token = registerAndGetToken(request);

            mockMvc.perform(get("/api/users/search")
                            .header("Authorization", "Bearer " + token)
                            .param("type", "email")
                            .param("query", request.getEmail()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("should search by name")
        void shouldSearchByName() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String token = registerAndGetToken(request);

            mockMvc.perform(get("/api/users/search")
                            .header("Authorization", "Bearer " + token)
                            .param("type", "name")
                            .param("query", request.getFirstName()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("should search by status")
        void shouldSearchByStatus() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String token = registerAndGetToken(request);

            mockMvc.perform(get("/api/users/search")
                            .header("Authorization", "Bearer " + token)
                            .param("type", "status")
                            .param("query", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("should return 400 for unknown search type")
        void shouldReturn400ForUnknownType() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String token = registerAndGetToken(request);

            mockMvc.perform(get("/api/users/search")
                            .header("Authorization", "Bearer " + token)
                            .param("type", "phone")
                            .param("query", "12345"))
                    .andExpect(status().isBadRequest());
        }
    }
}
