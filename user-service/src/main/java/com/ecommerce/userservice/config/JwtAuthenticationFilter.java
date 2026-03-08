package com.ecommerce.userservice.config;

import com.ecommerce.userservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// This filter runs ONCE for every HTTP request (OncePerRequestFilter).
// It sits in the Security Filter Chain BEFORE the authorization check.
//
// What it does:
//   1. Check if the request has an "Authorization: Bearer <token>" header
//   2. If yes, extract and validate the JWT
//   3. If valid, load the user from the database and set them as authenticated
//   4. If no token or invalid token, do nothing (request continues as anonymous)
//
// The filter NEVER blocks requests — it only sets authentication context.
// The SecurityFilterChain config (next file) decides which endpoints require auth.

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. Get the Authorization header
        String authHeader = request.getHeader("Authorization");

        // 2. If no header or doesn't start with "Bearer ", skip this filter
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extract the token (everything after "Bearer ")
        String token = authHeader.substring(7);

        try {
            // 4. Extract the username (email) from the token
            String userEmail = jwtService.extractUsername(token);

            // 5. Only proceed if we have a username AND no one is already authenticated
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 6. Load the user from the database
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // 7. Validate the token (matches user + not expired + is access token)
                if (jwtService.isTokenValid(token, userDetails)
                        && "access".equals(jwtService.extractTokenType(token))) {

                    // 8. Create an authentication token and set it in the SecurityContext.
                    // After this, Spring Security considers this request authenticated.
                    // Any @PreAuthorize or role check will now work.
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,  // no credentials needed (already authenticated via JWT)
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid token (expired, tampered, malformed) — do nothing.
            // Request continues as unauthenticated (anonymous).
            // The authorization layer will return 401/403 if the endpoint requires auth.
        }

        // 9. Continue to the next filter in the chain
        filterChain.doFilter(request, response);
    }
}
