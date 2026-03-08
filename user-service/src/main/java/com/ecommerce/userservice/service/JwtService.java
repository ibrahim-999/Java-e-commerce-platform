package com.ecommerce.userservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// JwtService handles everything JWT-related:
//   1. Generate access tokens (short-lived, 15 min)
//   2. Generate refresh tokens (long-lived, 7 days)
//   3. Validate tokens (is it expired? is it tampered with?)
//   4. Extract claims (who is this token for? what roles do they have?)
//
// A JWT has three parts separated by dots: header.payload.signature
//   Header:    {"alg": "HS256", "typ": "JWT"}
//   Payload:   {"sub": "john@example.com", "roles": ["ROLE_CUSTOMER"], "exp": 1234567890}
//   Signature: HMACSHA256(header + "." + payload, secret_key)
//
// The signature ensures the token hasn't been tampered with.
// If anyone changes the payload, the signature won't match and we reject it.

@Service
public class JwtService {

    // @Value reads from application.properties.
    // The secret key is used to sign and verify tokens.
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;    // 15 minutes in milliseconds

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;   // 7 days in milliseconds

    // ==================== TOKEN GENERATION ====================

    // Generates an access token for a user.
    // The token contains the user's email (subject) and roles (claims).
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities());
        claims.put("type", "access");
        return buildToken(claims, userDetails.getUsername(), accessTokenExpiry);
    }

    // Generates a refresh token — used to get a new access token without re-logging in.
    // Contains minimal claims (just the subject and type).
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userDetails.getUsername(), refreshTokenExpiry);
    }

    // Builds the actual JWT string.
    private String buildToken(Map<String, Object> claims, String subject, long expiry) {
        return Jwts.builder()
                .claims(claims)                                          // custom data (roles, type)
                .subject(subject)                                        // who this token is for (email)
                .issuedAt(new Date())                                    // when the token was created
                .expiration(new Date(System.currentTimeMillis() + expiry))  // when it expires
                .signWith(getSigningKey())                               // sign with our secret key
                .compact();                                              // build the final JWT string
    }

    // ==================== TOKEN VALIDATION ====================

    // Validates a token: is it for this user AND not expired?
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ==================== CLAIM EXTRACTION ====================

    // The "subject" claim is the user's email — the primary identifier.
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    // Generic method to extract any claim using a function.
    // Function<Claims, T> means: "give me Claims, I'll return a T".
    // This avoids writing separate methods for every claim.
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Parses and verifies the token.
    // If the signature doesn't match (token was tampered with), this throws an exception.
    // If the token is malformed, this throws an exception.
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ==================== SIGNING KEY ====================

    // Converts the secret string into a cryptographic key.
    // HS256 requires a minimum 256-bit (32 byte) key.
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
