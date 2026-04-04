package com.nexoai.ontology.config;

import com.nexoai.ontology.core.tenant.TenantContext;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;

    public RateLimitFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        UUID tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(tenantId, this::createBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded\",\"status\":429,\"retryAfter\":60}");
        }
    }

    private Bucket createBucket(UUID tenantId) {
        int tokensPerMinute = resolveRateLimit(tenantId);
        return Bucket.builder()
                .addLimit(Bandwidth.simple(tokensPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private int resolveRateLimit(UUID tenantId) {
        try {
            String plan = jdbcTemplate.queryForObject(
                    "SELECT plan FROM tenants WHERE id = ?::uuid", String.class, tenantId.toString());
            return switch (plan) {
                case "STARTER" -> 300;
                case "PRO" -> 1000;
                case "ENTERPRISE" -> 5000;
                default -> 60; // FREE
            };
        } catch (Exception e) {
            log.debug("Could not resolve plan for tenant {}, using default rate limit", tenantId);
            return 60;
        }
    }
}
