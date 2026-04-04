package com.nexoai.ontology.core.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                JwtTokenService.JwtClaims claims = jwtTokenService.parse(token);
                TenantContext.setTenantId(UUID.fromString(claims.tenantId()));
                TenantContext.setCurrentUser(claims.email());
                TenantContext.setCurrentRole(claims.role());
            } catch (Exception e) {
                log.debug("JWT parsing failed: {}", e.getMessage());
            }
        }

        // Default tenant if no JWT
        if (TenantContext.getTenantId().equals(TenantContext.DEFAULT_TENANT_ID)) {
            log.trace("Using default tenant for request: {}", request.getRequestURI());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
