package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.ApiResponse;
import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.service.UserSearchService;
import com.ecommerce.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSearchService userSearchService;

    // ==================== CREATE ====================
    // POST /api/users
    //
    // @PostMapping — handles HTTP POST requests (creating a resource).
    //
    // @RequestBody — tells Spring "parse the JSON request body into this object".
    //   The client sends: {"firstName": "John", "lastName": "Doe", "email": "...", ...}
    //   Spring + Jackson automatically converts that JSON into a CreateUserRequest object.
    //
    // @Valid — triggers the validation annotations on CreateUserRequest.
    //   If @NotBlank or @Email fails, Spring returns 400 Bad Request automatically
    //   with the error messages — your code never runs.
    //
    // HttpStatus.CREATED (201) — the correct status code for "resource created".
    //   Don't use 200 OK for creation — 201 is the standard.

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        // Convert DTO → Entity (we control what goes into the entity)
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(request.getPassword())
                .phoneNumber(request.getPhoneNumber())
                .build();

        User savedUser = userService.createUser(user);

        // Convert Entity → DTO (password is excluded), wrap in ApiResponse
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", UserResponse.fromEntity(savedUser)));
    }

    // ==================== READ (single) ====================
    // GET /api/users/1
    //
    // @PathVariable — extracts the {id} from the URL.
    //   GET /api/users/5 → id = 5

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.fromEntity(user)));
    }

    // ==================== READ (all — paginated) ====================
    // GET /api/users?page=0&size=10&sort=createdAt,desc
    //
    // @RequestParam — extracts query parameters from the URL.
    //   page = which page to return (0-based, so page=0 is the first page)
    //   size = how many items per page
    //   sort = which field to sort by, and direction (asc/desc)
    //
    // All parameters have defaults, so GET /api/users with no params
    // returns page 0 with 10 items, sorted by createdAt descending (newest first).

    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // Parse the sort parameter: "createdAt,desc" → Sort.by(DESC, "createdAt")
        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(
                sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                sortParams[0]
        );

        Pageable pageable = PageRequest.of(page, size, sortOrder);

        // Page.map() converts each User entity to a UserResponse DTO
        // while preserving all the pagination metadata (totalElements, totalPages, etc.)
        Page<UserResponse> users = userService.getAllUsers(pageable)
                .map(UserResponse::fromEntity);

        return ResponseEntity.ok(users);
    }

    // ==================== SEARCH (Strategy pattern) ====================
    // GET /api/users/search?type=email&query=john&page=0&size=10
    // GET /api/users/search?type=name&query=doe
    // GET /api/users/search?type=status&query=ACTIVE
    //
    // The "type" parameter picks which search strategy to use.
    // The controller doesn't know HOW to search — it delegates to UserSearchService,
    // which picks the right strategy. This is the Strategy pattern in action.

    @GetMapping("/search")
    public ResponseEntity<Page<UserResponse>> searchUsers(
            @RequestParam String type,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<UserResponse> results = userSearchService.search(type, query, pageable)
                .map(UserResponse::fromEntity);

        return ResponseEntity.ok(results);
    }

    // ==================== UPDATE ====================
    // PUT /api/users/1

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        User updatedData = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .build();

        User updatedUser = userService.updateUser(id, updatedData);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", UserResponse.fromEntity(updatedUser)));
    }

    // ==================== DELETE ====================
    // DELETE /api/users/1
    //
    // HttpStatus.NO_CONTENT (204) — "success, but nothing to return".
    //   The standard response for a successful delete.

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
