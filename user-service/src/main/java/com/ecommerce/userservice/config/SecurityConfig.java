package com.ecommerce.userservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @Configuration — this class provides Spring beans (objects managed by Spring).
// @EnableWebSecurity — activates Spring Security's web protection.
// @EnableMethodSecurity — allows @PreAuthorize on individual controller methods.

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    // The SecurityFilterChain defines the RULES:
    //   - Which endpoints are public (no auth needed)?
    //   - Which endpoints require authentication?
    //   - Which endpoints require specific roles?
    //   - Where does our JWT filter sit in the chain?
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless JWT auth.
                // CSRF protection is for session-based auth (cookies).
                // Since we use tokens in the Authorization header, CSRF doesn't apply.
                .csrf(AbstractHttpConfigurer::disable)

                // Define authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no authentication required
                        .requestMatchers("/api/auth/**").permitAll()          // login, register
                        .requestMatchers("/api/status", "/api/welcome").permitAll()  // health check
                        .requestMatchers("/actuator/**").permitAll()          // monitoring
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()  // swagger

                        // Admin-only endpoints
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")     // list all users
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN") // delete users

                        // All other endpoints require authentication (any role)
                        .anyRequest().authenticated()
                )

                // Stateless session — no server-side sessions.
                // Every request must carry its own JWT token.
                // This is what makes JWT auth "stateless" — the server remembers nothing.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Use our custom authentication provider (BCrypt + UserDetailsService)
                .authenticationProvider(authenticationProvider())

                // Add our JWT filter BEFORE Spring's default username/password filter.
                // This ensures the JWT is checked before any other auth mechanism.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // BCryptPasswordEncoder — hashes passwords using the BCrypt algorithm.
    // BCrypt automatically handles salting (random data added before hashing).
    // Even if two users have the same password, their hashes will be different.
    //
    // "password123" → "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    //
    // BCrypt is intentionally SLOW — this makes brute-force attacks impractical.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationProvider — the component that actually verifies credentials.
    // DaoAuthenticationProvider:
    //   1. Loads the user via UserDetailsService.loadUserByUsername()
    //   2. Compares the provided password with the stored hash using BCrypt
    //   3. If they match, authentication succeeds
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // AuthenticationManager — the entry point for triggering authentication.
    // Our AuthController calls this to authenticate login requests.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
