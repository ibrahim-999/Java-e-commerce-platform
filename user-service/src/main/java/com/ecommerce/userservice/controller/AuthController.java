package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.service.JwtService;
import com.ecommerce.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// All endpoints under /api/auth are PUBLIC (configured in SecurityConfig).
// No token needed to register or log in.

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    // POST /api/auth/register
    // Creates a new user and returns JWT tokens immediately (auto-login after registration).
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateUserRequest request) {
        // Create the user (password gets hashed in UserService)
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(request.getPassword())
                .phoneNumber(request.getPhoneNumber())
                .build();

        userService.createUser(user);

        // Load as UserDetails to generate tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        // Generate tokens and return them
        // Records use constructor instead of builder — all fields in order
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(
                jwtService.generateAccessToken(userDetails),
                jwtService.generateRefreshToken(userDetails),
                request.getEmail(),
                "Bearer"));
    }

    // POST /api/auth/login
    // Authenticates credentials and returns JWT tokens.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // AuthenticationManager does the heavy lifting:
        //   1. Calls UserDetailsService.loadUserByUsername(email)
        //   2. Compares the provided password with the stored BCrypt hash
        //   3. If they match, returns successfully
        //   4. If they don't match, throws BadCredentialsException
        // Records use field() not getField() — so request.email() not request.getEmail()
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // If we get here, authentication succeeded — generate tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());

        return ResponseEntity.ok(new AuthResponse(
                jwtService.generateAccessToken(userDetails),
                jwtService.generateRefreshToken(userDetails),
                request.email(),
                "Bearer"));
    }

    // POST /api/auth/refresh
    // Takes a refresh token and returns a new access token.
    // The client uses this when the access token expires (every 15 min).
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String refreshToken = authHeader.substring(7);
        String email = jwtService.extractUsername(refreshToken);

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Verify this is actually a refresh token and it's valid
        if (!jwtService.isTokenValid(refreshToken, userDetails)
                || !"refresh".equals(jwtService.extractTokenType(refreshToken))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Generate a new access token (refresh token stays the same)
        return ResponseEntity.ok(new AuthResponse(
                jwtService.generateAccessToken(userDetails),
                refreshToken,
                email,
                "Bearer"));
    }
}
