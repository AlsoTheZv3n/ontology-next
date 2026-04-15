package com.nexoai.ontology.core.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-JVM fixed-window counter used when Redis is unavailable.
 *
 * Tradeoff against the distributed Redis limiter: a tenant can now spend
 * up to {@code limit × number_of_instances} requests per window during an
 * outage — not as tight as the real counter, but far tighter than fail-open
 * (unlimited). That degradation is explicit and observable via the
 * ALLOW_DEGRADED outcome, so on-call can tell from metrics that the fallback
 * is in use.
 *
 * Windows older than 10× the latest seen window are evicted opportunistically
 * so the map doesn't grow unbounded over a long outage.
 */
@Component
public class LocalFallbackLimiter {

    private static final int EVICT_AFTER_WINDOWS = 10;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, long limit, Duration window) {
        long nowMs = System.currentTimeMillis();
        long windowMs = Math.max(window.toMillis(), 1L);

        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || nowMs - existing.startMs > windowMs) {
                return new Window(nowMs, new AtomicLong(0));
            }
            return existing;
        });

        long count = w.count.incrementAndGet();

        // Opportunistic cleanup: purge keys whose last-seen window is long gone.
        if (windows.size() > 1000) {
            windows.entrySet().removeIf(
                    e -> nowMs - e.getValue().startMs > windowMs * EVICT_AFTER_WINDOWS);
        }

        return count <= limit;
    }

    void clear() { windows.clear(); }  // exposed for tests

    private record Window(long startMs, AtomicLong count) {}
}
