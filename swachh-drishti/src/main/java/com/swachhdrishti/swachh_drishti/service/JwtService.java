package com.swachhdrishti.swachh_drishti.service;

import com.swachhdrishti.swachh_drishti.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// WHY this class:
// JWT = JSON Web Token. It's a signed string that proves who the user is.
// Format: header.payload.signature
// Flutter sends this in every request header: "Authorization: Bearer <token>"
// JwtAuthFilter intercepts every request, calls this service to validate the token
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expirationMs}")
    private long expirationMs;

    // Generate a JWT token for a user after successful login/signup
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", user.getName());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail()) // email is the unique identifier
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Extract email from token — used by JwtAuthFilter to find the user
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // Check if token is expired
    public boolean isTokenValid(String token, User user) {
        final String email = extractEmail(token);
        return email.equals(user.getEmail()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // WHY Keys.hmacShaKeyFor:
    // The secret key string from application.properties is converted to
    // a cryptographic key object. HMAC-SHA256 signs the token.
    // Anyone with this key can verify the signature — keep it secret.
    private Key getSignKey() {
        byte[] keyBytes = secretKey.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}