package com.nexoai.ontology.core.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests using a mocked StringRedisTemplate that simulates an in-memory counter.
 * This verifies tryConsume's ALLOW/BLOCK/FAIL_OPEN semantics, TTL-on-first-hit, and
 * metric recording without requiring a live Redis.
 */
class RedisRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private MeterRegistry metrics;
    private RedisRateLimiter limiter;
    private AtomicLong counter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        metrics = new SimpleMeterRegistry();
        limiter = new RedisRateLimiter(redis, metrics);
        counter = new AtomicLong(0);
        when(valueOps.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());
    }

    @Test
    void under_limit_returns_ALLOW() {
        var o = limiter.tryConsume("ratelimit:t1", 60, Duration.ofMinutes(1));
        assertThat(o).isEqualTo(RedisRateLimiter.Outcome.ALLOW);
    }

    @Test
    void first_hit_sets_expiry_on_the_key() {
        limiter.tryConsume("ratelimit:t1", 60, Duration.ofMinutes(1));
        verify(redis).expire(eq("ratelimit:t1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void subsequent_hits_do_not_reset_expiry() {
        for (int i = 0; i < 10; i++) limiter.tryConsume("ratelimit:t1", 60, Duration.ofMinutes(1));
        // TTL is only set the first time through — otherwise the window would keep sliding forward.
        verify(redis, times(1)).expire(anyString(), any(Duration.class));
    }

    @Test
    void exceeding_limit_returns_BLOCK() {
        long limit = 3;
        var window = Duration.ofMinutes(1);
        assertThat(limiter.tryConsume("k", limit, window)).isEqualTo(RedisRateLimiter.Outcome.ALLOW);
        assertThat(limiter.tryConsume("k", limit, window)).isEqualTo(RedisRateLimiter.Outcome.ALLOW);
        assertThat(limiter.tryConsume("k", limit, window)).isEqualTo(RedisRateLimiter.Outcome.ALLOW);
        assertThat(limiter.tryConsume("k", limit, window)).isEqualTo(RedisRateLimiter.Outcome.BLOCK);
        assertThat(limiter.tryConsume("k", limit, window)).isEqualTo(RedisRateLimiter.Outcome.BLOCK);
    }

    @Test
    void redis_exception_fails_open_and_records_metric() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("connection refused"));

        var o = limiter.tryConsume("k", 60, Duration.ofMinutes(1));

        assertThat(o).isEqualTo(RedisRateLimiter.Outcome.FAIL_OPEN);
        assertThat(metrics.counter("nexo.rate.limit.redis_errors").count()).isEqualTo(1);
    }

    @Test
    void null_from_increment_fails_open() {
        when(valueOps.increment(anyString())).thenReturn(null);

        var o = limiter.tryConsume("k", 60, Duration.ofMinutes(1));

        assertThat(o).isEqualTo(RedisRateLimiter.Outcome.FAIL_OPEN);
        assertThat(metrics.counter("nexo.rate.limit.redis_errors").count()).isEqualTo(1);
    }

    @Test
    void reset_deletes_the_key() {
        limiter.reset("ratelimit:t1");
        verify(redis).delete("ratelimit:t1");
    }

    @Test
    void reset_swallows_errors() {
        doThrow(new RuntimeException("boom")).when(redis).delete(anyString());
        limiter.reset("k"); // should not throw
    }
}
