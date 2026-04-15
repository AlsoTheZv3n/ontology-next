package com.nexoai.ontology.core.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window counter on Redis, with a configurable behaviour when Redis is
 * unavailable.
 *
 * {@link FailMode} controls what happens on a Redis exception:
 *   FAIL_OPEN       — request passes through (no protection). Legacy behaviour.
 *   FAIL_CLOSED     — request is blocked (protects backend, hurts availability).
 *   LOCAL_FALLBACK  — per-JVM ConcurrentHashMap takes over (protection degraded
 *                     but still present). Default — see red-04 audit finding.
 *
 * Sliding-window is a future step — see prod-11 commit message for why
 * fixed-window is adequate for the abuse-mitigation use case.
 */
@Component
@Slf4j
public class RedisRateLimiter {

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final LocalFallbackLimiter localFallback;

    @Value("${nexo.ratelimit.fail-mode:LOCAL_FALLBACK}")
    private FailMode failMode;

    public RedisRateLimiter(StringRedisTemplate redis, MeterRegistry meterRegistry,
                             LocalFallbackLimiter localFallback) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.localFallback = localFallback;
    }

    public enum FailMode { FAIL_OPEN, FAIL_CLOSED, LOCAL_FALLBACK }

    public enum Outcome {
        ALLOW,
        BLOCK,
        /** Redis down, FAIL_OPEN configured — request passed through without check. */
        FAIL_OPEN,
        /** Redis down, LOCAL_FALLBACK accepted the request (per-JVM counter). */
        ALLOW_DEGRADED
    }

    public Outcome tryConsume(String key, long limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return onRedisFailure(key, limit, window, "null count from INCR");
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            return count <= limit ? Outcome.ALLOW : Outcome.BLOCK;
        } catch (Exception e) {
            return onRedisFailure(key, limit, window, e.getMessage());
        }
    }

    public void reset(String key) {
        try { redis.delete(key); } catch (Exception e) {
            log.debug("rate limit reset failed for {}: {}", key, e.getMessage());
        }
    }

    private Outcome onRedisFailure(String key, long limit, Duration window, String reason) {
        if (meterRegistry != null) {
            meterRegistry.counter("nexo.rate.limit.redis_errors").increment();
        }
        log.warn("rate-limit backend unavailable (mode={}): {}", failMode, reason);
        return switch (failMode) {
            case FAIL_OPEN -> Outcome.FAIL_OPEN;
            case FAIL_CLOSED -> Outcome.BLOCK;
            case LOCAL_FALLBACK -> localFallback.tryConsume(key, limit, window)
                    ? Outcome.ALLOW_DEGRADED
                    : Outcome.BLOCK;
        };
    }

    // Exposed for tests — production flips this via @Value.
    void setFailMode(FailMode mode) { this.failMode = mode; }
}
