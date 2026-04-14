package com.nexoai.ontology.core.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window counter on Redis. For each (key, windowSeconds) pair we INCR the
 * counter and — if we were the one that created it — EXPIRE it after the window.
 * The counter is shared across all backend instances pointing at the same Redis,
 * which is what the in-memory ConcurrentHashMap variant could not provide.
 *
 * Tradeoff vs. a token bucket: fixed-window means a tenant can burst up to
 * 2×limit across the boundary of two adjacent windows. For abuse prevention
 * that's acceptable; for SLA enforcement it's not. Sliding-window is a future
 * step (Redis sorted-set + ZADD/ZREMRANGEBYSCORE).
 *
 * Fail-open: if Redis is unavailable the limiter returns ALLOW. A correctness-
 * critical rate limit would fail-closed, but the intent here is abuse
 * mitigation, so we'd rather serve traffic than 500 every request during an
 * incident. A metric counter tracks the fail-open so on-call can see it.
 */
@Component
@Slf4j
public class RedisRateLimiter {

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    public RedisRateLimiter(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    public enum Outcome { ALLOW, BLOCK, FAIL_OPEN }

    /**
     * Try to consume one unit against the counter at {@code key}. If the count
     * after INCR exceeds {@code limit}, the request is blocked.
     *
     * @param key       Redis key, typically "ratelimit:{tenantId}"
     * @param limit     max units per window
     * @param window    length of the window (TTL set on the key)
     */
    public Outcome tryConsume(String key, long limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return failOpen("null count from INCR");
            }
            if (count == 1L) {
                // First hit in this window — stamp a TTL so the key eventually disappears.
                redis.expire(key, window);
            }
            return count <= limit ? Outcome.ALLOW : Outcome.BLOCK;
        } catch (Exception e) {
            return failOpen(e.getMessage());
        }
    }

    /** Drop the counter for a key — used on plan upgrade so the new limit applies immediately. */
    public void reset(String key) {
        try { redis.delete(key); } catch (Exception e) {
            log.debug("rate limit reset failed for {}: {}", key, e.getMessage());
        }
    }

    private Outcome failOpen(String reason) {
        log.warn("rate-limit backend unavailable, failing open: {}", reason);
        if (meterRegistry != null) {
            meterRegistry.counter("nexo.rate.limit.redis_errors").increment();
        }
        return Outcome.FAIL_OPEN;
    }
}
