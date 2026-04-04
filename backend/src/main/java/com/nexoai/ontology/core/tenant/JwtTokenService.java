package com.nexoai.ontology.core.tenant;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class JwtTokenService {

    @Value("${nexo.jwt.secret:nexo-ontology-super-secret-key-that-is-at-least-256-bits-long!}")
    private String jwtSecret;

    @Value("${nexo.jwt.expiration-ms:86400000}")
    private long expirationMs;

    public String generateToken(String email, UUID tenantId, String tenantApiName, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(email)
                .claim("tenantId", tenantId.toString())
                .claim("tenantApiName", tenantApiName)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public JwtClaims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new JwtClaims(
                claims.getSubject(),
                claims.get("tenantId", String.class),
                claims.get("tenantApiName", String.class),
                claims.get("role", String.class)
        );
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public record JwtClaims(String email, String tenantId, String tenantApiName, String role) {}
}
