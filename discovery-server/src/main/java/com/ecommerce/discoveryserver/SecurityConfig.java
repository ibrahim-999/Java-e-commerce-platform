package com.ecommerce.discoveryserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Security config for the Eureka dashboard.
//
// Why secure Eureka?
//   The Eureka dashboard shows every service's IP address, port, and health status.
//   In production, this is sensitive information — you don't want it publicly accessible.
//   We protect it with HTTP Basic auth (username/password from application.properties).
//
// We also DISABLE CSRF for Eureka:
//   Eureka clients (our microservices) register via POST/PUT requests.
//   CSRF would block these requests because they don't carry a CSRF token.
//   This is safe because Eureka is an internal service, not user-facing.

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Allow Eureka clients to register without auth
                        // (in production, you'd use mutual TLS or API keys instead)
                        .requestMatchers("/eureka/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
