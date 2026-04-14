package com.nexoai.ontology.core.tenant;

import com.nexoai.ontology.core.apikey.ApiKeyService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticates requests with either:
 * - JWT (Bearer <jwt-token>): parses claims, extracts tenant + role
 * - API Key (Bearer nxo_...): looks up in api_keys table, uses MEMBER role
 *
 * If authentication fails, SecurityContext is not set. Spring Security will
 * then return 401 when AUTH_ENFORCED=true (see SecurityConfig).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_PREFIX = "nxo_";

    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (token.startsWith(API_KEY_PREFIX)) {
                authenticateWithApiKey(token, request);
            } else {
                authenticateWithJwt(token, request);
            }
        } catch (Exception e) {
            log.debug("Auth failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticateWithJwt(String token, HttpServletRequest request) {
        try {
            JwtTokenService.JwtClaims claims = jwtTokenService.parse(token);
            TenantContext.setTenantId(UUID.fromString(claims.tenantId()));
            TenantContext.setCurrentUser(claims.email());
            TenantContext.setCurrentRole(claims.role());

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    claims.email(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            // Don't set auth — Spring Security will handle 401
        }
    }

    private void authenticateWithApiKey(String rawKey, HttpServletRequest request) {
        Map<String, Object> keyRow = apiKeyService.validateKey(rawKey);
        if (keyRow == null) {
            log.debug("Invalid or expired API key: {}", rawKey.substring(0, Math.min(12, rawKey.length())));
            return;
        }

        UUID tenantId = UUID.fromString(keyRow.get("tenant_id").toString());
        UUID keyId = UUID.fromString(keyRow.get("id").toString());
        String name = (String) keyRow.get("name");

        TenantContext.setTenantId(tenantId);
        TenantContext.setCurrentUser("apikey:" + name);
        TenantContext.setCurrentRole("MEMBER"); // API keys default to MEMBER role

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "apikey:" + name,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        log.debug("API key authenticated: {} (tenant={})", keyId, tenantId);
    }
}
