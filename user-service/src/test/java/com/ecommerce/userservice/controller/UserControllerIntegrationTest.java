package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.factory.UserFactory;
import com.ecommerce.userservice.model.Role;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @SpringBootTest — starts the FULL Spring application context (all beans, all config).
// Unlike unit tests, this runs your actual application code against a real database.
//
// @ActiveProfiles("test") — uses application-test.properties which points to
// a separate test database. Your dev data is never touched.
//
// @AutoConfigureMockMvc — creates a MockMvc instance that simulates HTTP requests
// without starting an actual HTTP server. Faster than making real HTTP calls.

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {

    // MockMvc — sends fake HTTP requests to your controllers.
    // No Postman needed, no browser, no real HTTP server.
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper — converts Java objects to JSON strings (for request bodies).
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    // Clean and re-seed before each test so tests don't affect each other.
    // This is the equivalent of Laravel's RefreshDatabase trait.
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Seed roles if they don't exist (ddl-auto=create-drop recreates tables)
        if (roleRepository.count() == 0) {
            roleRepository.save(Role.builder().name("ROLE_CUSTOMER").build());
            roleRepository.save(Role.builder().name("ROLE_SELLER").build());
            roleRepository.save(Role.builder().name("ROLE_ADMIN").build());
        }
    }

    @Nested
    @DisplayName("POST /api/users")
    class CreateUser {

        @Test
        @DisplayName("should create user and return 201")
        void shouldCreateUser() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.firstName").value(request.getFirstName()))
                    .andExpect(jsonPath("$.lastName").value(request.getLastName()))
                    .andExpect(jsonPath("$.email").value(request.getEmail()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.roles", hasItem("ROLE_CUSTOMER")))
                    .andExpect(jsonPath("$.password").doesNotExist());  // password must NOT be in response
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

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400WhenFieldsMissing() throws Exception {
            CreateUserRequest request = new CreateUserRequest();

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 when email already exists")
        void shouldReturn409WhenDuplicateEmail() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();

            // Create the first user
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Try to create another user with the same email
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Email already exists")));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserById {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUser() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String response = mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn().getResponse().getContentAsString();

            Long userId = objectMapper.readTree(response).get("id").asLong();

            mockMvc.perform(get("/api/users/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId))
                    .andExpect(jsonPath("$.email").value(request.getEmail()));
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(get("/api/users/{id}", 99999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("User not found")));
        }
    }

    @Nested
    @DisplayName("GET /api/users")
    class GetAllUsers {

        @Test
        @DisplayName("should return paginated users")
        void shouldReturnPaginatedUsers() throws Exception {
            // Create 3 users
            for (int i = 0; i < 3; i++) {
                CreateUserRequest request = UserFactory.createUserRequest();
                mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));
            }

            mockMvc.perform(get("/api/users")
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("should delete user and return 204")
        void shouldDeleteUser() throws Exception {
            CreateUserRequest request = UserFactory.createUserRequest();
            String response = mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn().getResponse().getContentAsString();

            Long userId = objectMapper.readTree(response).get("id").asLong();

            mockMvc.perform(delete("/api/users/{id}", userId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/users/{id}", userId))
                    .andExpect(status().isNotFound());
        }
    }
}
