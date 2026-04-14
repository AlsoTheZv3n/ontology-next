package com.nexoai.ontology.config;

import com.nexoai.ontology.core.ratelimit.RedisRateLimiter;
import com.nexoai.ontology.core.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
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

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisRateLimiter limiter;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RedisRateLimiter limiter, JdbcTemplate jdbcTemplate,
                            MeterRegistry meterRegistry) {
        this.limiter = limiter;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
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

        String plan = resolvePlan(tenantId);
        int limit = planLimit(plan);
        String key = "ratelimit:" + tenantId;

        RedisRateLimiter.Outcome outcome = limiter.tryConsume(key, limit, WINDOW);

        meterRegistry.counter("nexo.rate.limit.hits",
                "plan", plan, "outcome", outcome.name().toLowerCase()).increment();

        if (outcome == RedisRateLimiter.Outcome.BLOCK) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded\",\"status\":429,\"retryAfter\":60}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolvePlan(UUID tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT plan FROM tenants WHERE id = ?::uuid",
                    String.class, tenantId.toString());
        } catch (Exception e) {
            return "FREE";
        }
    }

    private static int planLimit(String plan) {
        if (plan == null) return 60;
        return switch (plan) {
            case "STARTER"    -> 300;
            case "PRO"        -> 1000;
            case "ENTERPRISE" -> 5000;
            default           -> 60;
        };
    }
}
